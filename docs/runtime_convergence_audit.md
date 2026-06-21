# Runtime Convergence Audit — Stage 13.0D

This document serves as the Phase 1 Runtime Truth Audit for Stage 13.0D. It catalogs the current execution paths of the NutriGuard platform and classifies each major subsystem on the verification and authority spectrum.

---

## 1. Current Runtime Flows

### 1.1 Current CameraX Flow
- **Path**: CameraX Preview Frame ➔ `OcrCameraFrameAnalyzer` ➔ `FramePipeline` (Throttler/Validator) ➔ `OCRPipeline` (Full Frame OCR) ➔ `onOcrResult` Callback ➔ `ScanViewModel.setLatestOcr(...)`.
- **Evidence**:
  - `CameraPreview.kt` binds analysis lifecycle to `OcrCameraFrameAnalyzer`.
  - `OcrCameraFrameAnalyzer.kt` launches a coroutine to validate the frame and runs the full OCR pipeline.

### 1.2 Current ScanViewModel Flow
- **Path**: Ingestion trigger (either `ingestLiveCamera` or `ingestTestImage`) ➔ Launches coroutine in `Dispatchers.Default` ➔ Invokes `processAndNavigate(...)`.
- **Evidence**: [ScanViewModel.kt:L185](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L185) and [ScanViewModel.kt:L204](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L204).

### 1.3 Current OCR Flow
- **Path**: `OCRPipelineRouter` analyzes bitmap complexity ➔ Routes to an OCR strategy (`STANDARD`, `UPSCALE`, `SHARPENED`, `THRESHOLDED`, `LOW_LIGHT`, `TILED`) ➔ ML Kit `TextRecognizer` executes ➔ `OCRLineReconstructor` builds lines ➔ `IngredientRegionDetector` identifies text block.
- **Evidence**: [OCRPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/OCRPipeline.kt) `invoke(...)` method.

### 1.4 Current Semantic Flow (Dual Execution)
- **Path**:
  1. **Result A (Legacy)**: Runs `SemanticPipeline` (Normalize ➔ Extract ➔ Group ➔ Phrase Correct ➔ spelling Correction via `OcrCorrectionEngine`).
  2. **Result B (Graph)**: If `FeatureFlags.useExecutionGraph` is active, runs `PipelineRunner.run(...)` which instantiates `SemanticExecutionGraph` (Structural Layout ➔ Targeted OCR ➔ Section Classifier ➔ Semantic Router ➔ Domain Interpreters).
  3. **Comparison**: Compiles both results, logs discrepancies to Logcat with tag `NUTRIGUARD_VAL`.
- **Evidence**: [ScanViewModel.kt:L245-L384](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L245-L384).

### 1.5 Current Replay Flow
- **Path**: If failures occur in either Result B (graph) or Result A (legacy), formats trace log (inputs, outputs, latencies, failures) and persists to device cache via `ReplayStorageHelper.saveReplay(...)`.
- **Evidence**: [ScanViewModel.kt:L386-L530](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L386-L530).

### 1.6 Current Navigation Flow
- **Path**: Maps `PipelineResult` (or legacy ingestion results) to `Screen.Results` route arguments (`rawOcrText`, `normalizedText`, `extractedTokens`, `canonicalJson`, `latencyJson`, `executionId`) ➔ Invokes `navController.navigateTo` on Main thread.
- **Evidence**: [ScanViewModel.kt:L678-L831](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L678-L831).

---

## 2. Subsystem Verification & Authority Matrix

| Subsystem | EXISTS | COMPILES | TESTED | INTEGRATED | OBSERVED | AUTHORITATIVE | Evidence Reference |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **PipelineRunner** | Yes | Yes | Yes | Yes | Yes | **No** | [ScanViewModel.kt:L267](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L267) (runs in parallel validation mode, not sole path). |
| **SemanticExecutionGraph** | Yes | Yes | Yes | Yes | Yes | **No** | [PipelineRunner.kt:L52](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt#L52) (co-authority). |
| **SemanticRouter** | Yes | Yes | Yes | Yes | Yes | **No** | [SemanticExecutionGraph.kt:L42](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L42) (runs only in parallel graph path). |
| **Domain Interpreters** | Yes | Yes | Yes | Yes | Yes | **No** | [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt) (nutrition/allergen results run only in graph). |
| **Replay Systems** | Yes | Yes | Yes | Yes | Yes | **Yes** | [ReplayStorageHelper.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/replay/ReplayStorageHelper.kt) (sole serialization handler). |
| **OCR Improvements** | Yes | Yes | Yes | Yes | Yes | **Yes** | [OCRPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/OCRPipeline.kt) (invoked for all camera/test OCR). |
| **Layered OCR Analysis** | Yes | Yes | Yes | Yes | Yes | **No** | [TargetedOcrCoordinator.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/TargetedOcrCoordinator.kt) (runs only within the graph). |
| **Normalization Improvements** | Yes | Yes | Yes | Yes | Yes | **Yes** | [TextNormalizer.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/normalization/TextNormalizer.kt) (used by both legacy and graph paths). |
| **Dataset Provenance** | Yes | Yes | Yes | Yes | Yes | **Yes** | [DatasetVerificationTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/dataset/DatasetVerificationTest.kt) (gating test checks assets). |
| **PSP** | Yes | Yes | Yes | Yes | Yes | **Yes** | `ProjectHealthGenerator.kt` (compiles health metrics). |
