# NutriGuard - Benchmark & Parity Pipeline

This document explains the separation of concerns between the scientific Python benchmark suite and the local Android Kotlin validation/benchmarking utilities.

## Conceptual Separation of Concerns

To preserve runtime resources and prevent execution coupling, the benchmark architecture enforces a clean separation of boundaries:

```
[ Android Runtime App (Kotlin) ]
  ├── Simulates ingestion throughput
  ├── Gathers frame/OCR/ingestion latencies
  └── Logs cache replays on low confidence
          │
          ▼ (Exported JSON Replays)
[ Scientific Evaluation Suite (Python) ]
  ├── Computes scientific metrics (CER, WER, F1) against ground-truth labels
  ├── Classifies failures into standard taxonomy
  └── Generates regression test reports
```

### 1. Kotlin Runtime Benchmarking
- **Inputs**: Seeded test assets under `assets/test_labels/` (`in1.jpg` to `in182.jpg`).
- **Functionality**:
  - Automatically loads the select test assets.
  - Feeds them to the `FramePipeline` and `OcrPipeline` sequentially.
  - Measures latency for OCR, text normalization, ingredient extraction, alias resolving, and canonicalization.
  - Calculates throughput (Average Latency per Image) and logs average ingredients found.
  - *Does not run ground-truth diffing to conserve android execution performance.*

### 2. Python Scientific Benchmarking
- **Inputs**: Local JSON replays and master datasets (raw labels vs. annotated expected ground-truth files).
- **Parity Metrics**:
  - **CER (Character Error Rate)**: Computes edit distance at the character level to evaluate OCR quality. Target: `< 0.15` (15%).
  - **WER (Word Error Rate)**: Computes word-level insertions, substitutions, and deletions. Target: `< 0.35` (35%).
  - **F1-Score**: Measures harmonic mean of Precision (relevant ingredients / total extracted) and Recall (extracted ingredients / total expected). Target: `> 0.90` (90%) for Extraction and `1.0` (100%) for Canonicalization.

## Parity Sync Discipline

The normalization and extraction behaviors between Kotlin and Python are behaviorally aligned:
- **Normalization**: Unicode cleanups, character substitutions, and bracket/parentheses stripping must match regex-for-regex.
- **Alias Resolution**: Both pipelines load identical alias databases for correct resolution.
- **Canonicalization**: Words are mapped to standard terms defined in the curated ingredient list.
