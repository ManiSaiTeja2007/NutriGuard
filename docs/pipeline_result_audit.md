# Pipeline Result & Models Audit

This document fulfills the Enhancement 2 requirement of Stage 13.0B, auditing `PipelineResult`, `PipelineSnapshot`, the Replay Model, and `Navigation Arguments` to identify field mapping gaps before wiring the presentation layers to the unified execution graph.

---

## 1. Field-Level Structure Comparison

Below is the field audit matching existing data models in the codebase:

| Model / Class | Field Name | Type | Purpose | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| **PipelineResult** | `executionId` | `UUID` / `PipelineExecutionId` | Unique ID of the pipeline execution run | 🟢 Verified |
| | `ocrBlocks` | `List<OCRBlock>` | Raw OCR bounding boxes grouped into blocks | 🟢 Verified |
| | `ocrLines` | `List<OCRLine>` | Vertical/horizontal layout aligned OCR lines | 🟢 Verified |
| | `semanticIngredients` | `List<SemanticIngredient>` | Output ingredients with spelling corrections | 🟢 Verified |
| | `interpretedIngredients`| `List<InterpretedIngredient>`| Ingredients mapped to E-numbers & warnings | 🟢 Verified |
| | `replayTrace` | `List<ReplayStageTrace>` | List of stage latencies and details | 🟢 Verified |
| | `metrics` | `PipelineMetrics` | Processing timing and memory usage telemetry | 🟢 Verified |
| | `preprocessingProfile` | `PreprocessingProfile` | Contrast/blur/brightness diagnostics | 🟢 Verified |
| | `failures` | `List<PipelineFailure>` | In-graph warnings and validation failures | 🟢 Verified |
| | `allergenInterpretation`| `AllergenInterpretation?` | Parsed allergen advice warnings | 🟢 Verified |
| | `nutritionInterpretation`| `NutritionInterpretation?`| Parsed macronutrients | 🟢 Verified |
| | `storageInterpretation` | `StorageInterpretation?` | Storage/temperature instructions | 🟢 Verified |
| | `metadataInterpretation`| `MetadataInterpretation?` | Manufacturer details, batch codes | 🟢 Verified |
| **PipelineSnapshot** | `executionId` | `String` | Snapshot reference key matching PipelineResult | 🟢 Verified |
| | `rawImagePath` | `String` | Sandboxed absolute path to original capture | 🟢 Verified |
| | `preprocessedImagePath`| `String` | Sandboxed absolute path to filtered bitmap | 🟢 Verified |
| | `result` | `PipelineResult` | Core execution output data | 🟢 Verified |
| | `timestamp` | `Long` | Milliseconds execution time | 🟢 Verified |
| | `scanSource` | `String` | Identifier of scan type (Live Camera / Test Asset)| 🟢 Verified |
| **Replay Model** | `replay_id` | `String` | Matches execution ID of capture | 🟢 Verified |
| | `source_image` | `String` | Path / filename of input image source | 🟢 Verified |
| | `ocr_output` | `String` | Raw reconstructed OCR text | 🟢 Verified |
| | `normalized_text` | `String` | Normalized lowercased string | 🟢 Verified |
| | `extracted_ingredients` | `List<String>` | Token list after delimiter splitting | 🟢 Verified |
| | `canonical_ingredients` | `JSONArray` | List of original, corrected, and canonical terms | 🟢 Verified |
| | `failures` | `JSONArray` | Failures/anomaly classification tags | 🟢 Verified |
| | `latency_metrics_ms` | `JSONObject` | Latency breakdown per stage | 🟢 Verified |
| | `timestamp` | `String` | ISO 8601 UTC timestamp string | 🟢 Verified |
| **Results Route Args** | `rawOcrText` | `String` | Raw OCR text string | 🟢 Verified |
| | `normalizedText` | `String` | Lowercased clean delimiters string | 🟢 Verified |
| | `extractedTokens` | `List<String>` | Ingredients token list | 🟢 Verified |
| | `canonicalJson` | `String` | Stringified JSON array of `CorrectionResult` | 🟢 Verified |
| | `latencyJson` | `String` | Stringified JSON map of timing statistics | 🟢 Verified |
| | `executionId` | `String` | Snapshotted execution index | 🟢 Verified |

---

## 2. Identified Mapping Gaps

During this audit, we identified the following gaps that need translation during the migration from legacy linear structures to unified graph results:

1. **FailureType Mapping**:
   - `OcrResult.failures` uses `com.example.core.intelligence.correction.FailureType` enum classes, whereas `PipelineResult.failures` uses `PipelineFailure` data classes wrapping the enum and holding detail descriptions.
   - *Translation Strategy*: In `ScanViewModel.kt`'s navigation converter, map the `PipelineFailure` list to the target format by extracting the enum tags.
2. **Replay Persistence Alignment**:
   - The legacy `ReplayStorageHelper` requires `List<Map<String, Any>>` for `failures` input, whereas the graph pipeline compiles `PipelineFailure` structures.
   - *Translation Strategy*: Add an adapter function or map inside `ScanViewModel.kt` when executing dual parallel validation runs to translate `PipelineFailure` objects into legacy map elements.
3. **Allergen list vs. Ingredient list warnings**:
   - `ResultsScreen.kt` currently processes a flat list of ingredients `CorrectionResult` and looks for inline warning strings. With the execution graph active, allergen alerts are parsed separately inside `AllergenInterpretation`.
   - *Translation Strategy*: For Stage 13.0B, serialize both ingredient warnings and structured allergen warnings into the results layout to prevent display truncation. In later stages, `ResultsScreen.kt` will render a dedicated warnings card.
