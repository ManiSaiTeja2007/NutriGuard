# Test Convergence Audit — Stage 13.0D

This document catalogs and audits the test suites in the NutriGuard project to transition towards a single, unified test strategy matching our converged runtime path.

---

## 1. Test Audit Registry

We have categorized every test suite based on its purpose, code coverage, runtime relevance, legacy dependencies, and target state.

| Test File | Directory | Classification | Purpose & Coverage | Legacy Dependency | Target state / Replacement Path |
| :--- | :--- | :---: | :--- | :--- | :--- |
| **`SemanticIntelligenceTest.kt`** | `app/src/test` (JVM) | **KEEP** | Verifies basic ingredient translation, category parsing, and e-numbers logic. | Calls `IngredientInterpreter.interpret` directly. | Keep as low-level business rule unit tests. |
| **`TextIntelligenceTest.kt`** | `app/src/test` (JVM) | **KEEP** | Tests text normalizers and parsing helpers. | Uses `TextNormalizer` and tokenizer helpers. | Keep for low-level normalizer unit test coverage. |
| **`AllergenInterpreterTest.kt`** | `app/src/test` (JVM) | **KEEP** | Asserts allergen matching and warning generations. | Calls `AllergenInterpreter` directly. | Keep to cover allergen classification rules. |
| **`ExecutionGraphReplayTest.kt`**| `app/src/test` (JVM) | **KEEP** | Tests execution trace capturing inside the graph. | Graph classes. | Keep for execution graph replay verification. |
| **`ExecutionProfilerTest.kt`** | `app/src/test` (JVM) | **KEEP** | Tests stage latency profiling and memory tracking. | Profiler classes. | Keep to protect execution diagnostic tooling. |
| **`SemanticRouterTest.kt`** | `app/src/test` (JVM) | **KEEP** | Tests routing logic mapping sections to domains. | `SemanticRouter` | Keep for domain dispatching rules. |
| **`SemanticSectionClassifierTest.kt`** | `app/src/test` (JVM) | **KEEP** | Asserts keyword/weight classifier matches sections. | `SemanticSectionClassifier` | Keep for section heading classification logic. |
| **`AmbiguityRegressionTest.kt`** | `app/src/test` (JVM) | **REWRITE** | Protects against regressions on ambiguous ingredients. | Calls `IngredientInterpreter` directly. | Rewrite in Stage 13.0D.5 to execute via `PipelineRunner` to run in real graph context. |
| **`ContextualReconstructionTest.kt`** | `app/src/test` (JVM) | **REWRITE** | Asserts context correction logic. | Uses `semanticPipeline` directly. | Rewrite in Stage 13.0D.5 to run via `ContextualReconstructionStage`. |
| **`DriftMetricsTest.kt`** | `app/src/test` (JVM) | **REWRITE** | Compares vocabulary prediction drift over dataset. | Runs against `semanticPipeline` and `IngredientInterpreter`. | Rewrite to use `PipelineRunner` for dataset parsing evaluation. |
| **`MultilingualRegressionTest.kt`**| `app/src/test` (JVM) | **REWRITE** | Protects multilingual translation regression. | Calls `IngredientInterpreter`. | Rewrite to call `PipelineRunner` on multilingual text blocks. |
| **`OcrRegressionTest.kt`** | `app/src/test` (JVM) | **KEEP** | Tests character confusion and spacing rules. | Uses `OcrCorrectionEngine` & rules. | Keep as low-level correction engine unit tests. |
| **`ReplayConsistencyTest.kt`** | `app/src/test` (JVM) | **DEPRECATE** | Validates legacy replay log structure. | Legacy replay format. | Deprecate. Replaced by `ExecutionGraphReplayTest`. |
| **`ReplayRegressionTest.kt`** | `app/src/test` (JVM) | **DEPRECATE** | Tests legacy replay trace writing. | Legacy replay format. | Deprecate. Replaced by `ExecutionGraphReplayTest`. |
| **`SafeRejectionTest.kt`** | `app/src/test` (JVM) | **REWRITE** | Asserts non-ingredient text rejection. | Runs against `IngredientInterpreter`. | Rewrite to execute via `PipelineRunner` to verify rejection in graph routing. |
| **`DatasetVerificationTest.kt`** | `app/src/test` (JVM) | **KEEP** | Core dataset sanity verification. | Local corpus file dependencies. | Keep as the primary dataset compliance gate. |
| **`RuntimeExecutionVerificationTest.kt`** | `app/src/androidTest` | **KEEP** | Verifies ViewModel executes all graph stages. | Production `ScanViewModel` flow. | Keep as authoritative runtime wiring gate. |
| **`PipelineIntegrationSmokeTest.kt`** | `app/src/androidTest` | **KEEP** | Verifies full graph executes on test assets. | Full graph integration. | Keep as runtime smoke test gate. |
| **`HeadlessPipelineTest.kt`** | `app/src/androidTest` | **KEEP** | Runs graph end-to-end without UI components. | Headless graph. | Keep to verify standalone graph pipeline. |
| **`PackagingValidationTest.kt`** | `app/src/androidTest` | **KEEP** | Parity and metric verification on full corpus. | Corpus. | Keep as accuracy/precision scorecard validator. |
| **`OcrHardeningTest.kt`** | `app/src/androidTest` | **KEEP** | Verifies tiling, binarization, preprocessors. | Camera/OCR modules. | Keep for image quality and preprocessing verification. |
| **`StageOneFramePipelineTest.kt`**| `app/src/androidTest` | **KEEP** | Tests FramePipeline and image conversions. | FramePipeline | Keep for Frame preview pipeline verification. |
| **`StageTwoOcrPipelineTest.kt`**| `app/src/androidTest` | **KEEP** | Tests OCR text segments reconstruction. | OCR Reconstruction | Keep for OCR reconstruction verification. |

---

## 2. Test Streamlining Rules

1. **Do Not Delete Yet**: No test files classified as `DEPRECATE` or `REWRITE` may be deleted until the **Stage 13.0D.5 Legacy Retirement Gate** is fully passed.
2. **Standardize Target Execution**: All rewritten regression tests must run through `PipelineRunner` using the staging execution graph configuration.
3. **Execution Gating**: Any test failure in `KEEP` or `REWRITE` tests blocks the exit gate.
