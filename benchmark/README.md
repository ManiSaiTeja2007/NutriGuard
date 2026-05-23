# NutriGuard Benchmark Dataset Infrastructure

Welcome to the NutriGuard offline-first benchmarking subsystem! This directory contains the scripts, schemas, and configurations required to download, structure, and evaluate datasets for OCR quality, normalization accuracy, and canonicalization recall.

---

## 1. Directory Structure Overview

The approved layout separates raw source data, ground truth annotations, evaluation scripts, and replay dumps:

```text
benchmark/
├── raw/                            # Immutable raw dataset downloads
│   ├── openfoodfacts/              # Raw product files from OFF
│   ├── spellcheck/                 # Raw spelling typo datasets
│   ├── kaggle_labels/              # Raw Kaggle labels and images
│   ├── ingredient_detection/       # Target ingredient packaging bounding boxes
│   └── custom/                     # Custom developer additions
│
├── processed/                      # Preprocessed ground truth datasets
│   ├── ocr_ground_truth/           # Raw OCR text annotations
│   ├── normalized_ground_truth/    # Cleaned, standardized tokens
│   ├── canonical_ground_truth/     # Ground truth canonical groupings
│   ├── benchmark_subsets/          # Sampled subsets for quick local verification
│   └── reports/                    # CER/WER and recall metric outputs
│
├── manifests/                      # Integrity manifests (JSON formats)
│
├── replays/                        # OCR replay dumps for debugging failures
│
├── scripts/                        # Automation code (Python)
│   ├── download/                   # Downloader scripts (OFF, Norvig, Kaggle)
│   ├── preprocess/                 # Text extractor mapping to Ground Truth format
│   └── benchmark/                  # Metric execution (CER, WER, F1)
│
└── README.md                       # Subsystem documentation (this file)
```

---

## 2. Dataset Setup Instructions

### Prerequisites
Make sure Python 3 is installed on your system. You can verify it by running:
```powershell
python --version
```

---

### Step A: Download OpenFoodFacts Product Database (Subset Mode)
Runs a query against the live search API of OpenFoodFacts, fetches 50 representative products with full ingredients text, writes the file to the `raw/` directory, and outputs its manifest.
```powershell
python benchmark/scripts/download/download_openfoodfacts.py
```
- **Target Output**: `benchmark/raw/openfoodfacts/off_products_subset.json`
- **Manifest**: `benchmark/manifests/openfoodfacts_manifest.json`

---

### Step B: Download Spelling Corrections Corpus
Downloads Peter Norvig's spell check error list containing typical human spelling mistakes, calculates its SHA-256 hash, and generates the manifest.
```powershell
python benchmark/scripts/download/download_spellcheck.py
```
- **Target Output**: `benchmark/raw/spellcheck/norvig_spell_errors.txt`
- **Manifest**: `benchmark/manifests/spellcheck_manifest.json`

---

### Step C: Download Kaggle Labels & Bounding Boxes
*Note: Requires Kaggle credentials setup.*
```powershell
python benchmark/scripts/download/download_kaggle_labels.py
```
- **Target Output**: `benchmark/raw/kaggle_labels/`
- **Manifest**: `benchmark/manifests/kaggle_labels_manifest.json`

#### Kaggle API Credentials Setup
The script verifies that your credentials file (`kaggle.json`) is in the correct folder:
- **Windows**: `C:\Users\<username>\.kaggle\kaggle.json` (or `%USERPROFILE%\.kaggle\kaggle.json`)
- **macOS / Linux**: `~/.kaggle/kaggle.json`

To get your `kaggle.json`:
1. Log into [Kaggle](https://www.kaggle.com).
2. Open your Profile -> Settings page.
3. Click **Create New Token** in the API section.
4. Save the downloaded file to the directories shown above.
5. Install the Kaggle CLI utility:
   ```powershell
   pip install kaggle
   ```

---

## 3. Ground Truth Annotation Schema

Each item in our `processed/ocr_ground_truth/` dataset follows this standardized JSON schema:

```json
{
  "image": "label_001.jpg",
  "product_name": "Product Name Example",
  "ground_truth_text": "ingredients: sugar, salt, citric acid",
  "expected_ingredients": [
    "sugar",
    "salt",
    "citric acid"
  ],
  "expected_canonical": [
    "sugar",
    "salt",
    "citric acid"
  ]
}
```

This ensures that evaluations can cleanly measure performance across all levels of the pipeline:
1. **OCR level**: compares image/live OCR text against `ground_truth_text`.
2. **Extraction level**: compares token output against `expected_ingredients`.
3. **Intelligence level**: compares final mapping against `expected_canonical`.

---

## 4. Run Preprocessing Pipeline
To transform raw downloaded OpenFoodFacts JSON files into our structured Ground Truth schema, execute:
```powershell
python benchmark/scripts/preprocess/preprocess_datasets.py
```
This generates structured `.json` records matching our schema format and saves them inside `benchmark/processed/ocr_ground_truth/`.

---

## 5. Manifest Schema Documentation

All dataset manifests generated in `benchmark/manifests/` adhere to this metadata standard:

```json
{
  "dataset_name": "Dataset Display Name",
  "source": "URL or API endpoint used",
  "downloaded_at": "ISO 8601 Timestamp (UTC)",
  "subset_size": 1000,
  "file_count": 1,
  "files": [
    {
      "path": "relative/path/to/file",
      "size_bytes": 10240,
      "checksum_sha256": "sha256-hash-value"
    }
  ],
  "preprocessing_lineage": {
    "raw_status": "downloaded",
    "annotation_version": "1.0.0",
    "normalized_version": "1.0.0"
  }
}
```

---

## 6. OCR Replay System

The `benchmark/replays/` folder acts as an offline sandbox for capturing failures:
- When a benchmark test fails, the corresponding raw OCR text and confidence values are dumped to a `.json` file inside `replays/`.
- Developers can replay these raw logs through the composed text-intelligence stages (`NormalizationStage`, `ExtractionStage`, etc.) to trace and fix typos, vocabulary limitations, or parsing issues without deploying to a live device or repeating image OCR capture.

---

## 7. Future Evaluation Metrics

The script `benchmark/scripts/benchmark/benchmark_pipeline.py` implements the math for evaluating NutriGuard's pipelines:

- **Character Error Rate (CER)**: Measures OCR character differences:
  $$\text{CER} = \frac{D_{\text{Levenshtein}}(\text{GT}, \text{Hypothesis})}{\text{Length}(\text{GT})}$$
- **Word Error Rate (WER)**: Measures OCR word alignment errors:
  $$\text{WER} = \frac{D_{\text{Levenshtein}}(\text{GT\_words}, \text{Hypothesis\_words})}{\text{Length}(\text{GT\_words})}$$
- **Fuzzy Recall & Precision**: Measures ingredient intelligence:
  $$\text{Precision} = \frac{\text{True Positives}}{\text{Extracted Set Size}}$$
  $$\text{Recall} = \frac{\text{True Positives}}{\text{Ground Truth Set Size}}$$

To run the mock evaluation pass and verify calculations:
```powershell
python benchmark/scripts/benchmark/benchmark_pipeline.py
```

---

## 8. Licensing and Terms

By downloading datasets from OpenFoodFacts and Kaggle, you agree to respect their corresponding licenses:
- **OpenFoodFacts**: Open Database License (ODbL) and Database Contents License (DbCL).
- **Kaggle Datasets**: Check respective dataset pages for license details (typically CC-BY-SA or public domain).
