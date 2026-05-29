# Replay Ingestion Specification Document

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **How does the replay ingestion system work and what is its JSON schema?** (Offline replay mechanisms, JSON serialization schemas, failure detection heuristics, storage folder layouts)
>
> This document does NOT answer:
> * **What systems exist and who owns them?** (See [system_inventory.md](file:///d:/projects/Ongoing/nutriguard/docs/system_inventory.md))
> * **What actually executes?** (See [runtime_audit.md](file:///d:/projects/Ongoing/nutriguard/docs/runtime_audit.md))
> * **What has been verified?** (See [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md))

This document defines the storage layout, JSON schemas, and failure detection rules for the offline replay reporting mechanism in the NutriGuard platform.

---

## 1. System Purpose & Overview

The replay system provides diagnostic visibility into the offline Edge AI ingestion pipeline. When a scan triggers an execution quality anomaly (such as low confidence maps or extraction parsing failures), the application records a structured JSON execution trace to local cache. This ensures developers can debug incorrect canonical mappings and resolve regressions.

---

## 2. Storage Folder Layout

Replay files are serialized inside the application's local sandbox cache directory:
```
/data/user/0/com.example/cache/
  ├── {replay_id}_replay.json
  ├── {replay_id}_replay.json
  └── ...
```
Filenames are prefixed with a deterministic 16-character SHA-256 hash of the input signature (source image filename + execution timestamp).

---

## 3. Replay JSON Schema Specification

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

---

## 4. Failure Detection Heuristics (Android Runtime)

Since ground-truth labels are isolated from the Edge AI Android runtime, the application flags scan failures using execution indicators:

1. **LOW_CONFIDENCE_FAILURE**:
   - **Trigger**: Any canonicalized ingredient maps with a confidence value `< 0.75f`.
   - **Details**: Captures OCR corruption or missing vocabulary mappings.
2. **EXTRACTION_FAILURE**:
   - **Trigger**: `extractedTokens` list is empty, but `ocrText` contains alphanumeric character blocks.
   - **Details**: Captures section parsing/delimiter-splitting failures.
