# File Streamlining Audit — Stage 13.0E

This document records the streamlined catalog of the NutriGuard codebase files, classifying them to establish clear ownership and plot the deprecation path.

## 1. File Classification Registry

All files are classified as:
- **`AUTHORITATIVE`**: Core production components of the converged execution graph.
- **`TRANSITIONAL`**: Transitional files supporting temporary config flags.
- **`DEPRECATED`**: Retained for backward fallback validation.
- **`DELETE`**: Obsolete or redundant components.

| File Path | functional Area | Classification | Owner / Target State |
| :--- | :--- | :---: | :--- |
| **`PipelineRunner.kt`** | Execution Orchestrator | **AUTHORITATIVE** | Production graph orchestrator |
| **`SemanticExecutionGraph.kt`** | Ingestion Graph | **AUTHORITATIVE** | Executes the 9 stages |
| **`ScanViewModel.kt`** | UI ViewModel | **TRANSITIONAL** | Deferred startup via lazy loading |
| **`ResultsScreen.kt`** | Analysis UI | **AUTHORITATIVE** | Unified Top Bar back navigation |
| **`OcrCameraFrameAnalyzer.kt`** | Camera Preview Overlay | **AUTHORITATIVE** | Bounding boxes live display |
| **`TargetedOcrCoordinator.kt`** | Crop OCR dispatcher | **AUTHORITATIVE** | Bypasses visual complexity routing |
| **`OCRPipeline.kt`** | OCR Engine | **AUTHORITATIVE** | Direct OCR entry point |
| **`SemanticSectionClassifier.kt`**| Heading tagger | **AUTHORITATIVE** | Stage 3 classifier |
| **`SemanticRouter.kt`** | Section router | **AUTHORITATIVE** | Stage 4 dispatcher |
| **`SpecializedInterpretationStage.kt`**| Ingestion adapter | **TRANSITIONAL** | Stage 5 adapter |
| **`ContextualReconstructionStage.kt`**| Correction wrapper | **AUTHORITATIVE** | Stage 6 corrector |
| **`AggregationStage.kt`** | Map combiner | **AUTHORITATIVE** | Stage 7 aggregator |
| **`ConfidenceCalibrationStage.kt`**| Weight evaluator | **AUTHORITATIVE** | Stage 8 calibrator |
| **`ReplayGenerationStage.kt`** | Trace compiler | **AUTHORITATIVE** | Stage 9 trace generator |
| **`ProductionSanityTest.kt`** | Instrumented Test | **DELETE** | Merged into `ProductionSeparationTest.kt` |

---

## 2. Deletion Validation & Registry

### `ProductionSanityTest.kt`
- **Reference Search Evidence**: Search query `ProductionSanityTest` returned no external calls or references inside production or testing configurations.
- **Replacement Evidence**: `ProductionSeparationTest.kt` fully duplicates compose drawer opening logic and asserts the presence or absence of the `drawer_dev_console` tag based on flavor capabilities.
- **Rollback Assessment**: Negligible risk. Merging the compose node assertion ensures that flavor separation remains 100% verified under `ProductionSeparationTest.kt`.
