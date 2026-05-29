# Pipeline Integration & Runtime Convergence Audit

This document is the authoritative Stage 13.0A engineering audit mapping the runtime pathways of the NutriGuard ingestion pipeline. It details what code executes, what is bypassed, where duplicate parsing resides, and lists the target backlog actions to achieve unified execution convergence.

---

## 1. Authoritative Execution Path Matrix
Every finding in this audit has been traced to active codebase source files and verified without synthetic assumptions.

| Core Subsystem | Exists | Compiles | Unit Tested | Benchmark | Dev UI | Production UI | Audit Finding & Verification Evidence |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **OCRPipeline** | Yes | Yes | Yes | Yes | Yes | Yes | **VERIFIED**: Runs in both production and developer paths.<br>Evidence: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L129) runs `ocrPipeline(Pair(frame, frameResult))` on test assets and frame preview callback calls it. |
| **SemanticPipeline** | Yes | Yes | Yes | Yes | Yes | Yes | **VERIFIED**: Runs in both production and developer paths as the core spell-checker.<br>Evidence: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L233) runs `semanticPipeline(Pair(ocrText, ocrMetadata))`. |
| **PipelineRunner** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Inactive in live scan views. Bypassed by ScanViewModel. Runs only in headless tests.<br>Evidence: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt) does not import or execute `PipelineRunner`. It is executed in [HeadlessPipelineTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/androidTest/java/com/example/verification/HeadlessPipelineTest.kt#L46). |
| **SemanticExecutionGraph** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Inactive in live scan views. Instantiated and run inside PipelineRunner.<br>Evidence: [PipelineRunner.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/PipelineRunner.kt#L38) instantiates and runs the graph. |
| **StructuralLayoutAnalyzer** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Executed only within the staged execution graph during headless tests.<br>Evidence: [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L39) calls `structuralLayoutAnalyzer.execute(...)`. |
| **TargetedOcrCoordinator** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Executed only within the staged execution graph during headless tests.<br>Evidence: [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L55) calls `targetedOcrCoordinator.execute(...)`. |
| **SemanticSectionClassifier** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Executed only within the staged execution graph during headless tests.<br>Evidence: [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L72) calls `semanticSectionClassifier.execute(...)`. |
| **SemanticRouter** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Executed only within the staged execution graph during headless tests.<br>Evidence: [SemanticExecutionGraph.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticExecutionGraph.kt#L78) calls `semanticRouter.execute(...)`. |
| **AllergenInterpreter** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Executed only in graph integration tests; bypassed in production UI.<br>Evidence: [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt#L41) calls `AllergenInterpreter.interpret(...)`. Production UI has no allergen badge display. |
| **NutritionInterpreter** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Inactive in live scan views; called only during routing execution tests.<br>Evidence: [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt#L45) calls `NutritionInterpreter.interpret(...)`. |
| **StorageInstructionInterpreter**| Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Inactive in live scan views.<br>Evidence: [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt#L49) calls `StorageInstructionInterpreter.interpret(...)`. |
| **PackagingMetadataInterpreter** | Yes | Yes | Yes | Yes | No | No | **VERIFIED**: Inactive in live scan views.<br>Evidence: [SemanticRouter.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/core/pipeline/graph/SemanticRouter.kt#L53) calls `PackagingMetadataInterpreter.interpret(...)`. |
| **IngredientInterpreter** | Yes | Yes | Yes | Yes | Yes | Yes | **VERIFIED**: Run in production VM loops to parse ingredients manually.<br>Evidence: [ScanViewModel.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/production/ScanViewModel.kt#L252) calls `IngredientInterpreter.interpret(...)` in a manual processing loop. |

---

## 2. Production / Test Divergence Audit
Exposes systems that compile and test correctly but are completely bypassed under the live production UI.

### Summary of Divergent Subsystems
1. **The Graph Orchestration Bypass**:
   - `PipelineRunner` and `SemanticExecutionGraph` run in Android tests (`HeadlessPipelineTest.kt`) but are completely bypassed in production UI scans (`ScanViewModel.kt`). Live frames run the legacy `SemanticPipeline` line-by-line, causing major divergence in layout analysis, section routing, and allergen segmentation.
2. **Interpreter Domain Divergence**:
   - `AllergenInterpreter`, `NutritionInterpreter`, `StorageInstructionInterpreter`, and `PackagingMetadataInterpreter` exist and are tested, but they never execute in the production UI because the routing layer is bypassed.
3. **Double Ingredient Mapping**:
   - In production, ingredients are corrected in the `SemanticPipeline` and then iterated over in a manual ViewModel loop invoking `IngredientInterpreter.interpret` (lines 252-265 of `ScanViewModel.kt`). In contrast, the test path executes `SpecializedInterpretationStage` and `AggregationStage` where interpretation is performed within the pipeline context, returning structured `PipelineResult` metadata.

### Runtime Environments Metrics Mappings
- **Unit Tested**: Local JVM test suites ([AllergenInterpreterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/intelligence/AllergenInterpreterTest.kt), [SemanticRouterTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticRouterTest.kt), [SemanticSectionClassifierTest.kt](file:///d:/projects/Ongoing/nutriguard/app/src/test/java/com/example/pipeline/graph/SemanticSectionClassifierTest.kt)).
- **Benchmark Runtime**:Simulates test labels 001-182 inside [DebugBenchmarkScreen.kt](file:///d:/projects/Ongoing/nutriguard/app/src/main/java/com/example/ui/features/debug/DebugBenchmarkScreen.kt) to compute latencies.
- **Developer Runtime**: Manual asset selection panel under `developerDebug` variant (`ScanViewModel.kt` test images panel).
- **Production Runtime**: Live CameraX preview feed scan (`ScanViewModel.kt` live scan panel).
- **Verified Runtime**: Connected Android instrumented tests (`HeadlessPipelineTest.kt`).

---

## 3. Duplicate Logic Audit
Identifies overlapping processing stages, normalizations, and routing behaviors.

* **Duplicate Tokenizer / Normalization**:
  - The legacy `SemanticPipeline` performs text normalization via internal pipeline steps. The new `StructuralLayoutAnalyzer` and `SemanticSectionClassifier` also clean texts to match headers.
  - *Recommendation*: **Merge**. In Stage 13.1, extract shared text normalizers into a utility package (`com.example.core.normalization`) and reference them in both layout processing and spelling pipelines.
* **Duplicate Ingestion Mapping Loops**:
  - `ScanViewModel.kt` maps tokens via `IngredientInterpreter` in a manual loop, while the execution graph performs this during `AggregationStage` and `SpecializedInterpretationStage`.
  - *Recommendation*: **Deprecate & Delete**. Retire the manual loop in `ScanViewModel.kt` once `ScanViewModel` is fully wired to consume `PipelineRunner.run(...)` output.
* **Redundant Replay Writing**:
  - `ScanViewModel.kt` checks for `failuresList` manually and writes to `ReplayStorageHelper` directly (line 364), while `ReplayGenerationStage` in the execution graph does the exact same check and writes replays under the staged run.
  - *Recommendation*: **Deprecate**. Retain only the graph-staged `ReplayGenerationStage` execution.

---

## 4. Dead Code Audit
List of compiled code paths and utilities that exist in the codebase but are never invoked by active production features or test runners.

1. **Test-Only Mock Fallbacks**:
   - Some legacy mock fallback methods in `OcrCorrectionEngine` were bypassed once dataset checksum checks were tightened.
   - *Recommendation*: **Keep (No Changes)**. Per Enhancement 6, do not delete or refactor any code during the audit stage. These are flagged for post-audit clean up.
2. **Orphaned Token Splits**:
   - Certain character-split patterns in `IngredientExtractor` split tokens on raw characters without checking parenthetical context. They are bypassed by the new context-aware normalizers.
   - *Recommendation*: **Keep**. Retain for reference until Stage 13.0A is approved.

---

## 5. Audit Safety & Integrity Policy
> [!IMPORTANT]
> **Enhancement 6 Compliance**: No application source code has been deleted, modified, merged, or refactored during this audit. Only documentation files, matrices, and metadata snapshots have been updated. Implementation of convergence decisions will occur strictly in Stage 13.0B.

---

## 6. Top 10 Runtime Convergence Actions
The following actions represent the convergence roadmap for Stage 13.0B, ranked by impact, complexity, risk, and expected accuracy/runtime gains:

1. **Wire ScanViewModel to PipelineRunner**:
   - *Description*: Replace direct instantiation of `SemanticPipeline` and `OCRPipeline` with `PipelineRunner` in `ScanViewModel.kt`.
   - *Impact*: Critical | *Risk*: High | *Complexity*: Medium
   - *Expected Accuracy Gain*: High (unlocks layout and section intelligence)
   - *Expected Runtime Gain*: Moderate (crop optimizations reduce ML Kit workload)
2. **Adopt PipelineResult in ResultsScreen**:
   - *Description*: Update `ResultsScreen.kt` to consume the structured `PipelineResult` (allergens, nutrition, storage, metadata) via the `PipelineSnapshotRepository` instead of flat JSON arguments.
   - *Impact*: Critical | *Risk*: Medium | *Complexity*: Medium
   - *Expected Accuracy Gain*: N/A (UI only)
   - *Expected Runtime Gain*: High (removes expensive JSON serialization between screens)
3. **De-duplicate Ingestion Mapping Loops**:
   - *Description*: Remove the manual loop invoking `IngredientInterpreter.interpret` in `ScanViewModel.kt` and rely on the execution graph's built-in `AggregationStage`.
   - *Impact*: High | *Risk*: Low | *Complexity*: Low
   - *Expected Accuracy Gain*: N/A (consistency mapping)
   - *Expected Runtime Gain*: Low (reduces redundant iterations)
4. **Unify Text Normalization Utilities**:
   - *Description*: Consolidate duplicate normalizers from legacy and graph layers into `com.example.core.normalization.TextNormalizer`.
   - *Impact*: Medium | *Risk*: Medium | *Complexity*: Low
   - *Expected Accuracy Gain*: Moderate (consistent token parsing)
   - *Expected Runtime Gain*: Low
5. **Route Sections in SemanticRouter**:
   - *Description*: Enable routing rules in `SemanticRouter.kt` to dispatch sections to `AllergenInterpreter` and `NutritionInterpreter` instead of ignoring them.
   - *Impact*: High | *Risk*: Low | *Complexity*: Low
   - *Expected Accuracy Gain*: High (correct domain classifications)
   - *Expected Runtime Gain*: N/A
6. **Migrate Replay Logging to ReplayGenerationStage**:
   - *Description*: Remove manual `ReplayStorageHelper` calls in `ScanViewModel.kt` and use the graph's `ReplayGenerationStage` for unified log persistence.
   - *Impact*: Medium | *Risk*: Low | *Complexity*: Low
   - *Expected Accuracy Gain*: N/A (observability only)
   - *Expected Runtime Gain*: Low
7. **Optimize Coordinate Mapping in StructuralLayoutAnalyzer**:
   - *Description*: Ensure camera frame coordinates correctly map to downsampled layout coordinates for targeted crop-scaling.
   - *Impact*: High | *Risk*: High | *Complexity*: High
   - *Expected Accuracy Gain*: High (prevents character truncation)
   - *Expected Runtime Gain*: Moderate
8. **Enforce Hard Confidence Safeguard in Graph Aggregator**:
   - *Description*: Verify that the graph's confidence calibration stage enforces the strict `0.80f` threshold consistently.
   - *Impact*: High | *Risk*: Low | *Complexity*: Low
   - *Expected Accuracy Gain*: High (prevents false corrections)
   - *Expected Runtime Gain*: N/A
9. **Eliminate Orphaned Text Utilities**:
   - *Description*: Safely remove unused split utilities in `IngredientExtractor` after validation passes.
   - *Impact*: Low | *Risk*: Low | *Complexity*: Low
   - *Expected Accuracy Gain*: N/A (refactor clean-up)
   - *Expected Runtime Gain*: N/A
10. **Synchronize Android Instrumented Test Data Ground Truths**:
    - *Description*: Update `HeadlessPipelineTest.kt` assertions to align precisely with the taxonomic categories extracted from real-world labels.
    - *Impact*: Medium | *Risk*: Low | *Complexity*: Low
    - *Expected Accuracy Gain*: N/A (verification health)
    - *Expected Runtime Gain*: N/A
