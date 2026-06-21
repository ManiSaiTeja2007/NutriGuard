# Duplicate Logic Audit — Stage 13.0D

This document records the business logic duplication audit conducted across all major functional subsystems of NutriGuard to ensure clean architectural division and single points of authority.

---

## 1. Duplication Audit Matrix

We analyzed every functional area for duplicated implementations, parallel processing logic, or overlapping responsibilities.

| Functional Area | Subsystem Components | Classification | Rationale & Evidence |
| :--- | :--- | :---: | :--- |
| **Normalization** | `TextNormalizer` vs `IngredientNormalizer` | **KEEP** | `TextNormalizer` cleans raw full-text OCR blocks (newlines, punctuation spacing, junk chars). `IngredientNormalizer` cleans single extracted tokens (punctuation stripping, E-number spacing). They are complementary and have distinct scopes. |
| **Alias Mapping** | `AliasRepairEngine` | **KEEP** | `AliasRepairEngine` is the single authority for resolving synonym mappings. No duplicate engine exists. |
| **Ingredient Interpretation** | ViewModel loops vs `AggregationStage` / `ConfidenceCalibrationStage` | **DEPRECATE** | Pre-converged ViewModel manually iterated tokens to invoke `IngredientInterpreter.interpret`. In converged graph path, this is performed during the `AggregationStage` / `ConfidenceCalibrationStage`, and ViewModel maps straight from `PipelineResult.interpretedIngredients`. We deprecate and disable the manual ViewModel loop. |
| **Routing** | `SemanticRouter` vs `OCRPipelineRouter` | **KEEP** | `SemanticRouter` dispatches parsed section lines to domains (allergens, nutrition, ingredients). `OCRPipelineRouter` chooses OCR tiling strategies based on image size and clarity. Distinct roles. |
| **Confidence Scoring** | Graph Calibration vs Legacy Pipeline Scoring | **DEPRECATE** | Legacy pipeline calculated confidence bonuses directly inside parsing loops. Converged path uses the explicit `ConfidenceCalibrationStage` at Stage 8. Legacy scoring is deprecated and kept only under the rollback switch. |
| **OCR Utilities** | `OcrCameraFrameAnalyzer` vs `TargetedOcrCoordinator` | **KEEP** | `OcrCameraFrameAnalyzer` runs live background OCR for bounding box overlays (user guidance). `TargetedOcrCoordinator` executes cropped OCR on demand during ingestion. Decoupled to avoid duplicate background thread conflicts. |
| **Replay Serialization** | `ReplayStorageHelper` vs `ReplayGenerationStage` | **MERGE** | `ReplayGenerationStage` compiles individual stage latency and artifact traces into `PipelineResult`. `ReplayStorageHelper` serializes this compiled list to the cache folder on disk. They are merged into a single coordinated pipeline output chain. |
| **Classification Helpers**| `SemanticSectionClassifier` | **KEEP** | Single authority for matching and labeling section headings. No duplicate. |
| **Text Recovery** | `TextNormalizer` | **KEEP** | Recovering hyphenated linebreaks is performed solely by `TextNormalizer.normalize`. No duplicate logic. |
| **Preprocessing** | `OcrPreprocessor` | **KEEP** | Contrast sharpening and CLAHE are run solely by `OcrPreprocessor` on demand. No duplicate. |

---

## 2. Convergence Evidence

- **Zero Parallel Run**: `ScanViewModel.kt` executes `PipelineRunner.run(...)` as the sole ingestion processor. No parallel execution of legacy `semanticPipeline` is performed when the graph flag is active, ensuring zero duplicate logic executions in production.
- **Direct Navigation Mapping**: `ScanViewModel` now maps straight from the structured domains returned in `PipelineResult` (such as `allergenInterpretation` and `nutritionInterpretation`), bypassing duplicate parsing blocks.
