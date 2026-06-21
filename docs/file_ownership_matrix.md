# File Ownership Matrix — Stage 13.0D

This document maps and classifies the major source files of the NutriGuard project under the converged runtime architecture to identify the authoritative components and plot the path for future legacy retirement.

---

## 1. File Classification Registry

Every major file is classified as one of:
- **`AUTHORITATIVE`**: Part of the single runtime authority.
- **`TRANSITIONAL`**: Active, but contains rollback options or temporary adapters.
- **`DEPRECATED`**: Legacy code maintained solely for fallback/validation.
- **`DELETE_CANDIDATE`**: Obsolete code to be removed after retirement gates are met.

| File Path | Functional Area | Classification | Runtime Status | Test Status | Deletion Risk | Replacement / Target State |
| :--- | :--- | :---: | :--- | :--- | :--- | :--- |
| **[PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt)** | Execution Orchestrator | **AUTHORITATIVE** | Sole Ingestion Entry | Covered by Smoke & VM Tests | High | None. Single authority. |
| **[SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt)** | Execution Graph | **AUTHORITATIVE** | Orchestrates 8 Stages | Covered by Headless & VM Tests | High | None. Main execution engine. |
| **[ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt)** | Presentation / Ingestion | **TRANSITIONAL** | Ingests via PipelineRunner, has legacy fallback | VM Instrumented Tests | High | Refactor in Stage 13.0D.5 to remove legacy fallback branch. |
| **[SemanticPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt)** | Legacy Ingestion | **DEPRECATED** | Fallback path / Used in Graph Stage 5 | Legacy Regression Tests | Low | Keep for fallback until retirement gate Stage 13.0D.5. |
| **[OcrCameraFrameAnalyzer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/ocr/OcrCameraFrameAnalyzer.kt)** | Preview OCR Overlay | **AUTHORITATIVE** | UI guidance / Bounding boxes | UI Preview Tests | High | None. Decoupled from Ingestion. |
| **[StructuralLayoutAnalyzer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/StructuralLayoutAnalyzer.kt)** | Layout Analysis | **AUTHORITATIVE** | Stage 1 (Profiling) | Headless & VM Tests | High | None. |
| **[TargetedOcrCoordinator.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/TargetedOcrCoordinator.kt)** | Zoned OCR Dispatching | **AUTHORITATIVE** | Stage 2 (OCR Zone crop) | Headless & VM Tests | High | None. |
| **[SemanticSectionClassifier.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticSectionClassifier.kt)**| Section Labeling | **AUTHORITATIVE** | Stage 3 (Keyword match) | Classifier Unit Tests | High | None. |
| **[SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt)** | Domain Routing | **AUTHORITATIVE** | Stage 4 (Domain partition) | Router Unit Tests | High | None. |
| **[SpecializedInterpretationStage.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SpecializedInterpretationStage.kt)**| Graph Legacy Wrapper | **TRANSITIONAL** | Stage 5 (Wraps legacy pipeline) | Headless & Smoke Tests | Medium | Rewrite in Stage 14.0 to parse ingredients directly without legacy pipeline. |
| **[ContextualReconstructionStage.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/ContextualReconstructionStage.kt)**| Correction / Context | **AUTHORITATIVE** | Stage 6 (Text fix) | Regression Tests | High | None. |
| **[AggregationStage.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/AggregationStage.kt)** | Output Aggregation | **AUTHORITATIVE** | Stage 7 (Combines maps) | Headless & VM Tests | High | None. |
| **[ConfidenceCalibrationStage.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/ConfidenceCalibrationStage.kt)**| Score Calculation | **AUTHORITATIVE** | Stage 8 (Weights match) | Calibration Tests | High | None. |
| **[ReplayGenerationStage.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/ReplayGenerationStage.kt)** | Replay Generation | **AUTHORITATIVE** | Stage 9 (Trace compiler) | Replay Consistency Tests | High | None. |
| **[AllergenInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/AllergenInterpreter.kt)** | Allergen Domain | **AUTHORITATIVE** | Decodes allergens | Allergen Unit Tests | High | None. |
| **[NutritionInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/NutritionInterpreter.kt)** | Nutrition Domain | **AUTHORITATIVE** | Decodes nutrition values | VM Ingestion Tests | High | None. |
| **[IngredientInterpreter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/intelligence/IngredientInterpreter.kt)**| Ingredient Domain | **AUTHORITATIVE** | Decodes single ingredients | Intelligence Unit Tests | High | None. |
| **[AliasRepairEngine.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/aliases/AliasRepairEngine.kt)** | Alias Resolver | **AUTHORITATIVE** | Resolves synonyms | Intelligence Unit Tests | High | None. |
| **[TextNormalizer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/normalization/TextNormalizer.kt)** | Full Text Sanitizer | **AUTHORITATIVE** | Cleans raw lines | Text Normalizer Tests | High | None. |
| **[IngredientNormalizer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/normalization/IngredientNormalizer.kt)**| Token Sanitizer | **AUTHORITATIVE** | Cleans single tokens | Text Normalizer Tests | High | None. |
| **[ReplayStorageHelper.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt)** | Trace Persister | **AUTHORITATIVE** | Writes logs to disk | Replay Unit Tests | High | None. |
| **[FeatureFlags.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/config/FeatureFlags.kt)** | Feature Flags | **TRANSITIONAL** | Holds execution graph switch | VM Tests | High | Remove `useExecutionGraph` switch in Stage 13.0D.5. |

---

## 2. Governance Protections

1. **Delete Candidate Rule**: No file marked as `DELETE_CANDIDATE` or `DEPRECATED` may be deleted while `ScanViewModel` contains any references to it or if it is required for rollback validation.
2. **Transition File Progress**: Transitional files must be updated to Authoritative state as their dependencies converge.
