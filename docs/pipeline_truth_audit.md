# Pipeline Truth Audit — Stage 13.0E

This document verifies the runtime pipeline path from raw frame capture to UI rendering and proves that `PipelineRunner` acts as the sole, authoritative ingestion orchestrator.

## 1. End-to-End Runtime Execution Path

The production ingestion pipeline flows sequentially through the following classes:

```text
CameraX Frame / Test Asset
  ↓ (Frame captured)
ScanViewModel
  ↓ (ingestLiveCamera / ingestTestImage)
PipelineRunner.run()
  ↓ (Orchestrates graph execution)
SemanticExecutionGraph.execute()
  ↓ (Executes stages 1 to 9)
Aggregation & Calibration
  ↓ (Outputs PipelineResult)
ScanViewModel
  ↓ (processAndNavigate outputs Screen.Results arguments)
ResultsScreen
  ↓ (Renders clean canonical UI)
```

---

## 2. Execution Stage Verification Registry

We audited every stage of the execution graph to verify its compilation, testing, and runtime invocation:

| Stage | Class Name | Exists | Compiles | Tested | Invoked | Production Invoked | Bypassed? |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **1. Layout Analyzer** | `StructuralLayoutAnalyzer` | Yes | Yes | Yes | Yes | Yes | **No** |
| **2. Zoned OCR** | `TargetedOcrCoordinator` | Yes | Yes | Yes | Yes | Yes | **No** |
| **3. Section Classifier** | `SemanticSectionClassifier`| Yes | Yes | Yes | Yes | Yes | **No** |
| **4. Routing** | `SemanticRouter` | Yes | Yes | Yes | Yes | Yes | **No** |
| **5. Interpretation** | `SpecializedInterpretationStage`| Yes | Yes | Yes | Yes | Yes | **No** |
| **6. Reconstruction** | `ContextualReconstructionStage`| Yes | Yes | Yes | Yes | Yes | **No** |
| **7. Aggregation** | `AggregationStage` | Yes | Yes | Yes | Yes | Yes | **No** |
| **8. Calibration** | `ConfidenceCalibrationStage`| Yes | Yes | Yes | Yes | Yes | **No** |
| **9. Replay trace** | `ReplayGenerationStage` | Yes | Yes | Yes | Yes | Yes | **No** |

### 2.1 Authoritative Status Validation
- **Single Authority**: `PipelineRunner.kt` is confirmed as the sole orchestration entrance. It instantiates `SemanticExecutionGraph` and executes it.
- **Rollback / Fallback Bypasses**: The legacy `SemanticPipeline` fallback branch inside `ScanViewModel.processAndNavigate` (lines 383–669) is **completely bypassed** because `pipelineResult` is successfully returned by `PipelineRunner.run()`. No duplicate execution or background legacy runs are active.
- **Diagnostics Isolation**: The legacy discrepancy comparison loop (`NUTRIGUARD_VAL`) has been removed from production run context, moving all validation to offline tests.
