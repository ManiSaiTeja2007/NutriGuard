# Unified Runtime Pipeline — Stage 13.0D

This document specifies the single, unified target runtime pipeline for the NutriGuard platform.

---

## 1. Unified Pipeline Flow

```text
       CameraX (Live Preview)
          │
          ▼
    Frame Selection (FramePipeline throttles and analyzes complexity)
          │
          ▼
   Cached Frame Store (ViewModel caches active frame bitmap & rotation)
          │
          ▼  [Ingest Button Clicked]
   StructuralLayoutAnalyzer (Stage 1: Downsamples bitmap & recovers layout zones)
          │
          ▼
   TargetedOcrCoordinator (Stage 2: Crops layout zones, executes ML Kit OCR, translates bounds)
          │
          ▼
   SemanticSectionClassifier (Stage 3: Groups text lines into functional domains)
          │
          ▼
   SemanticRouter (Stage 4: Dispatches section blocks to specialized interpreters)
          │
          ├─► AllergenInterpreter (Allergens domain check)
          ├─► NutritionInterpreter (Nutrition labels parser)
          ├─► StorageInstructionInterpreter (Storage warnings)
          ├─► PackagingMetadataInterpreter (Manufacturer details)
          └─► SpecializedInterpretationStage (Spelling correction on ingredients block)
          │
          ▼
   AggregationStage (Stage 6: Consolidates and structures all interpretations)
          │
          ▼
   ConfidenceCalibrationStage (Stage 7: Calibrates score based on contrast/blur metrics)
          │
          ▼
   ReplayGenerationStage (Stage 8: Compiles JSON execution trace & writes cache log)
          │
          ▼
     PipelineResult (Unified data model carrying results, metrics, and failures)
          │
          ▼
      Results UI (ResultsScreen renders distinct cards for each domain)
```

---

## 2. Component Responsibility & Contracts

### 2.1 Front-end Frame Capture & Store
- **CameraX & FramePipeline**:
  - *Contract*: Ingests raw camera frames, throttles frame rate, and provides `FrameAnalysisResult` containing blur, contrast, brightness, and resolution metrics.
  - *Cached Frame Store*: The `ScanViewModel` caches the latest valid `Bitmap` and rotation degrees.

### 2.2 Execution Graph Orchestration (`PipelineRunner`)
- **SemanticExecutionGraph**:
  - *Contract*: Input is the `Bitmap` and `OcrMetadata` (derived from preprocessing). Output is the `PipelineResult`.
  - *Role*: Sequentially coordinates the execution of layout analysis, targeted OCR, section classification, routing, domain interpretation, confidence calibration, and replay logging.

### 2.3 Domain Routing & Interpretation
- **SemanticRouter**:
  - *Contract*: Grouped text lines categorized by domain. Routes to domain interpreters.
  - *SpecializedInterpretationStage*: Runs spelling engine (`OcrCorrectionEngine`) only on the isolated ingredients text blocks.

### 2.4 Trace Logging & Output Delivery
- **Replay & Aggregation**:
  - *Contract*: Aggregates domain outputs, writes standard JSON execution logs to Cache Storage, and provides unified `PipelineResult` for Jetpack Compose rendering.
