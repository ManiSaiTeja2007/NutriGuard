#!/usr/bin/env python3
import os
import sys
import json
import shutil
import zipfile
import argparse
import hashlib
import re
from pathlib import Path

# Paths configuration using pathlib
SCRIPT_DIR = Path(__file__).resolve().parent
BENCHMARK_DIR = SCRIPT_DIR.parent
DATASETS_DIR = BENCHMARK_DIR / "datasets"
PROJECT_ROOT = BENCHMARK_DIR.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

def get_image_dimensions(image_path: Path):
    # Try parsing PNG and JPEG binary headers first to avoid PIL dependency.
    try:
        with open(image_path, "rb") as f:
            head = f.read(24)
            # PNG check
            if head.startswith(b"\x89PNG\r\n\x1a\n"):
                import struct
                # Width at 16, Height at 20 (4 bytes each, big endian)
                w, h = struct.unpack(">II", head[16:24])
                return int(w), int(h)
            # JPEG check
            elif head.startswith(b"\xff\xd8"):
                import struct
                f.seek(2)
                while True:
                    marker_header = f.read(4)
                    if len(marker_header) < 4:
                        break
                    # A marker is 0xFF followed by byte (not 0x00 or 0xFF)
                    if marker_header[0] != 0xff:
                        # Scan forward for 0xFF
                        f.seek(-3, 1)
                        single = f.read(1)
                        if not single:
                            break
                        if single[0] != 0xff:
                            continue
                        marker_header = b"\xff" + f.read(3)
                        if len(marker_header) < 4:
                            break

                    marker = marker_header[1]
                    length = struct.unpack(">H", marker_header[2:4])[0]
                    # SOF markers: 0xC0-0xC3, 0xC5-0xC7, 0xC9-0xCB, 0xCD-0xCF
                    if marker in (0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf):
                        sof_data = f.read(5)
                        if len(sof_data) == 5:
                            h, w = struct.unpack(">HH", sof_data[1:5])
                            return int(w), int(h)
                        break
                    else:
                        f.seek(length - 2, 1)
    except Exception:
        pass

    # Fallback to PIL
    try:
        from PIL import Image
        with Image.open(image_path) as img:
            return img.width, img.height
    except Exception:
        # Fallback to tiny 1x1 image dimensions
        return 1, 1

def compute_sha256(file_path: Path) -> str:
    sha = hashlib.sha256()
    with open(file_path, "rb") as f:
        while True:
            chunk = f.read(65536)
            if not chunk:
                break
            sha.update(chunk)
    return sha.hexdigest()

def get_next_label_id(clean_dir: Path, failure_dir: Path) -> int:
    """
    Scans clean and failure directories to find the highest existing label ID
    and returns the next available integer ID.
    """
    max_id = 0
    pattern = re.compile(r"^label_(\d{6})\.(jpg|png)$")
    
    for directory in [clean_dir, failure_dir]:
        if directory.exists():
            for file in directory.iterdir():
                match = pattern.match(file.name)
                if match:
                    val = int(match.group(1))
                    if val > max_id:
                        max_id = val
    return max_id + 1

def parse_session_export(export_path: Path, temp_dir: Path) -> Path:
    """
    Extracts the source zip file if zip, otherwise copies/uses the folder directly.
    Returns the path to the directory containing manifest.json.
    """
    if export_path.is_file() and export_path.suffix.lower() == ".zip":
        print(f"[*] Extracting archive: {export_path.name}")
        with zipfile.ZipFile(export_path, 'r') as zip_ref:
            zip_ref.extractall(temp_dir)
        return temp_dir
    elif export_path.is_dir():
        return export_path
    else:
        raise ValueError(f"Invalid export source path: {export_path}")

def process_export(source_path: Path, clean_dir: Path, failure_dir: Path) -> bool:
    """
    Processes a single unzipped session export folder, validates it, and merges
    it into the target packaging corpus database.
    """
    manifest_file = source_path / "manifest.json"
    if not manifest_file.exists():
        print(f"[-] Skip: manifest.json not found in {source_path}")
        return False
        
    with open(manifest_file, "r", encoding="utf-8") as f:
        manifest = json.load(f)
        
    # Locate raw image
    raw_img_rel = "raw/raw_image.png"
    raw_img_path = source_path / raw_img_rel
    if not raw_img_path.exists():
        # Check if there is raw_image.jpg or any other png under raw/
        raw_dir = source_path / "raw"
        if raw_dir.exists():
            images = list(raw_dir.glob("*.*"))
            if images:
                raw_img_path = images[0]
                
    if not raw_img_path.exists():
        print(f"[-] Skip: raw image file not found in {source_path}")
        return False
        
    # Check for duplicates using SHA-256 hash
    img_hash = compute_sha256(raw_img_path)
    
    # Check clean_dir
    for f in clean_dir.glob("label_*.jpg"):
        if compute_sha256(f) == img_hash:
            print(f"[-] Skip: Duplicate image detected in clean corpus: {f.name}")
            return False
            
    # Check failure_dir
    for f in failure_dir.glob("label_*.jpg"):
        if compute_sha256(f) == img_hash:
            print(f"[-] Skip: Duplicate image detected in failure/diff corpus: {f.name}")
            return False

    # Read semantic interpretations and replay traces
    semantic_file = source_path / "semantic" / "semantic_interpretation.json"
    replay_file = source_path / "replay" / "replay_trace.json"
    
    semantic_data = []
    if semantic_file.exists():
        with open(semantic_file, "r", encoding="utf-8") as f:
            semantic_data = json.load(f)
            
    # Detect failures to classify clean vs failure paths
    failures = []
    has_failures = False
    
    for item in semantic_data:
        item_fails = item.get("failures", [])
        if item_fails:
            failures.extend(item_fails)
            has_failures = True
        # Check low confidence
        conf_str = item.get("confidence", "HIGH")
        if conf_str in ["LOW", "LOW_LIGHT_PROFILE"]:
            has_failures = True
            failures.append("LOW_CONFIDENCE")
            
    dest_category_dir = failure_dir if has_failures else clean_dir
    next_id = get_next_label_id(clean_dir, failure_dir)
    target_img_name = f"label_{next_id:06d}.jpg"
    target_txt_name = f"label_{next_id:06d}.txt"
    
    dest_img_path = dest_category_dir / target_img_name
    dest_txt_path = dest_category_dir / target_txt_name
    
    # 1. Save / Copy Image (convert to JPG if original is PNG using PIL if available)
    try:
        from PIL import Image
        with Image.open(raw_img_path) as img:
            img.convert("RGB").save(dest_img_path, "JPEG")
    except ImportError:
        # Fallback to direct file copy
        shutil.copy2(raw_img_path, dest_img_path)

    # 2. Build Annotation File Content
    raw_ingredients = []
    expected_canonicals = []
    
    for item in semantic_data:
        raw_ingredients.append(item.get("originalText", ""))
        expected_canonicals.append(item.get("canonicalName", ""))
        
    raw_text = "Ingredients: " + ", ".join(filter(None, raw_ingredients))
    canonical_text = "\n".join(filter(None, expected_canonicals))
    
    # Read metrics for logging if present
    metrics_file = source_path / "metrics" / "metrics.json"
    calories = "Unknown"
    sodium = "Unknown"
    
    # Parse failures tags
    failures_tags = "\n".join(set(failures)) if failures else "clean"
    
    annotation_content = f"""[RAW INGREDIENTS]
{raw_text}

[EXPECTED CANONICAL]
{canonical_text}

[NUTRITION VALUES]
Calories: {calories}
Sodium: {sodium}

[FAILURE_TAGS]
{failures_tags}
"""

    with open(dest_txt_path, "w", encoding="utf-8") as txt_file:
        txt_file.write(annotation_content)
        
    # Save Normalized and Canonical ground truths
    try:
        from benchmark.scripts.benchmark.pipeline_stages import TextNormalizer, IngredientExtractor, IngredientCanonicalizer, IngredientVocabulary
        
        extracted_section = IngredientExtractor.extract_raw_section(raw_text)
        normalized_text = TextNormalizer.normalize(extracted_section)
        
        vocab = IngredientVocabulary().get_vocabulary()
        tokens = IngredientExtractor.tokenize(normalized_text, vocab)
        canonical_tokens = [IngredientCanonicalizer.canonicalize(t) for t in tokens if t.strip()]
        canonical_text_out = "\n".join(canonical_tokens)
        
        processed_dir = DATASETS_DIR / "processed"
        normalized_dir = processed_dir / "normalized"
        canonical_dir = processed_dir / "canonical"
        
        normalized_dir.mkdir(parents=True, exist_ok=True)
        canonical_dir.mkdir(parents=True, exist_ok=True)
        
        with open(normalized_dir / f"label_{next_id:06d}_normalized.txt", "w", encoding="utf-8") as f_norm:
            f_norm.write(normalized_text)
            
        with open(canonical_dir / f"label_{next_id:06d}_canonical.txt", "w", encoding="utf-8") as f_canon:
            f_canon.write(canonical_text_out)
            
    except Exception as e:
        print(f"[-] Warning: Failed to write processed normalized/canonical files on ingestion: {e}")
        
    w, h = get_image_dimensions(dest_img_path)
    category_name = dest_category_dir.name
    print(f"[+] Assembled: {target_img_name} ({w}x{h}) & {target_txt_name} -> {category_name}")
    return True

def main():
    parser = argparse.ArgumentParser(description="NutriGuard Corpus Assembly Line Ingestor")
    parser.add_argument("--src", required=True, help="Path to exported zip file, directory of zips, or export folder")
    parser.add_argument("--dest-clean", help="Target clean raw labels path")
    parser.add_argument("--dest-failures", help="Target failure/difficult labels path")
    parser.add_argument("--validate", action="store_true", default=True, help="Trigger validate_dataset_integrity.py automatically")
    args = parser.parse_args()
    
    src_path = Path(args.src).resolve()
    clean_dir = Path(args.dest_clean).resolve() if args.dest_clean else DATASETS_DIR / "raw" / "clean_labels"
    failure_dir = Path(args.dest_failures).resolve() if args.dest_failures else DATASETS_DIR / "raw" / "difficult_fonts"
    
    clean_dir.mkdir(parents=True, exist_ok=True)
    failure_dir.mkdir(parents=True, exist_ok=True)
    
    temp_dir = BENCHMARK_DIR / "temp_assembly"
    if temp_dir.exists():
        shutil.rmtree(temp_dir)
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    success_count = 0
    try:
        # Determine files to process
        sources = []
        if src_path.is_file() and src_path.suffix.lower() == ".zip":
            sources.append(src_path)
        elif src_path.is_dir():
            # Check if it contains manifest.json directly
            if (src_path / "manifest.json").exists():
                sources.append(src_path)
            else:
                # Find all zip files or subfolders
                sources.extend(list(src_path.glob("*.zip")))
                sources.extend([d for d in src_path.iterdir() if d.is_dir() and (d / "manifest.json").exists()])
                
        if not sources:
            print(f"[-] No valid export zip files or manifest folders found at: {src_path}")
            sys.exit(0)
            
        for source in sources:
            try:
                run_temp = temp_dir / source.stem
                run_temp.mkdir(parents=True, exist_ok=True)
                
                unpacked_path = parse_session_export(source, run_temp)
                if process_export(unpacked_path, clean_dir, failure_dir):
                    success_count += 1
            except Exception as e:
                print(f"[-] Error processing {source.name}: {e}", file=sys.stderr)
                
    finally:
        if temp_dir.exists():
            shutil.rmtree(temp_dir)
            
    print(f"\n[+] Ingestion cycle completed. Successfully imported {success_count} new entries.")
    
    if success_count > 0 and args.validate:
        print("\n[*] Running dataset validation & split manifests regeneration...")
        validate_script = SCRIPT_DIR / "validate_dataset_integrity.py"
        if validate_script.exists():
            import subprocess
            res = subprocess.run([sys.executable, str(validate_script)], capture_output=True, text=True)
            print(res.stdout)
            if res.returncode != 0:
                print(res.stderr, file=sys.stderr)
                sys.exit(res.returncode)
        else:
            print(f"[-] Warning: validate_dataset_integrity.py not found at {validate_script}")

if __name__ == "__main__":
    main()
