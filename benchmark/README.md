# NutriGuard Dataset Benchmarking & Scientific Evaluation Guide

Welcome to the NutriGuard offline-first dataset infrastructure guide. This subsystem provides a structured organization for raw datasets, synthetic variations, manifests, and validation pipelines to enable reproducible testing.

---

## 1. Directory Structure

The repository benchmark folder is structured as follows:

```text
benchmark/
├── datasets/
│   ├── raw/                            # Immutable raw downloads
│   │   ├── clean_labels/               # High-contrast, readable labels
│   │   ├── blurry_labels/              # Out-of-focus captures
│   │   ├── rotated_labels/             # Non-horizontal images (90, 180, 270 deg)
│   │   ├── low_light/                  # Dark or shaded photos
│   │   ├── multilingual/               # Non-English/mixed language text
│   │   ├── curved_packaging/           # Circular canisters or bottles
│   │   ├── noisy_backgrounds/          # Patterned packaging backdrops
│   │   ├── partial_occlusion/          # Folded labels or fingers in frame
│   │   ├── handwritten/                # Script/cursive style text
│   │   └── difficult_fonts/            # Stylized or low-contrast typography
│   │
│   ├── processed/                      # Preprocessed pipeline outputs
│   │   ├── normalized/                 # Output of TextNormalizer
│   │   ├── resized/                    # Standardized image resolutions
│   │   └── canonical/                  # Output of Canonicalizer
│   │
│   └── synthetic/                      # Programmatically altered datasets
│       ├── generated_blur/             # Gaussian blurred copies
│       ├── generated_rotation/         # Rotated variants (90 deg)
│       ├── generated_noise/            # Salt-and-pepper noise injected
│       └── generated_lowlight/         # Reduced brightness simulations
│
├── manifests/                          # Split and integrity index files
│   ├── master_manifest.json            # All verified dataset records
│   ├── train_manifest.json             # Stratified random train split (70%)
│   ├── validation_manifest.json        # Stratified random validation split (15%)
│   ├── test_manifest.json              # Stratified random test split (15%)
│   └── manifest_v1.json                # Archive copy of master
│
├── reports/                            # Latency and accuracy metrics
│
├── replays/                            # OCR debug logs sandbox
│   ├── raw_ocr_outputs/                # Saved ML Kit outputs
│   ├── canonicalization_outputs/       # Final mapped logs
│   ├── failed_cases/                   # Captured failure cases
│   └── diff_reports/                   # Side-by-side comparison logs
│
├── subsets/                            # Sample subsets configurations
│
├── download_cache/                     # Staging cache for download raw files
│
└── scripts/                            # Pipeline execution utilities (Python)
    ├── download/
    │   └── download_off_images.py      # Staged, safe dataset downloader
    ├── prepare_dataset_structure.py    # Creates folder tree and seeds assets
    ├── generate_synthetic_variations.py# Renders synthetic PIL variations
    └── validate_dataset_integrity.py   # Validates naming, parity, and splits data
```

---

## 2. Setup & Download Instructions

### Step A: Initialize the Directories & Seed Samples
This creates all folders and seeds initial mock clean label files (`label_000001.jpg` to `label_000005.jpg`) with annotations:
```powershell
python benchmark/scripts/prepare_dataset_structure.py
```

### Step B: Download Real Packaging Labels (Optional/Subset)
To retrieve real food packaging label images from OpenFoodFacts, stage them in cache, rename them deterministically, and move them into `raw/clean_labels/`:
```powershell
python benchmark/scripts/download/download_off_images.py
```

---

## 3. Ground Truth Annotation Schema (`.txt` files)
Every image file `label_XXXXXX.jpg` must have a corresponding annotation file `label_XXXXXX.txt` in the same directory using this structured format:

```text
[RAW INGREDIENTS]
Ingredients: sugar, salt, citric acid, msg

[EXPECTED CANONICAL]
sugar
salt
citric acid
monosodium glutamate

[NUTRITION VALUES]
Calories: 120
Sodium: 150mg

[FAILURE_TAGS]
blur
low_light
curved_text
```

---

## 4. Synthetic Data Augmentation
To apply Gaussian blur, rotations, noise, and low-light operations programmatically to clean labels, run:
```powershell
python benchmark/scripts/generate_synthetic_variations.py
```
*Note: If the `Pillow` library is missing, install it via `pip install Pillow`. The script will gracefully copy files as-is in fallback mode if Pillow is not present.*

---

## 5. Dataset Validation & Train/Val/Test Splits
To verify directory structures, check image/annotation parity, compute file checksums, detect duplicates, and generate stratified splits:
```powershell
python benchmark/scripts/validate_dataset_integrity.py
```
This writes `master_manifest.json`, `train_manifest.json` (70%), `validation_manifest.json` (15%), and `test_manifest.json` (15%) inside the `manifests/` directory.

---

## 6. Deterministic Benchmark Philosophy
- **Fixed Random Seed**: All random shuffling operations use seed `42` to guarantee identical split allocations across runs.
- **Parity Verification**: The pipeline strictly requires matching `.jpg` and `.txt` files under standard names (`label_\d{6}.jpg`) to avoid untracked assets.
- **Stratified Balances**: Train/Val/Test splits are computed on a per-category basis to guarantee that all dataset conditions (clean, blurry, low-light, synthetic) are equally represented across all training and evaluation segments.

---

## 7. Failure Taxonomy Reference

We classify failures into 9 categories to quickly diagnose stage-level regressions:
* **`OCR_FAILURE`**: Triggered when Character Error Rate (CER) > 15.0% or Word Error Rate (WER) > 35.0%.
* **`NORMALIZATION_FAILURE`**: Occurs if normalized text contains structural mismatches, leftover newlines, or failed linebreak hyphens.
* **`TOKENIZATION_FAILURE`**: Triggered when top-level delimiters (commas or semicolons) are missed, resulting in a single multi-word block instead of individual tokens.
* **`EXTRACTION_FAILURE`**: Flagged when ingredient list extraction yields an F1-score below 90.0%.
* **`ALIAS_FAILURE`**: Triggered if spelling corrections resolve spelling errors incorrectly.
* **`CANONICALIZATION_FAILURE`**: Triggered if final alias mappings to the canonical names differ from the expected ground truth.
* **`LOW_CONFIDENCE_FAILURE`**: Triggered when the average alias correction confidence is below 75.0%.
* **`TRUNCATION_FAILURE`**: Triggered when the count of extracted tokens drops by more than 50% compared to expected annotations.
* **`NOISE_FAILURE`**: Triggered when noise/blurry inputs degrade OCR quality below acceptable thresholds.

---

## 8. Benchmark Success & Quality Thresholds

| Metric | Target Success Threshold | Critical Failure Threshold |
|---|---|---|
| **Character Error Rate (CER)** | `<= 5.0%` | `> 15.0%` (OCR Failure) |
| **Word Error Rate (WER)** | `<= 10.0%` | `> 35.0%` (OCR Failure) |
| **Extraction F1-Score** | `>= 90.0%` | `< 80.0%` (Extraction Failure) |
| **Canonical Accuracy** | `>= 85.0%` | `< 70.0%` (Canonical Failure) |
| **Ingestion Latency** | `<= 100ms / frame` | `> 500ms` (Performance regression) |

---

## 9. Replay Troubleshooting Workflow

To debug drift between Python evaluations and the Android Kotlin pipeline:
1. **Locate Replays**: Inspect `benchmark/replays/failed_cases/` for failed items.
2. **Review JSON Snapshots**: Open the `{replay_id}_replay.json` containing step-by-step variables (`ocr_output`, `normalized_text`, `extracted_ingredients`, `canonical_ingredients`).
3. **Reproduce Locally**: Run the benchmark runner pointing to the specific subset or stage to inspect local variables:
   ```powershell
   python benchmark/scripts/benchmark_runner.py --stage extraction --subset blurry
   ```
4. **Align Code**: Ensure changes are updated in both Kotlin pipeline classes and Python runner helpers in tandem.

---

## 10. Curated Subsets Strategy

We run isolated evaluations against these subsets to identify specific domain weaknesses:
* **`clean`**: Baseline clean label readability test.
* **`blurry`**: Focuses on out-of-focus camera capture simulations.
* **`low_light`**: Verifies shadows and brightness-reduced OCR performance.
* **`curved_packaging`**: Validates text bending and curved bottle packaging challenges.
* **`multilingual`**: Validates mixed languages and multi-lingual ingredient labels.
* **`catastrophic_ocr`**: Evaluates extreme rotations, handwriting, and stylized fonts.

---

## 11. Pipeline & Schema Versioning Parity
To guarantee reproducibility:
- Every **manifest** (`master_manifest.json`, etc.), **replay** (`*_replay.json`), and **report** (`benchmark_report_*.json`) is stamped with the `pipeline_version`, `benchmark_schema_version`, and `dataset_version`.
- Any version mismatch will trigger a compatibility validation warning during dataset scanning.

