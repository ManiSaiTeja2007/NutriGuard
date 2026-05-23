#!/usr/bin/env python3
import os
import sys
import json
import csv
import time
import argparse
import hashlib
from pathlib import Path
from datetime import datetime

# Setup paths for import parity
PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from benchmark.scripts.benchmark.config_loader import ConfigLoader
from benchmark.scripts.benchmark.pipeline_stages import IngredientVocabulary
from benchmark.scripts.benchmark.evaluators.ocr_evaluator import OcrEvaluator
from benchmark.scripts.benchmark.evaluators.normalization_evaluator import NormalizationEvaluator
from benchmark.scripts.benchmark.evaluators.extraction_evaluator import ExtractionEvaluator
from benchmark.scripts.benchmark.evaluators.alias_evaluator import AliasEvaluator
from benchmark.scripts.benchmark.evaluators.canonicalization_evaluator import CanonicalizationEvaluator
from benchmark.scripts.benchmark.failures.failure_serializer import FailureSerializer

# SUBSET CATEGORY MAPPINGS
SUBSET_MAPPINGS = {
    "clean": ["raw_clean"],
    "blurry": ["raw_blurry", "synth_blur"],
    "low_light": ["raw_lowlight", "synth_lowlight"],
    "curved_packaging": ["raw_curved"],
    "multilingual": ["raw_multilingual"],
    "catastrophic_ocr": ["raw_rotated", "synth_rotation", "raw_difficult_fonts", "raw_handwritten"]
}

def parse_annotation_file(filepath: Path) -> dict:
    """
    Parses standard NutriGuard ground truth structured annotation text file.
    """
    try:
        content = filepath.read_text(encoding="utf-8")
    except Exception as e:
        print(f"[-] Failed to read annotation file {filepath}: {e}", file=sys.stderr)
        return {}

    sections = {}
    current_section = None
    current_lines = []

    for line in content.splitlines():
        line_strip = line.strip()
        if line_strip.startswith("[") and line_strip.endswith("]"):
            if current_section:
                sections[current_section] = current_lines
            current_section = line_strip[1:-1]
            current_lines = []
        elif line_strip:
            current_lines.append(line_strip)

    if current_section:
        sections[current_section] = current_lines

    # Parse RAW INGREDIENTS
    raw_ingredients_lines = sections.get("RAW INGREDIENTS", [])
    raw_ingredients = ""
    if raw_ingredients_lines:
        line = raw_ingredients_lines[0]
        if ":" in line:
            raw_ingredients = line.split(":", 1)[1].strip()
        else:
            raw_ingredients = line

    # Parse EXPECTED CANONICAL
    expected_canonical = sections.get("EXPECTED CANONICAL", [])

    # Parse NUTRITION VALUES
    nutrition = {}
    for line in sections.get("NUTRITION VALUES", []):
        if ":" in line:
            k, v = line.split(":", 1)
            nutrition[k.strip().lower()] = v.strip()

    # Parse FAILURE_TAGS
    failure_tags = [t.strip() for t in sections.get("FAILURE_TAGS", []) if t.strip()]

    return {
        "raw_ingredients": raw_ingredients,
        "expected_canonical": expected_canonical,
        "nutrition": nutrition,
        "failure_tags": failure_tags
    }

def run_benchmark():
    parser = argparse.ArgumentParser(description="NutriGuard scientific OCR benchmark runner.")
    parser.add_argument("--manifest", type=str, help="Path to manifest JSON file.")
    parser.add_argument("--subset", type=str, choices=["all", "clean", "blurry", "low_light", "curved_packaging", "multilingual", "catastrophic_ocr"], help="Filter execution to a curated subset.")
    parser.add_argument("--stage", type=str, choices=["ocr", "normalization", "extraction", "alias", "canonicalization"], help="Execute and evaluate up to a specific pipeline stage.")
    parser.add_argument("--compare", type=str, help="Compare results against a previous report JSON.")
    parser.add_argument("--config", type=str, help="Path to configuration JSON.")
    parser.add_argument("--no-corruption", action="store_true", help="Explicitly disable synthetic OCR corruption simulation.")
    
    args = parser.parse_args()

    # Load configuration
    config_path = Path(args.config) if args.config else None
    config = ConfigLoader.load_config(config_path)

    # CLI Overrides
    manifest_path = Path(args.manifest) if args.manifest else PROJECT_ROOT / "benchmark" / "manifests" / "master_manifest.json"
    subset = args.subset if args.subset else config.get("subset_selection", "all")
    stage_limit = args.stage if args.stage else config.get("stage_filtering")
    
    simulate_corruption = not args.no_corruption if args.no_corruption else config.get("simulate_ocr_corruption", True)

    pipeline_version = config.get("pipeline_version", "1.0.0")
    schema_version = config.get("benchmark_schema_version", "1.0.0")
    dataset_version = config.get("dataset_version", "1.0.0")

    output_dir = PROJECT_ROOT / config["report"]["output_dir"]
    replays_dir = PROJECT_ROOT / config["report"]["replays_dir"]

    output_dir.mkdir(parents=True, exist_ok=True)
    replays_dir.mkdir(parents=True, exist_ok=True)
    (replays_dir / "canonicalization_outputs").mkdir(parents=True, exist_ok=True)
    (replays_dir / "failed_cases").mkdir(parents=True, exist_ok=True)

    print("=" * 60)
    print(f"[*] Starting NutriGuard Scientific OCR Benchmark Execution")
    print(f"    Manifest:          {manifest_path.relative_to(PROJECT_ROOT)}")
    print(f"    Subset:            {subset}")
    print(f"    Stage Limit:       {stage_limit or 'None (Full Ingestion)'}")
    print(f"    Simulate OCR Noise: {simulate_corruption}")
    print(f"    Pipeline Version:  {pipeline_version}")
    print("=" * 60)

    # Load Manifest
    if not manifest_path.exists():
        print(f"[-] ERROR: Manifest file does not exist: {manifest_path}", file=sys.stderr)
        sys.exit(1)

    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest_data = json.load(f)
    except Exception as e:
        print(f"[-] ERROR: Failed to parse manifest JSON: {e}", file=sys.stderr)
        sys.exit(1)

    entries = manifest_data.get("entries", [])
    print(f"[+] Loaded {len(entries)} entries from manifest.")

    # Filter by subset
    filtered_entries = []
    for entry in entries:
        category = entry.get("category")
        if subset == "all":
            filtered_entries.append(entry)
        else:
            allowed_categories = SUBSET_MAPPINGS.get(subset, [])
            if category in allowed_categories:
                filtered_entries.append(entry)

    # Sort deterministically by image_path
    filtered_entries.sort(key=lambda x: x["image_path"])
    print(f"[+] Subset '{subset}' filtered to {len(filtered_entries)} entries.")

    vocabulary = IngredientVocabulary()
    record_results = []
    all_failures = []
    
    # Track metrics aggregations
    metrics_summary = {
        "cer": [], "wer": [],
        "precision": [], "recall": [], "f1": [],
        "nested_precision": [], "nested_recall": [], "nested_f1": [],
        "alias_precision": [], "alias_recall": [], "alias_f1": [], "alias_confidence": [],
        "canonical_precision": [], "canonical_recall": [], "canonical_f1": [], "canonical_accuracy": []
    }
    
    stage_latencies = {
        "ocr": [], "normalization": [], "extraction": [], "alias": [], "canonicalization": []
    }

    start_bench_time = time.perf_counter()

    for idx, entry in enumerate(filtered_entries):
        image_path_rel = entry["image_path"]
        annotation_path_rel = entry["annotation_path"]
        category = entry["category"]

        # Resolve paths
        image_path = PROJECT_ROOT / "benchmark" / image_path_rel
        annotation_path = PROJECT_ROOT / "benchmark" / annotation_path_rel

        # Generate deterministic Replay ID via hash
        replay_id = hashlib.sha256(image_path_rel.encode("utf-8")).hexdigest()[:16]

        # Parse ground truth annotations
        gt = parse_annotation_file(annotation_path)
        if not gt:
            continue

        gt_text = gt["raw_ingredients"]
        expected_canonical = gt["expected_canonical"]
        
        # Tokenize expected ingredients matching extractor logic
        expected_ingredients = [t.strip().lower().rstrip(".").rstrip(",") for t in gt_text.split(",") if t.strip().lower() and not t.strip().lower().startswith("ingredients")]

        # Replay path
        replay_path_rel = f"replays/canonicalization_outputs/{replay_id}_replay.json"
        replay_path = PROJECT_ROOT / "benchmark" / replay_path_rel

        record_failures = []
        record_latency = {}

        # STAGE 1: OCR
        s_time = time.perf_counter()
        ocr_res = OcrEvaluator.evaluate(
            gt_text=gt_text,
            image_name=image_path.name,
            category=category,
            subset=subset,
            replay_path=str(replay_path_rel),
            pipeline_version=pipeline_version,
            replays_dir=replays_dir,
            simulate_corruption=simulate_corruption
        )
        ocr_text = ocr_res["output_text"]
        record_latency["ocr"] = (time.perf_counter() - s_time) * 1000.0
        stage_latencies["ocr"].append(record_latency["ocr"])

        metrics_summary["cer"].append(ocr_res["metrics"]["cer"])
        metrics_summary["wer"].append(ocr_res["metrics"]["wer"])
        record_failures.extend(ocr_res["failures"])

        if stage_limit == "ocr":
            # Early exit for stage testing
            save_replay_and_record(replay_id, image_path_rel, ocr_text, "", [], [], ocr_res["metrics"], record_failures, record_latency, pipeline_version, schema_version, dataset_version, replay_path, replays_dir)
            continue

        # STAGE 2: Normalization
        s_time = time.perf_counter()
        norm_res = NormalizationEvaluator.evaluate(
            ocr_text=ocr_text,
            gt_text=gt_text,
            subset=subset,
            replay_path=str(replay_path_rel),
            pipeline_version=pipeline_version
        )
        normalized_text = norm_res["output_text"]
        record_latency["normalization"] = (time.perf_counter() - s_time) * 1000.0
        stage_latencies["normalization"].append(record_latency["normalization"])
        record_failures.extend(norm_res["failures"])

        if stage_limit == "normalization":
            save_replay_and_record(replay_id, image_path_rel, ocr_text, normalized_text, [], [], norm_res["metrics"], record_failures, record_latency, pipeline_version, schema_version, dataset_version, replay_path, replays_dir)
            continue

        # STAGE 3: Extraction
        s_time = time.perf_counter()
        ext_res = ExtractionEvaluator.evaluate(
            normalized_text=normalized_text,
            expected_ingredients=expected_ingredients,
            vocabulary=vocabulary,
            subset=subset,
            replay_path=str(replay_path_rel),
            pipeline_version=pipeline_version
        )
        extracted_tokens = ext_res["extracted_tokens"]
        record_latency["extraction"] = (time.perf_counter() - s_time) * 1000.0
        stage_latencies["extraction"].append(record_latency["extraction"])

        metrics_summary["precision"].append(ext_res["metrics"]["precision"])
        metrics_summary["recall"].append(ext_res["metrics"]["recall"])
        metrics_summary["f1"].append(ext_res["metrics"]["f1"])
        metrics_summary["nested_precision"].append(ext_res["metrics"]["nested_precision"])
        metrics_summary["nested_recall"].append(ext_res["metrics"]["nested_recall"])
        metrics_summary["nested_f1"].append(ext_res["metrics"]["nested_f1"])
        
        record_failures.extend(ext_res["failures"])

        if stage_limit == "extraction":
            save_replay_and_record(replay_id, image_path_rel, ocr_text, normalized_text, extracted_tokens, [], ext_res["metrics"], record_failures, record_latency, pipeline_version, schema_version, dataset_version, replay_path, replays_dir)
            continue

        # STAGE 4: Alias Correction
        s_time = time.perf_counter()
        alias_res = AliasEvaluator.evaluate(
            extracted_tokens=extracted_tokens,
            expected_ingredients=expected_ingredients,
            vocabulary=vocabulary,
            subset=subset,
            replay_path=str(replay_path_rel),
            pipeline_version=pipeline_version
        )
        corrected_tokens = alias_res["corrected_tokens"]
        record_latency["alias"] = (time.perf_counter() - s_time) * 1000.0
        stage_latencies["alias"].append(record_latency["alias"])

        metrics_summary["alias_precision"].append(alias_res["metrics"]["precision"])
        metrics_summary["alias_recall"].append(alias_res["metrics"]["recall"])
        metrics_summary["alias_f1"].append(alias_res["metrics"]["f1"])
        metrics_summary["alias_confidence"].append(alias_res["metrics"]["average_confidence"])
        record_failures.extend(alias_res["failures"])

        if stage_limit == "alias":
            save_replay_and_record(replay_id, image_path_rel, ocr_text, normalized_text, extracted_tokens, corrected_tokens, alias_res["metrics"], record_failures, record_latency, pipeline_version, schema_version, dataset_version, replay_path, replays_dir)
            continue

        # STAGE 5: Canonicalization
        s_time = time.perf_counter()
        canon_res = CanonicalizationEvaluator.evaluate(
            resolved_records=alias_res["resolved_records"],
            expected_canonical=expected_canonical,
            subset=subset,
            replay_path=str(replay_path_rel),
            pipeline_version=pipeline_version
        )
        canonicalized_ingredients = canon_res["canonicalized_ingredients"]
        record_latency["canonicalization"] = (time.perf_counter() - s_time) * 1000.0
        stage_latencies["canonicalization"].append(record_latency["canonicalization"])

        metrics_summary["canonical_precision"].append(canon_res["metrics"]["precision"])
        metrics_summary["canonical_recall"].append(canon_res["metrics"]["recall"])
        metrics_summary["canonical_f1"].append(canon_res["metrics"]["f1"])
        metrics_summary["canonical_accuracy"].append(canon_res["metrics"]["accuracy"])
        record_failures.extend(canon_res["failures"])

        # Save deterministic Replay JSON
        replay_obj = save_replay_and_record(
            replay_id=replay_id,
            image_path_rel=image_path_rel,
            ocr_text=ocr_text,
            normalized_text=normalized_text,
            extracted_tokens=extracted_tokens,
            canonical_ingredients=canonicalized_ingredients,
            metrics={
                "cer": ocr_res["metrics"]["cer"],
                "wer": ocr_res["metrics"]["wer"],
                "extraction_f1": ext_res["metrics"]["f1"],
                "alias_f1": alias_res["metrics"]["f1"],
                "canonical_f1": canon_res["metrics"]["f1"],
                "canonical_accuracy": canon_res["metrics"]["accuracy"]
            },
            record_failures=record_failures,
            record_latency=record_latency,
            pipeline_version=pipeline_version,
            schema_version=schema_version,
            dataset_version=dataset_version,
            replay_path=replay_path,
            replays_dir=replays_dir
        )

        all_failures.extend(record_failures)
        record_results.append({
            "image_path": image_path_rel,
            "category": category,
            "replay_id": replay_id,
            "metrics": {
                "cer": ocr_res["metrics"]["cer"],
                "wer": ocr_res["metrics"]["wer"],
                "extraction_precision": ext_res["metrics"]["precision"],
                "extraction_recall": ext_res["metrics"]["recall"],
                "extraction_f1": ext_res["metrics"]["f1"],
                "canonical_precision": canon_res["metrics"]["precision"],
                "canonical_recall": canon_res["metrics"]["recall"],
                "canonical_f1": canon_res["metrics"]["f1"],
                "canonical_accuracy": canon_res["metrics"]["accuracy"]
            },
            "failures_count": len(record_failures),
            "latency_total_ms": sum(record_latency.values())
        })

    end_bench_time = time.perf_counter()
    total_elapsed_ms = (end_bench_time - start_bench_time) * 1000.0

    # Aggregate Overall Statistics
    def safe_avg(lst):
        return sum(lst) / len(lst) if lst else 0.0

    aggregated_metrics = {
        "dataset_version": dataset_version,
        "pipeline_version": pipeline_version,
        "benchmark_schema_version": schema_version,
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "total_images_processed": len(filtered_entries),
        "total_failures_detected": len(all_failures),
        "total_runtime_ms": total_elapsed_ms,
        "average_cer": safe_avg(metrics_summary["cer"]),
        "average_wer": safe_avg(metrics_summary["wer"]),
        "average_extraction_precision": safe_avg(metrics_summary["precision"]),
        "average_extraction_recall": safe_avg(metrics_summary["recall"]),
        "average_extraction_f1": safe_avg(metrics_summary["f1"]),
        "average_nested_extraction_f1": safe_avg(metrics_summary["nested_f1"]),
        "average_alias_f1": safe_avg(metrics_summary["alias_f1"]),
        "average_alias_confidence": safe_avg(metrics_summary["alias_confidence"]),
        "average_canonical_precision": safe_avg(metrics_summary["canonical_precision"]),
        "average_canonical_recall": safe_avg(metrics_summary["canonical_recall"]),
        "average_canonical_f1": safe_avg(metrics_summary["canonical_f1"]),
        "average_canonical_accuracy": safe_avg(metrics_summary["canonical_accuracy"]),
        "average_latency_ms": {
            k: safe_avg(v) for k, v in stage_latencies.items()
        },
        "failure_counts": {
            "OCR_FAILURE": len([f for f in all_failures if f.failure_type == "OCR_FAILURE"]),
            "NORMALIZATION_FAILURE": len([f for f in all_failures if f.failure_type == "NORMALIZATION_FAILURE"]),
            "TOKENIZATION_FAILURE": len([f for f in all_failures if f.failure_type == "TOKENIZATION_FAILURE"]),
            "EXTRACTION_FAILURE": len([f for f in all_failures if f.failure_type == "EXTRACTION_FAILURE"]),
            "ALIAS_FAILURE": len([f for f in all_failures if f.failure_type == "ALIAS_FAILURE"]),
            "CANONICALIZATION_FAILURE": len([f for f in all_failures if f.failure_type == "CANONICALIZATION_FAILURE"]),
            "LOW_CONFIDENCE_FAILURE": len([f for f in all_failures if f.failure_type == "LOW_CONFIDENCE_FAILURE"]),
            "TRUNCATION_FAILURE": len([f for f in all_failures if f.failure_type == "TRUNCATION_FAILURE"]),
            "NOISE_FAILURE": len([f for f in all_failures if f.failure_type == "NOISE_FAILURE"])
        }
    }

    # Generate Reports
    json_report_path = output_dir / f"benchmark_report_{subset}.json"
    csv_report_path = output_dir / f"benchmark_summary_{subset}.csv"
    md_report_path = output_dir / f"benchmark_summary_{subset}.md"

    # 1. JSON Report
    report_data = {
        "summary": aggregated_metrics,
        "records": record_results
    }
    with open(json_report_path, "w", encoding="utf-8") as jf:
        json.dump(report_data, jf, indent=2)

    # 2. CSV Summary (Deterministic column ordering)
    CSV_HEADER = [
        "image_path", "category", "replay_id", "cer", "wer",
        "extraction_precision", "extraction_recall", "extraction_f1",
        "canonical_precision", "canonical_recall", "canonical_f1",
        "canonical_accuracy", "failures_count", "latency_total_ms"
    ]
    with open(csv_report_path, "w", newline="", encoding="utf-8") as cf:
        writer = csv.writer(cf)
        writer.writerow(CSV_HEADER)
        for r in record_results:
            writer.writerow([
                r["image_path"],
                r["category"],
                r["replay_id"],
                f"{r['metrics']['cer']:.4f}",
                f"{r['metrics']['wer']:.4f}",
                f"{r['metrics']['extraction_precision']:.4f}",
                f"{r['metrics']['extraction_recall']:.4f}",
                f"{r['metrics']['extraction_f1']:.4f}",
                f"{r['metrics']['canonical_precision']:.4f}",
                f"{r['metrics']['canonical_recall']:.4f}",
                f"{r['metrics']['canonical_f1']:.4f}",
                f"{r['metrics']['canonical_accuracy']:.4f}",
                r["failures_count"],
                f"{r['latency_total_ms']:.2f}"
            ])

    # 3. Markdown Summary
    md_content = generate_markdown_summary(aggregated_metrics, subset)
    md_report_path.write_text(md_content, encoding="utf-8")

    print("[+] Evaluation completed successfully.")
    print(f"    JSON Report: {json_report_path.relative_to(PROJECT_ROOT)}")
    print(f"    CSV Report:  {csv_report_path.relative_to(PROJECT_ROOT)}")
    print(f"    MD Summary:  {md_report_path.relative_to(PROJECT_ROOT)}")

    # Stage 6 comparison if --compare flag provided
    if args.compare:
        compare_path = Path(args.compare)
        if compare_path.exists():
            diff_md = generate_comparison_diff(json_report_path, compare_path)
            diff_report_path = output_dir / f"ocr_diff_report_{subset}.md"
            diff_report_path.write_text(diff_md, encoding="utf-8")
            print(f"    Diff Report: {diff_report_path.relative_to(PROJECT_ROOT)}")
        else:
            print(f"[-] WARNING: Comparison file does not exist: {compare_path}", file=sys.stderr)

def save_replay_and_record(replay_id, image_path_rel, ocr_text, normalized_text, extracted_tokens,
                           canonical_ingredients, metrics, record_failures, record_latency,
                           pipeline_version, schema_version, dataset_version, replay_path, replays_dir):
    replay_obj = {
        "replay_id": replay_id,
        "source_image": image_path_rel,
        "ocr_output": ocr_text,
        "normalized_text": normalized_text,
        "extracted_ingredients": extracted_tokens,
        "canonical_ingredients": canonical_ingredients,
        "metrics": metrics,
        "failures": FailureSerializer.serialize(record_failures),
        "latency_metrics_ms": record_latency,
        "pipeline_version": pipeline_version,
        "benchmark_schema_version": schema_version,
        "dataset_version": dataset_version,
        "timestamp": datetime.utcnow().isoformat() + "Z"
    }

    with open(replay_path, "w", encoding="utf-8") as rf:
        json.dump(replay_obj, rf, indent=2)

    # Save to failed_cases if there are failures
    if record_failures:
        failed_path = replays_dir / "failed_cases" / f"{replay_id}_replay.json"
        with open(failed_path, "w", encoding="utf-8") as rf:
            json.dump(replay_obj, rf, indent=2)
            
    return replay_obj

def generate_markdown_summary(stats: dict, subset: str) -> str:
    avg_latency = stats["average_latency_ms"]
    total_lat = sum(avg_latency.values())
    
    return f"""# NutriGuard Scientific OCR Evaluation Summary — Subset: `{subset.upper()}`

Generated at: `{stats["timestamp"]}`
Pipeline Version: `{stats["pipeline_version"]}`
Benchmark Schema: `{stats["benchmark_schema_version"]}`
Dataset Version: `{stats["dataset_version"]}`

## 1. Executive Performance Metrics

| Metric | Value | Threshold Status |
|---|---|---|
| **Total Images Processed** | {stats["total_images_processed"]} | - |
| **Character Error Rate (CER)** | {stats["average_cer"]:.4f} | { "PASS" if stats["average_cer"] <= 0.05 else "WARNING" } |
| **Word Error Rate (WER)** | {stats["average_wer"]:.4f} | { "PASS" if stats["average_wer"] <= 0.10 else "WARNING" } |
| **Extraction Precision** | {stats["average_extraction_precision"]:.4f} | - |
| **Extraction Recall** | {stats["average_extraction_recall"]:.4f} | - |
| **Extraction F1-Score** | {stats["average_extraction_f1"]:.4f} | { "PASS" if stats["average_extraction_f1"] >= 0.90 else "FAIL" } |
| **Nested Extraction F1-Score** | {stats["average_nested_extraction_f1"]:.4f} | - |
| **Alias Resolution F1-Score** | {stats["average_alias_f1"]:.4f} | - |
| **Alias Average Confidence** | {stats["average_alias_confidence"]:.4f} | - |
| **Canonical F1-Score** | {stats["average_canonical_f1"]:.4f} | - |
| **Canonical Accuracy** | {stats["average_canonical_accuracy"]:.4%} | { "PASS" if stats["average_canonical_accuracy"] >= 0.85 else "FAIL" } |

## 2. Latency Metrics (ms)

* **Average Total Ingestion Latency**: `{total_lat:.2f} ms`

| Stage | Latency |
|---|---|
| OCR | {avg_latency.get("ocr", 0.0):.2f} ms |
| Normalization | {avg_latency.get("normalization", 0.0):.2f} ms |
| Extraction | {avg_latency.get("extraction", 0.0):.2f} ms |
| Alias Resolution | {avg_latency.get("alias", 0.0):.2f} ms |
| Canonicalization | {avg_latency.get("canonicalization", 0.0):.2f} ms |

## 3. Failure Taxonomy Breakdown

Total failures flagged: `{stats["total_failures_detected"]}`

* **OCR_FAILURE**: `{stats["failure_counts"].get("OCR_FAILURE", 0)}`
* **NORMALIZATION_FAILURE**: `{stats["failure_counts"].get("NORMALIZATION_FAILURE", 0)}`
* **TOKENIZATION_FAILURE**: `{stats["failure_counts"].get("TOKENIZATION_FAILURE", 0)}`
* **EXTRACTION_FAILURE**: `{stats["failure_counts"].get("EXTRACTION_FAILURE", 0)}`
* **ALIAS_FAILURE**: `{stats["failure_counts"].get("ALIAS_FAILURE", 0)}`
* **CANONICALIZATION_FAILURE**: `{stats["failure_counts"].get("CANONICALIZATION_FAILURE", 0)}`
* **LOW_CONFIDENCE_FAILURE**: `{stats["failure_counts"].get("LOW_CONFIDENCE_FAILURE", 0)}`
* **TRUNCATION_FAILURE**: `{stats["failure_counts"].get("TRUNCATION_FAILURE", 0)}`
* **NOISE_FAILURE**: `{stats["failure_counts"].get("NOISE_FAILURE", 0)}`
"""

def generate_comparison_diff(curr_path: Path, prev_path: Path) -> str:
    with open(curr_path, "r", encoding="utf-8") as f:
        curr = json.load(f)["summary"]
    with open(prev_path, "r", encoding="utf-8") as f:
        prev = json.load(f)["summary"]

    def diff_val(c, p, is_percent=False):
        d = c - p
        symbol = "+" if d >= 0 else ""
        if is_percent:
            return f"{c:.2%} ({symbol}{d:.2%})"
        return f"{c:.4f} ({symbol}{d:.4f})"

    return f"""# NutriGuard Scientific OCR Regression & Diff Report

Comparison generated at: `{datetime.utcnow().isoformat()}Z`

| Metric | Previous Value | Current Value | Delta | Status |
|---|---|---|---|---|
| **Images Processed** | {prev["total_images_processed"]} | {curr["total_images_processed"]} | {curr["total_images_processed"] - prev["total_images_processed"]} | - |
| **Average CER** | {prev["average_cer"]:.4f} | {curr["average_cer"]:.4f} | {curr["average_cer"] - prev["average_cer"]:.4f} | {"IMPROVEMENT" if curr["average_cer"] < prev["average_cer"] else "REGRESSION" if curr["average_cer"] > prev["average_cer"] else "STABLE"} |
| **Average WER** | {prev["average_wer"]:.4f} | {curr["average_wer"]:.4f} | {curr["average_wer"] - prev["average_wer"]:.4f} | {"IMPROVEMENT" if curr["average_wer"] < prev["average_wer"] else "REGRESSION" if curr["average_wer"] > prev["average_wer"] else "STABLE"} |
| **Extraction F1** | {prev["average_extraction_f1"]:.4f} | {curr["average_extraction_f1"]:.4f} | {curr["average_extraction_f1"] - prev["average_extraction_f1"]:.4f} | {"IMPROVEMENT" if curr["average_extraction_f1"] > prev["average_extraction_f1"] else "REGRESSION" if curr["average_extraction_f1"] < prev["average_extraction_f1"] else "STABLE"} |
| **Canonical F1** | {prev["average_canonical_f1"]:.4f} | {curr["average_canonical_f1"]:.4f} | {curr["average_canonical_f1"] - prev["average_canonical_f1"]:.4f} | {"IMPROVEMENT" if curr["average_canonical_f1"] > prev["average_canonical_f1"] else "REGRESSION" if curr["average_canonical_f1"] < prev["average_canonical_f1"] else "STABLE"} |
| **Canonical Accuracy** | {prev["average_canonical_accuracy"]:.2%} | {curr["average_canonical_accuracy"]:.2%} | {curr["average_canonical_accuracy"] - prev["average_canonical_accuracy"]:.2%} | {"IMPROVEMENT" if curr["average_canonical_accuracy"] > prev["average_canonical_accuracy"] else "REGRESSION" if curr["average_canonical_accuracy"] < prev["average_canonical_accuracy"] else "STABLE"} |

## Failure Delta Breakdown

| Failure Category | Previous Count | Current Count | Shift |
|---|---|---|---|
| OCR_FAILURE | {prev["failure_counts"].get("OCR_FAILURE", 0)} | {curr["failure_counts"].get("OCR_FAILURE", 0)} | {curr["failure_counts"].get("OCR_FAILURE", 0) - prev["failure_counts"].get("OCR_FAILURE", 0)} |
| NORMALIZATION_FAILURE | {prev["failure_counts"].get("NORMALIZATION_FAILURE", 0)} | {curr["failure_counts"].get("NORMALIZATION_FAILURE", 0)} | {curr["failure_counts"].get("NORMALIZATION_FAILURE", 0) - prev["failure_counts"].get("NORMALIZATION_FAILURE", 0)} |
| TOKENIZATION_FAILURE | {prev["failure_counts"].get("TOKENIZATION_FAILURE", 0)} | {curr["failure_counts"].get("TOKENIZATION_FAILURE", 0)} | {curr["failure_counts"].get("TOKENIZATION_FAILURE", 0) - prev["failure_counts"].get("TOKENIZATION_FAILURE", 0)} |
| EXTRACTION_FAILURE | {prev["failure_counts"].get("EXTRACTION_FAILURE", 0)} | {curr["failure_counts"].get("EXTRACTION_FAILURE", 0)} | {curr["failure_counts"].get("EXTRACTION_FAILURE", 0) - prev["failure_counts"].get("EXTRACTION_FAILURE", 0)} |
| ALIAS_FAILURE | {prev["failure_counts"].get("ALIAS_FAILURE", 0)} | {curr["failure_counts"].get("ALIAS_FAILURE", 0)} | {curr["failure_counts"].get("ALIAS_FAILURE", 0) - prev["failure_counts"].get("ALIAS_FAILURE", 0)} |
| CANONICALIZATION_FAILURE | {prev["failure_counts"].get("CANONICALIZATION_FAILURE", 0)} | {curr["failure_counts"].get("CANONICALIZATION_FAILURE", 0)} | {curr["failure_counts"].get("CANONICALIZATION_FAILURE", 0) - prev["failure_counts"].get("CANONICALIZATION_FAILURE", 0)} |
| LOW_CONFIDENCE_FAILURE | {prev["failure_counts"].get("LOW_CONFIDENCE_FAILURE", 0)} | {curr["failure_counts"].get("LOW_CONFIDENCE_FAILURE", 0)} | {curr["failure_counts"].get("LOW_CONFIDENCE_FAILURE", 0) - prev["failure_counts"].get("LOW_CONFIDENCE_FAILURE", 0)} |
| TRUNCATION_FAILURE | {prev["failure_counts"].get("TRUNCATION_FAILURE", 0)} | {curr["failure_counts"].get("TRUNCATION_FAILURE", 0)} | {curr["failure_counts"].get("TRUNCATION_FAILURE", 0) - prev["failure_counts"].get("TRUNCATION_FAILURE", 0)} |
| NOISE_FAILURE | {prev["failure_counts"].get("NOISE_FAILURE", 0)} | {curr["failure_counts"].get("NOISE_FAILURE", 0)} | {curr["failure_counts"].get("NOISE_FAILURE", 0) - prev["failure_counts"].get("NOISE_FAILURE", 0)} |
"""

if __name__ == "__main__":
    run_benchmark()
