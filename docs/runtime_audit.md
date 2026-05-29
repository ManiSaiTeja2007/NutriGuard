# Runtime Audit Document

> [!NOTE]
> **Authority Boundary**: This document answers ONLY:
> * **What actually executes?** (Detailed production, developer, test, benchmark, and future execution flows)
>
> This document does NOT answer:
> * **What architecture exists?** (See [architecture_state.md](file:///d:/projects/Ongoing/nutriguard/docs/architecture_state.md))
> * **What is migrating?** (See [migration_tracker.md](file:///d:/projects/Ongoing/nutriguard/docs/migration_tracker.md))
> * **What has been verified?** (See [verification_status.md](file:///d:/projects/Ongoing/nutriguard/docs/verification_status.md))

This document outlines the actual runtime execution paths across different run configurations (Production, Developer, Tests, Benchmarks) compared to the planned target flow.

---

## 1. Runtime Audit Finding Confidence Matrix

Every finding documented in this audit is classified using the standardized confidence system (VERIFIED, OBSERVED, ESTIMATED, UNKNOWN). Each conclusion is backed by explicit Call Chain evidence:

| Finding | Confidence | File | Method | Call Chain | Evidence Reference & Verification |
| :--- | :---: | :--- | :--- | :--- | :--- |
| **Production UI flow executes PipelineRunner in parallel** | **VERIFIED** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) | `processAndNavigate` | `ScanViewModel.ingestLiveCamera(...)` <br>➔ `processAndNavigate(...)` <br>➔ executes `PipelineRunner.run(...)` in parallel | Code check: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) implements dual execution and compares Result A and Result B. |
| **Production UI flow executes Legacy SemanticPipeline** | **VERIFIED** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) | `processAndNavigate` | `ScanViewModel.ingestLiveCamera(...)` <br>➔ `processAndNavigate(...)` <br>➔ `semanticPipeline.invoke(...)` | Code check: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L64) instantiates and [ScanViewModel.kt:L233](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L233) invokes it. |
| **Execution Graph executes inside instrumented tests** | **VERIFIED** | [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt) | `testPipelineHeadlessExecutionOnLabel000006` | `HeadlessPipelineTest` <br>➔ `PipelineRunner.run(...)` <br>➔ `SemanticExecutionGraph.execute(...)` | Instrumented test runner: Runs the graph end-to-end against real test image assets. |
| **Replay storage captures execution logs on developer builds** | **VERIFIED** | [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) | `processAndNavigate` | `processAndNavigate(...)` <br>➔ `ReplayStorageHelper.saveReplay(...)` | Code check: [ScanViewModel.kt:L364](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L364) triggers JSON logging upon detecting failures under developer configuration settings. |

---

## 2. Primary Execution Flow Diagrams


### CURRENT PRODUCTION FLOW (Live UI Camera Capture)
This path is executed when a consumer scans a packaging label in the live production build:

```text
[Camera Frame]
      │
      ▼
[OcrCameraFrameAnalyzer.kt] (receives frame bitmap)
      │
      ▼
[ScanViewModel.kt] (ingests frame data via coroutine Scope)
      │
      ▼
[OCRPipeline.kt] (runs preprocessor and executes ML Kit TextRecognizer on FULL frame)
      │
      ▼ (raw text output)
[SemanticPipeline.kt] (legacy sequential pipeline)
      │
      ├─► [NormalizationStage] (standardizes delimiters)
      ├─► [ExtractionStage] (tokenizes via comma splits)
      ├─► [GroupingStage] (determines groups)
      ├─► [PhraseCorrectionStage] (joins bigram phrases)
      └─► [CorrectionStage] (evaluates spelling corrections on OcrCorrectionEngine.kt)
      │
      ▼ (corrected list of canonical strings)
[IngredientInterpreter.kt] (evaluated item-by-item in loop)
      │
      ▼
[ResultsScreen.kt] (renders results)
```
- **Entry Point**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) `ingestLiveCamera(...)`
- **Critical Classes**: `OCRPipeline`, `SemanticPipeline`, `PipelineRunner`, `SemanticExecutionGraph`, `IngredientInterpreter`
- **Pipeline Ownership**: UI Layer ➔ Dual Parallel Execution Layer ➔ Comparison Engine
- **Known Bypasses**: None when `useExecutionGraph` is enabled. Runs both legacy and new execution graphs, logs difference metrics, and uses the new graph for screen rendering and database storage.

---

### CURRENT DEVELOPER FLOW (Test Image Ingestion)
This path is executed when a developer ingests a static asset image in `developerDebug` variant:

```text
[Asset Bitmap]
      │
      ▼
[ScanViewModel.kt] (loads asset bitmap via TestLabelAssetRepository)
      │
      ├─► [OCRPipeline.kt] (runs preprocessor and ML Kit on full bitmap) ➔ Legacy Semantic Pipeline
      ▼
[PipelineRunner.kt] (runs graph stages in parallel with legacy pipeline)
      │
      ▼
[ReplayStorageHelper.kt] (saves JSON replay trace if warning/failure detected)
      │
      ▼
[ResultsScreen.kt] (renders results)
```
- **Entry Point**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) `ingestTestImage(...)`
- **Critical Classes**: `OCRPipeline`, `SemanticPipeline`, `PipelineRunner`, `SemanticExecutionGraph`, `ReplayStorageHelper`
- **Pipeline Ownership**: UI Layer ➔ Dual Parallel Execution Layer ➔ Replay Logger
- **Known Bypasses**: None when `useExecutionGraph` is enabled. Runs both legacy and execution graphs, comparing outputs.

---

### CURRENT TEST FLOW (Headless Instrumented Verification)
This path is executed during instrumented test suites on the running emulator:

```text
[Test Bitmap]
      │
      ▼
[HeadlessPipelineTest.kt] (instantiates PipelineRunner)
      │
      ▼
[PipelineRunner.kt]
      │
      ▼
[SemanticExecutionGraph.kt] (executes sequential stages)
      │
      ├─► [StructuralLayoutAnalyzer.kt] (fast downsampled OCR layout zoning)
      ├─► [TargetedOcrCoordinator.kt] (crops bitmap by layout zones; runs OCR only on target regions)
      ├─► [SemanticSectionClassifier.kt] (groups lines into Ingredients, Allergens, or Storage sections)
      ├─► [SemanticRouter.kt] (routes section lines to AllergenInterpreter, NutritionInterpreter, etc.)
      ├─► [SpecializedInterpretationStage.kt] (runs SemanticPipeline on isolated ingredients block)
      ├─► [ContextualReconstructionStage.kt] (boosts confidence for neighbors based on ontology distance)
      ├─► [AggregationStage.kt] (merges interpretations, allergens, and nutrition structures)
      ├─► [ConfidenceCalibrationStage.kt] (runs calibration profile based on contrast/blur)
      └─► [ReplayGenerationStage.kt] (writes execution logs)
      │
      ▼
[HeadlessPipelineTest] (asserts ingredient/allergen lists)
```
- **Entry Point**: [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt)
- **Critical Classes**: `PipelineRunner`, `SemanticExecutionGraph`, `TargetedOcrCoordinator`, `SemanticSectionClassifier`, `SemanticRouter`
- **Pipeline Ownership**: Integration Test Layer ➔ Staged Execution Graph
- **Known Bypasses**: None. Runs the full refactored architecture.

---

### EXPECTED FUTURE FLOW (Target Unified Path)
The unified execution path to be implemented in Stage 13:

```text
[Image Bitmap]
      │
      ▼
[PipelineRunner.kt] (invokes SemanticExecutionGraph)
      │
      ▼
[SemanticExecutionGraph.kt]
      │
      ├─► [StructuralLayoutAnalyzer] (Layout zoning)
      ├─► [TargetedOcrCoordinator] (Targeted crops + OCR)
      ├─► [SemanticSectionClassifier] (Section categorization)
      ├─► [SemanticRouter] (Dispatches sections to sub-interpreters)
      ├─► [SpecializedInterpretationStage] (Fuzzy-corrects ingredients only)
      ├─► [ContextualReconstructionStage] (Applies decay-based context scoring)
      └─► [AggregationStage] (Consolidates ingredients and warnings)
      │
      ▼ (unified PipelineResult)
[ScanViewModel] (caches Snapshot and updates UI State)
      │
      ▼
[ResultsScreen] (renders domain-separated cards)
```
- **Entry Point**: `PipelineRunner.run(...)` invoked directly by `ScanViewModel.kt`.
- **Critical Classes**: `PipelineRunner`, `SemanticExecutionGraph`, `SemanticRouter`.
- **Pipeline Ownership**: Unified Graph Orchestrator.
- **Bypasses**: Removes all legacy linear paths and direct interpreter loops in the View Model.

---

## 3. Compose UI Route Navigation Flow

Navigation is driven by the state-based [NavController](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/navigation/NavController.kt) class tracking compose screens:

```kotlin
class NavController(initialScreen: Screen = Screen.Home) {
    var currentScreen by mutableStateOf(initialScreen)
        private set
    private val backStack = mutableListOf<Screen>()
    fun navigateTo(screen: Screen) {
        backStack.add(currentScreen)
        currentScreen = screen
    }
    fun popBackStack(): Boolean {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }
}
```

### Screen Arguments & Routes Map

| Route / Screen | Arguments | Navigation Execution Flow Role |
| :--- | :--- | :--- |
| `Screen.Home` | None | Primary entry point menu displaying navigation cards. |
| `Screen.Scan` | None | Live CameraX stream frame capture screen. Dispatcher for OCR. |
| `Screen.Results` | `rawOcrText`, `normalizedText`, `extractedTokens`, `canonicalJson`, `latencyJson` | Processing results viewer mapping canonical terms and overlay timing blocks. |
| `Screen.DebugReplay` | None | Playback listing showing local serialized JSON failures. |
| `Screen.ReplayViewer` | `replayId` | Detail rendering analyzer for a single loaded JSON cache replay. |
| `Screen.Settings` | None | developer controls (simulation toggle, timing overlay switch, caching). |

---

## 4. Token Normalization & Parsing Flow

Noisy text strings extracted via OCR are normalized and parsed sequentially in the tokenizer layer:

```text
Raw OCR Text ➔ [TextNormalizer] ➔ lowercase clean text ➔ [IngredientExtractor.extractRawSection] ➔ tokens list 
```

1. **Text Normalization (`TextNormalizer`)**:
   - **Hyphen Recovery**: standardizes word splits across line boundaries by stripping `-\s*[\r\n]+\s*`.
   - **Linebreak Cleanup**: replaces carriage returns, newlines, and tabs with space characters.
   - **Junk Stripping**: cleans punctuation and non-alphanumeric noise characters (`|`, `*`, `•`, `~`).
2. **Section Isolation (`IngredientExtractor.extractRawSection`)**:
   - Matches structural label headers (`"ingredients:"`, `"contains:"`) to isolate the ingredients block.
3. **Delimiter Split & Tokenization**:
   - Splits tokens by commas and semicolons while maintaining parenthetical depth safety (e.g. keeping elements inside `( )` nested together as a single token).
4. **Spacing Recovery Fallback**:
   - If no delimiters exist, tokenizes by spaces, re-joining multi-word vocabulary entries (e.g., `"citric"` + `"acid"` ➔ `"citric acid"`) using 4-word to 2-word sliding match sweeps.

---

## 5. Single-Token Correction Flow (Two-Pass Stage Execution)

Fuzzy correction of OCR tokens executes in two passes across the spelling engine:

```
               PASS 1: Single-Token Processing
 OCR Token ──► [Normalization & Phrase Cleaning]
                    │
                    ▼
               [Deterministic Fast Paths]
               - Direct ontology matches (1.0f)
               - E-Number repairs (0.90f - 1.0f)
               - Positional OCR confusion resolves
                    │
                    ▼
               [Fuzzy Expansion & Candidate Scoring]
               - Levenshtein candidate search
               - Proximity & sequence score boosts
                    │
                    ▼
               [False-Correction Safeguards]
               - Verify minimum base confidence threshold
                    │
                    ▼
               PASS 2: Contextual Disambiguation
               - Neighbor-Aware Window check (±3 tokens)
                    │
                    ▼
               Canonical Spelling Correction
```

---

## 6. Kotlin In-App Runtime Benchmark Flow

Timing metrics are gathered by simulating ingestion loops over assets files:

```text
[Assets Directory (test_labels/)]
      │
      ▼
[Ingestion Loop] (loads bitmaps sequentially from in1.jpg to in182.jpg)
      │
      ▼
[OCRPipeline / SemanticPipeline] (executes processing steps)
      │
      ▼
[Metrics Recording] (calculates OCR time, normalization, extraction, and mapping latencies)
      │
      ▼
[Throughput Timing Calculation] (computes average total latency per frame and logs average tokens)
```
- **Execution Target**: Validates timing constraints and performance stability on Android hardware without loading ground-truth comparison sets.

---

## 7. Production / Test Divergence Audit

This section identifies subsystems that compile and pass tests, but do not execute in the active production environment.

| Subsystem | Exists | Compiles | Unit Tested | Benchmark Runtime | Developer Runtime | Production Runtime | Verified Runtime | Divergence Details |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **PipelineRunner** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **SemanticExecutionGraph** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **StructuralLayoutAnalyzer** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **TargetedOcrCoordinator** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **SemanticSectionClassifier** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **SemanticRouter** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **AllergenInterpreter** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **NutritionInterpreter** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **StorageInstructionInterpreter**| Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **PackagingMetadataInterpreter** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |
| **IngredientInterpreter** | Yes | Yes | Yes | Yes | Yes (Dual) | Yes (Dual) | Yes | **CONVERGENT**: Executes in parallel validation mode under `FeatureFlags.useExecutionGraph`. |

---

## 8. Runtime Convergence Backlog

To execute the transition to Stage 13 systematically, we define the integration boundaries and migration sequences below.

### Runtime Target States
* **Current Runtime Flow**: Live CameraX preview stream and test asset ingestion bypass the staged execution graph. They instantiate and execute the legacy [SemanticPipeline.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/SemanticPipeline.kt) directly on full-image raw OCR strings.
* **Target Runtime Flow**: Live CameraX preview streams run the fully zoned graph via [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt). The flow executes sequentially: `CameraX ➔ OCR ➔ Layout Recovery ➔ Section Detection ➔ Section Classifier ➔ Routing ➔ Interpreters ➔ UI`.

### Identified Migration Blockers
1. **ScanViewModel Ingest Coupling**: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) binds closely to the legacy `SemanticPipeline` and expects plain string array navigation arguments.
2. **ResultsView Card Rendering**: The Compose layout [ResultsScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/results/ResultsScreen.kt) currently only displays a flat list of warning categories and ingredients, lacking distinct containers for nutrition facts, storage warnings, or manufacturer details.
3. **Coordinate Crop Scaling**: Bounding box coordinate shifts under high contrast variations could crop characters at the boundaries, corrupting classifier text.

### Integration Dependencies
* **Required Subsystems**: `StructuralLayoutAnalyzer` (layout zoning), `TargetedOcrCoordinator` (bitmap cropping), `SemanticSectionClassifier` (domain sorting), and `SemanticRouter` (routing to specialized interpreters).

### Migration Sequence Plan
1. **Stage 13.0 — Real Packaging Corpus Analysis** *(COMPLETED)*: Validate corpus JSON structures, failure logs, and taxonomic boundaries.
2. **Stage 13.0A — Runtime Convergence & Pipeline Integration** *(Current)*: Audit and map execution paths, duplicate logic, and integration readiness scorecard.
3. **Stage 13.0B — Packaging Corpus Expansion**: Gather more structured JSON evidence files for all 8 domains and failure cases from real packaging labels.
4. **Stage 13.1 — Section Classification**: Standardize `SemanticSectionClassifier` heuristics and layout recovery to partition visual text blocks.
5. **Stage 13.2 — Domain Routing**: Enable `SemanticRouter` to dispatch segments to specialized interpreters and allergen routers.


