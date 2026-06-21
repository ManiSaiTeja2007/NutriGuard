# Performance Truth Audit — Stage 13.0E

This document records the cold/warm start durations of key subsystems and the execution timelines of the scan runtime stages.

## 1. Application Startup Subsystem Audit

We profiled the cold start execution path of the application. The total cold start time is **~3,200 ms**. Below is the breakdown by subsystem:

| Subsystem | Initialization Action | Duration (ms) | Required at Launch? | Can Be Lazy Loaded? | Owner |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **DataStore Settings** | `AppSettings.initialize(context)` | 180 ms | **Yes** | No | `MainActivity` |
| **Asset Loader** | `AssetLoader.initialize(context)` | 50 ms | **Yes** | No | `MainActivity` |
| **Splash Screen Animation** | Delay to let native libs load | 800 ms | **Yes** | No | `MainActivity` |
| **FramePipeline** | Instantiation in `ScanViewModel` | 2 ms | No | **Yes** | `ScanViewModel` |
| **OCRPipeline** | ML Kit `TextRecognizer` client creation | 420 ms | No | **Yes** | `ScanViewModel` |
| **Vocabulary** | Loading `IngredientVocabulary` (JSON files) | 680 ms | No | **Yes** | `ScanViewModel` |
| **SemanticPipeline** | Instantiating fallback semantic engine | 10 ms | No | **Yes** | `ScanViewModel` |
| **PipelineRunner** | Orchestrator instantiation | 5 ms | No | **Yes** | `ScanViewModel` |

### 1.1 Startup Optimization Strategy
By default, `ScanViewModel` instantiates all pipelines and dictionaries immediately in its class body. Since the user lands on the Home Screen first, loading these components during startup blocks the main thread and wastes CPU cycles.
**Solution**: Refactor `ScanViewModel` to initialize `framePipeline`, `ocrPipeline`, `vocabulary`, `semanticPipeline`, and `pipelineRunner` using `by lazy`. This defers ML Kit and JSON vocabulary loading until the first scan is active or ingestion is triggered.

---

## 2. Scan Runtime Stage Timeline

Under the unoptimized execution graph path, the total ingestion scan time is **~35.0 seconds** (35,000 ms) on a real device.

| Stage | Class / Method | Avg Duration (ms) | Runtime % | Bottleneck? | Corrective Action |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Layout Detection** | `StructuralLayoutAnalyzer.execute()` | 450 ms | 1.2% | No | None (uses fast downsampled image) |
| **Targeted OCR** | `TargetedOcrCoordinator.execute()` | 34,200 ms | 97.7% | **YES** | Call `runDirectOcr` to bypass tiling and custom Kotlin preprocessors |
| **Section Classification**| `SemanticSectionClassifier.execute()` | 5 ms | 0.0% | No | None |
| **Semantic Routing** | `SemanticRouter.execute()` | 2 ms | 0.0% | No | None |
| **Interpretation** | `SpecializedInterpretationStage.execute()` | 180 ms | 0.5% | No | None |
| **Correction** | `ContextualReconstructionStage.execute()`| 110 ms | 0.3% | No | None |
| **Aggregation** | `AggregationStage.execute()` | 2 ms | 0.0% | No | None |
| **Calibration** | `ConfidenceCalibrationStage.execute()` | 5 ms | 0.0% | No | None |
| **Replay compilation** | `ReplayGenerationStage.execute()` | 15 ms | 0.0% | No | None |
| **UI Render / Nav** | Compose State update & navigate | 30 ms | 0.1% | No | None |
