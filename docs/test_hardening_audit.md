# Test Hardening Audit — Stage 13.0E

This document records the audit of the test suites to transition towards a lean, non-redundant authoritative set of verification gates.

## 1. Test Registry Audit

| Test Class | Source Set | Coverage Area | Unique Value | Classification | Action / Target State |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **`SemanticIntelligenceTest`** | JVM (`app/src/test`) | Ingredient category rules | Base classification checks | **KEEP** | Standard business logic check |
| **`TextIntelligenceTest`** | JVM | Sanitizers and text cleaning | Token-level normalizations | **KEEP** | Lower-level text helpers |
| **`AllergenInterpreterTest`** | JVM | Allergen warnings dispatch | Warning string parsing | **KEEP** | Core allergen interpreter check |
| **`ExecutionGraphReplayTest`** | JVM | Replay trace compilation | Graph serialization rules | **KEEP** | Graph replay verification |
| **`ExecutionProfilerTest`** | JVM | Stage latency measurements | Profiler calculations | **KEEP** | Profiler timing gate |
| **`SemanticRouterTest`** | JVM | Segment-to-domain matching | Router rule triggers | **KEEP** | Dispatches to interpreters |
| **`SemanticSectionClassifierTest`**| JVM | Section headers labeling | Section tags mapping | **KEEP** | Headings parser |
| **`DriftMetricsTest`** | JVM | Vocabulary and dataset regression | Whole dataset validation | **KEEP** | Regression safety check |
| **`UiAppearanceLintTest`** | JVM | Static layout rules | Lint check | **KEEP** | Design system layout |
| **`DatasetVerificationTest`** | JVM | Corpus compliance | Checks corpus schemas | **KEEP** | Dataset sanity checks |
| **`RuntimeExecutionVerificationTest`**| Android (`androidTest`) | ViewModel routing paths | Ingests real view models | **KEEP** | Authoritative integration gate |
| **`PipelineIntegrationSmokeTest`** | Android | Staged execution graph | Standalone smoke run | **KEEP** | Standalone graph integration gate |
| **`HeadlessPipelineTest`** | Android | Standalone graph | Standalone execution | **KEEP** | Standalone pipeline verification |
| **`PackagingValidationTest`** | Android | Parity scorecards | Real device benchmark runs | **KEEP** | Accuracy benchmark score |
| **`OcrHardeningTest`** | Android | Image preprocessing binarizers | Image quality checks | **KEEP** | Quality verification |
| **`StageOneFramePipelineTest`** | Android | Frame preview pipeline | Live frame previews | **KEEP** | Preview analyzer tests |
| **`StageTwoOcrPipelineTest`** | Android | OCR text line recovery | Word reconstruction | **KEEP** | Line builder tests |
| **`ProductionSanityTest`** | Android | Menu drawer buttons | Duplicate checks | **DELETE** | Merged into `ProductionSeparationTest` |
| **`ProductionSeparationTest`** | Android | Menu drawer build gating | Build separation | **KEEP** | Gated sidebar verification |

---

## 2. Test Deletion Impact Assessment

### `ProductionSanityTest`
- **Duplicate Coverage**: Both `ProductionSanityTest` and `ProductionSeparationTest` assert the existence of the `drawer_dev_console` tag inside the sidebar menu by performing compose rule UI lookups.
- **Simplification Win**: Merging the minor checks decreases test counts and compile dependencies without compromising separation gates.
