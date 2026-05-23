# NutriGuard - Replay Ingestion System

This document describes the offline replay recording mechanism, serialization schemas, failure conditions, and inspection layouts in NutriGuard.

## Purpose

The replay system provides engineering-grade visibility into the offline ingestion pipeline. When a scan triggers a pipeline quality exception (e.g. low resolution confidence or extraction anomaly), the app automatically records a structured JSON snapshot to local cache. This ensures developers can debug incorrect canonical mappings and identify regressions.

## Storage Folder Layout

Replay files are serialized inside the application's local cache directory:
```
/data/user/0/com.example/cache/
  ├── {replay_id}_replay.json
  ├── {replay_id}_replay.json
  └── ...
```
Filenames are prefixed with a deterministic 16-character SHA-256 hash of the input signature (source image filename + timestamp).

## Replay JSON Schema Specification

```json
{
  "replay_id": "8f3a5e1d904c6b2a",
  "source_image": "in123.jpg",
  "ocr_output": "INGREDIENTS: SUGAR, SALY, CITRIC ACID.",
  "normalized_text": "ingredients sugar saly citric acid",
  "extracted_ingredients": [
    "sugar",
    "saly",
    "citric acid"
  ],
  "canonical_ingredients": [
    {
      "originalToken": "sugar",
      "correctedToken": "sugar",
      "canonicalToken": "sugar",
      "confidence": 1.0,
      "matchType": "EXACT"
    },
    {
      "originalToken": "saly",
      "correctedToken": "salt",
      "canonicalToken": "salt",
      "confidence": 0.65,
      "matchType": "FUZZY"
    },
    {
      "originalToken": "citric acid",
      "correctedToken": "citric acid",
      "canonicalToken": "citric acid",
      "confidence": 1.0,
      "matchType": "EXACT"
    }
  ],
  "metrics": {
    "avg_confidence": 0.8833,
    "ingredient_count": 3.0,
    "ocr_character_count": 38.0
  },
  "failures": [
    {
      "failure_type": "LOW_CONFIDENCE_FAILURE",
      "stage": "canonicalization",
      "details": "One or more ingredients resolved with confidence less than 75%."
    }
  ],
  "latency_metrics_ms": {
    "ocr": 145,
    "normalization": 2,
    "extraction": 4,
    "alias resolution": 8,
    "canonicalization": 3
  },
  "pipeline_version": "1.0.0",
  "benchmark_schema_version": "1.0.0",
  "dataset_version": "1.0.0",
  "timestamp": "2026-05-23T12:00:00.000Z"
}
```

## Failure Detection Logic (Android Runtime)

Since ground-truth labels are isolated from the Android runtime environment, the app flags failures using execution heuristics:

1. **LOW_CONFIDENCE_FAILURE**
   - **Trigger**: Any canonicalized ingredient maps with a confidence value `< 0.75f`.
   - **Details**: Tracks OCR corruption or missing vocabulary mapping.
2. **EXTRACTION_FAILURE**
   - **Trigger**: `extractedTokens` list is empty, but `ocrText` contains alphanumeric blocks.
   - **Details**: Captures section parsing/delimiter-splitting failures.
