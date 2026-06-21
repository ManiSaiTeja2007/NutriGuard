# Runtime Truth Audit

**Generated:** 2026-05-30  
**Scope:** Verify actual runtime execution path. Document bypasses, duplicate execution, dead paths.

---

## Verified Execution Path (useExecutionGraph = true)

When a scan is triggered from `ScanScreen`, the following path executes:

```
User taps "Ingest Scanned Text" / "Ingest Test Image"
  ↓
ScanViewModel.ingestLiveCamera() or ingestTestImage()
  ↓
  viewModelScope.launch(Dispatchers.Default)
    ↓
    processAndNavigate(context, sourceName, ocrResult, navController)
      ↓
      FeatureFlags.useExecutionGraph == true (HARDCODED)
        ↓
        bitmap = latestBitmap or validationState.asset.bitmap
          ↓
          if bitmap != null:
            PipelineRunner.run(bitmap, rotationDegrees, imageSource, config, context)
              ↓
              PipelineSnapshotRepository.saveTempBitmap(context, bitmap, "raw")
                ↓
              SemanticExecutionGraph graph = NEW GRAPH EVERY SCAN (ISSUE-001)
                ↓
                graph.execute(bitmap, defaultOcrMetadata, executionId)
                  ↓
                  Stage 1: StructuralLayoutAnalyzer.execute(bitmap)
                    ↓
                    OCRComplexityAnalyzer.analyze(bitmap) [downscale to 128px]
                    generateHeatmap(bitmap) [pixel array traversal]
                    Bitmap.createScaledBitmap(input, 400, h, false) [downscale to 400px]
                    fastRecognizer.process(image).await() [ML KIT OCR CALL #1]
                    downsampledBitmap.recycle()
                    → writes detectedZones to context
                  ↓
                  Stage 2: TargetedOcrCoordinator.execute(bitmap)
                    ↓
                    filter zones (exclude IGNORE)
                    for each zone (sorted by priority):
                      croppedBitmap = Bitmap.createBitmap(...)
                      ocrPipeline.runDirectOcr(croppedBitmap) [ML KIT OCR CALL #2..N]
                      croppedBitmap.recycle()
                    deduplicateWords(allWords)
                    → writes targetedOcrBlocks, targetedOcrLines to context
                  ↓
                  Stage 3: SemanticSectionClassifier.execute(Unit, context)
                    → reads targetedOcrLines, classifies into sections
                    → writes classifiedSections to context
                  ↓
                  Stage 4: SemanticRouter.execute(Unit, context)
                    → reads classifiedSections
                    → routes to AllergenInterpreter, NutritionInterpreter, etc.
                    → collects ingredientTextBlocks
                    → writes routingResult to context.metadata
                  ↓
                  Stage 5: SpecializedInterpretationStage.execute(routingResult)
                    ↓
                    semanticPipeline(Pair(ingredientText, ocrMetadata))
                      ↓
                      NormalizationStage → ExtractionStage → GroupingStage
                      → PhraseCorrectionStage → CorrectionStage
                  ↓
                  Stage 6: ContextualReconstructionStage.execute(ingestionResult)
                    → maps correction results to SemanticIngredients
                    → calls IngredientInterpreter.interpret() per ingredient
                  ↓
                  Stage 7: AggregationStage.execute(semanticIngredients)
                  ↓
                  Stage 8: ConfidenceCalibrationStage.execute(aggregated)
                  ↓
                  Stage 9: ReplayGenerationStage.execute(Unit, context)
              ↓
              PipelineSnapshotRepository.saveTempBitmap(context, bitmap, "prep")
              collect metrics from profiler
              PipelineSnapshotRepository.renameTempFiles(context, executionId)
              PipelineSnapshotRepository.update(snapshot)
            ↓
            pipelineResult = result
          ↓
          else: bitmap == null → LOG WARNING, pipelineResult remains null → falls to legacy path
      ↓
      if pipelineResult != null:
        build canonicalJsonB, latenciesJsonB from pipelineResult
        if FeatureFlags.enableReplay && AppSettings.replaySaving && failures.isNotEmpty():
          ReplayStorageHelper.saveReplay(...)
        → return Screen.Results(...)
      ↓
      withContext(Dispatchers.Main):
        navController.navigateTo(Screen.Results(...))
```

---

## Duplicate Execution Paths

### Duplicate 1: `IngredientInterpreter.interpret()` called multiple times per ingredient

**Location:** Legacy path in `ScanViewModel.processAndNavigate()` (lines 410-418, 557-578, 581-593)

In the **legacy path** (when `pipelineResult == null`), `IngredientInterpreter.interpret()` is invoked 3× per ingredient:
1. Line 410: Inside `canonicalJsonA` JSON builder → for each correction result
2. Line 557: Building `semanticIngredients` list → for each correction result
3. Line 586: Building `interpretedIngredients` from `semanticIngredients` → for each semantic ingredient

All three use the same `result.canonical` and similar parameters. This is pure wasted CPU.

**In the execution graph path** (when `pipelineResult != null`): `IngredientInterpreter.interpret()` is called once per ingredient in `ContextualReconstructionStage` (line 36). The JSON builder at lines 274-304 does NOT call `interpret()` again. ✓

**Status:** Duplicate execution in legacy path only. ExecutionGraph path is correct.

---

### Duplicate 2: OCRComplexityAnalyzer.analyze() called twice in execution graph path

**Path 1:** `StructuralLayoutAnalyzer.execute()` line 40: `OCRComplexityAnalyzer.analyze(input)` on the full bitmap  
**Path 2:** This is NOT called in `OCRPipeline.runDirectOcr()` — `runDirectOcr` only calls `runOcrOnBitmap()` which skips complexity analysis. ✓

For the **non-graph path** (live camera via `OcrCameraFrameAnalyzer`):  
`OCRPipeline.invoke()` at line 108 calls `OCRComplexityAnalyzer.analyze(normalizedBitmap)` once. This is the correct single invocation. ✓

**Status:** No duplicate analysis.

---

### Duplicate 3: PipelineSnapshotRepository called twice

**Location:** `PipelineRunner.run()` and `ScanViewModel.processAndNavigate()` legacy path

In the **execution graph path**:
1. `PipelineRunner.run()` calls `saveTempBitmap(context, bitmap, "raw")` at line 31
2. `PipelineRunner.run()` calls `saveTempBitmap(context, bitmap, "prep")` at line 56
3. `PipelineRunner.run()` calls `renameTempFiles()` and `update()` at lines 121-130

In the **legacy path** (ScanViewModel when `pipelineResult == null`):
1. Lines 650-659: `renameTempFiles()` and `PipelineSnapshotRepository.add(snapshot)`

These are separate paths (graph vs legacy) — not duplicates. ✓

However, `OCRPipeline.invoke()` also calls `saveTempBitmap(context, toSave, "prep")` inside its `finally` block (line 218), AND `PipelineRunner.run()` also calls `saveTempBitmap(context, bitmap, "prep")` at line 56. In the execution graph path, the OCR pipeline is invoked via `runDirectOcr()` which does NOT go through `OCRPipeline.invoke()` — so the `finally` block does not run for zone crops. ✓

But for the structural analysis pass in `StructuralLayoutAnalyzer`, the `fastRecognizer` is called directly — not through `OCRPipeline`. So `OCRPipeline`'s `finally` bitmap save is not triggered for structural OCR. ✓

**Status:** No duplicate snapshot writes.

---

## Dead Execution Paths

### Dead Path 1: `AppHealthMonitor.reportError()` — Never Called

`reportError(error, contextInfo)` exists in `AppHealthMonitor` and would trigger the `FallbackRecoveryScreen`. However, no call site exists in the codebase:

```bash
# Search result: zero occurrences of reportError() call sites
grep -r "reportError" src/  → AppHealthMonitor.kt (definition only)
```

The `FallbackRecoveryScreen` in `MainActivity` checks `AppHealthMonitor.hasError`, which can only become `true` via `reportError()`. Therefore the `FallbackRecoveryScreen` is **permanently dead code** in the current runtime.

**Classification:** DEAD. The error recovery system is implemented but completely disconnected.

---

### Dead Path 2: Legacy Pipeline Path — Reachable but Unintended

`ScanViewModel.processAndNavigate()` falls to the legacy `SemanticPipeline` path when:
- `bitmap == null` (latestBitmap not set, or validationState.asset.bitmap not available)

This happens when:
- Live camera ingest before any OCR result has set `latestBitmap`
- Test image ingest before the image has been loaded

The warning log at line 268 indicates this is known: `"useExecutionGraph is true but active bitmap is null. Falling back to legacy path."` The legacy path still produces results from the `ocrResult.text` that was already extracted. But it uses the older, less structured pipeline.

**Classification:** INTENDED FALLBACK. Currently reachable. Not dead code.

---

### Dead Path 3: `Screen.ReplayViewer`, `Screen.DeveloperTools`, `Screen.BenchmarkRunner` in Production `when` Block

In `MainActivity` (lines 151-189), the `when(screen)` blocks for `Screen.DeveloperTools`, `Screen.ReplayViewer`, and `Screen.BenchmarkRunner` contain production-guard logic that renders `HomeScreen` instead. However, `NavController.filterScreen()` already redirects these to `Screen.Home` — so these `when` branches will **never match** in a production build because `currentScreen` is always `Screen.Home` by the time the `when` executes.

The branches only run in developer/benchmark/internal builds where `BuildCapabilities.isProductionBuild == false`.

**Classification:** ALIVE (developer builds). DEAD (production builds) for the production-guard inner `if` branches.

---

### Dead Path 4: `BenchmarkRunnerScreen` Drawer Item

```kotlin
// MainActivity.kt line 265
if (FeatureFlags.enableBenchmarks) {
    DrawerItem("Benchmark Run", ...)
}
```
`FeatureFlags.enableBenchmarks` returns `BuildCapabilities.isBenchmarkBuild`. In the developer build flavor, this is `false`. The Benchmark Runner drawer item is not shown in developer builds.

**Classification:** CONDITIONALLY LIVE (benchmark flavor only). Not dead — just flavor-gated.

---

## Bypass Analysis

### Bypass 1: OCR bypassed when bitmap is null

When `bitmap == null` in `processAndNavigate()`, the execution graph is bypassed and the system falls back to the `OcrResult.text` that was already extracted by the camera frame analyzer. This is a **legitimate bypass** — the OCR already ran earlier, and the result is being reused.

### Bypass 2: TargetedOcrCoordinator skips when no zones

When `sortedZones.isEmpty()` (no HIGH/MEDIUM zones detected), `TargetedOcrCoordinator` returns an empty `OcrResult` immediately without running ML Kit. This propagates up through the graph:
- `SpecializedInterpretationStage` receives empty `ingredientTextBlocks` → returns `null`
- `ContextualReconstructionStage` receives `null` → returns `emptyList()`
- `AggregationStage` processes empty list
- Final `PipelineResult.semanticIngredients` is empty

The `Screen.Results` navigation still occurs with empty JSON `"[]"`. This is a **legitimate bypass** for unlabeled images, but the UX needs an empty state.

### Bypass 3: ReplaySaving bypass when no failures

```kotlin
if (FeatureFlags.enableReplay && AppSettings.replaySaving && failuresListB.isNotEmpty()) {
    ReplayStorageHelper.saveReplay(...)
}
```
Replay is only saved when there are failures. Successful scans do not generate replay files. This is **intentional** but means clean scans are not replayable for regression testing.

---

## Runtime Truth Summary

| Component | Status | Issues |
|-----------|--------|--------|
| CameraX → ScanViewModel | VERIFIED | Frame throttle at 700ms |
| ScanViewModel → PipelineRunner | VERIFIED | Only when bitmap != null |
| PipelineRunner → SemanticExecutionGraph | VERIFIED | New graph per scan (ISSUE-001) |
| SemanticExecutionGraph → StructuralLayoutAnalyzer | VERIFIED | ML Kit OCR call #1 |
| SemanticExecutionGraph → TargetedOcrCoordinator | VERIFIED | ML Kit OCR calls #2..N, serial |
| SemanticExecutionGraph → SemanticRouter | VERIFIED | Reads context, routes sections |
| SemanticRouter → SpecializedInterpretationStage | VERIFIED | Uses existing semanticPipeline |
| SpecializedInterpretationStage → ContextualReconstruction | VERIFIED | Maps corrections to SemanticIngredients |
| ContextualReconstruction → Aggregation → Calibration | VERIFIED | No issues |
| Calibration → ReplayGeneration | VERIFIED | No issues |
| PipelineRunner → UI (via navController) | VERIFIED | Runs on Main dispatcher |
| AppHealthMonitor.reportError | DEAD | Never called anywhere |
| FallbackRecoveryScreen | DEAD | hasError is always false |
| Legacy SemanticPipeline path | FALLBACK | Only when bitmap == null |
| IngredientInterpreter.interpret × 3 | DUPLICATE | In legacy path only |
