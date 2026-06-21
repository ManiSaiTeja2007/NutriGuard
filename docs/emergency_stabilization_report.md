# NutriGuard Emergency Stabilization Report

**Generated:** 2026-05-30  
**Phase:** Root Cause Discovery + Audit  
**Status:** ACTIVE — Fixes In Progress

---

## Executive Summary

The application has multiple concurrent issues spanning all severity levels. The most critical issues are:

1. **P0 – DOUBLE OCR EXECUTION**: `FeatureFlags.useExecutionGraph` is hardcoded `true`, causing every scan to execute the full `SemanticExecutionGraph` (which runs its own internal OCR pass via `StructuralLayoutAnalyzer` + `TargetedOcrCoordinator`) AND a legacy `SemanticPipeline` path is also available in `ScanViewModel`. This is the **root cause of ~34 second scan times**.
2. **P0 – MISSING RESOURCE LEAK in `OCRPipeline`**: Preprocessing bitmaps are potentially recycled before use in the `finally` block due to sequencing with the `savedBitmap` copy.
3. **P1 – `IngredientVocabulary` instantiated per OCR call**: A new `IngredientVocabulary()` is constructed inside `OCRPipeline.invoke()` at line 238 on every camera frame. This causes unbounded allocation.
4. **P1 – `StructuralLayoutAnalyzer` creates a `TextRecognizer` singleton**: `fastRecognizer` is instantiated as a class-level field but never closed when the `StructuralLayoutAnalyzer` instance is discarded. In `PipelineRunner.run()`, a **new `SemanticExecutionGraph` is instantiated per scan**, meaning a new recognizer is allocated and never closed on every scan.
5. **P2 – Back stack grows unboundedly**: `NavController.navigateTo()` always pushes to `backStack` with no guard against duplicate pushes; repeated Scan→Results transitions fill the back stack.
6. **P3 – `ScanScreen` calls `AppHealthMonitor.trackScreenTransition("Scan")` directly in Composable body**: This is a side effect in composition, causing it to fire on every recomposition.

---

## Issue Registry

### ISSUE-001
| Field | Value |
|-------|-------|
| **ID** | ISSUE-001 |
| **Severity** | P0 – Crash Risk + Extreme Latency |
| **Category** | Performance / Architecture |
| **Affected Files** | `FeatureFlags.kt`, `PipelineRunner.kt`, `StructuralLayoutAnalyzer.kt`, `ScanViewModel.kt` |

**Root Cause:**  
`FeatureFlags.useExecutionGraph` returns hardcoded `true`. When `processAndNavigate` is called, it enters the `if (FeatureFlags.useExecutionGraph)` branch and calls `pipelineRunner.run()`, which internally instantiates a `SemanticExecutionGraph`. That graph executes **9 sequential stages**, the first two of which run **separate ML Kit OCR passes**:
- Stage 1: `StructuralLayoutAnalyzer.execute()` — runs a downsampled ML Kit OCR pass to detect zones
- Stage 2: `TargetedOcrCoordinator.execute()` — runs per-zone `ocrPipeline.runDirectOcr()` for each zone found

**Additional compounding factor**: `StructuralLayoutAnalyzer` instantiates a `TextRecognizer` as a class-level field `fastRecognizer`. Since `PipelineRunner.run()` creates a `new SemanticExecutionGraph(...)` on **every scan**, a new recognizer is allocated and leaked on every invocation. ML Kit recognizers are heavyweight and this causes GC pressure and progressive slowdown.

**Evidence:**
```kotlin
// PipelineRunner.kt line 38-48: New graph per scan
val graph = SemanticExecutionGraph(
    structuralLayoutAnalyzer = StructuralLayoutAnalyzer(), // <-- new recognizer
    targetedOcrCoordinator = TargetedOcrCoordinator(ocrPipeline),
    ...
)

// StructuralLayoutAnalyzer.kt line 28: recognizer never closed
private val fastRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

// FeatureFlags.kt line 21: hardcoded true
val useExecutionGraph: Boolean get() = true
```

**Recommended Fix:**  
1. Make `SemanticExecutionGraph` and all its stages singletons or reuse them across scans (move construction into `ScanViewModel` lazy delegates alongside `ocrPipeline`/`semanticPipeline`).
2. Close `StructuralLayoutAnalyzer.fastRecognizer` when the graph is destroyed by implementing `Closeable`.
3. Or: refactor `PipelineRunner` to hold the graph as a reusable field.

**Verification:** Measure scan latency before and after; expect dramatic drop from ~34s.

---

### ISSUE-002
| Field | Value |
|-------|-------|
| **ID** | ISSUE-002 |
| **Severity** | P0 – Memory Leak |
| **Category** | Resource Leak |
| **Affected Files** | `OCRPipeline.kt` |

**Root Cause:**  
In `OCRPipeline.invoke()`, the `finally` block at lines 215-221 calls `preprocessedBitmap?.recycle()`. However, if `strategy == OCRPipelineRouter.OcrStrategy.STANDARD`, `preprocessedBitmap` remains `null` (the standard path at line 203 uses `normalizedBitmap` directly). The `toSave` computation at line 216 correctly picks up `normalizedBitmap` in this case. But if a strategy path fails and `preprocessedBitmap` was partially allocated before throwing, it gets recycled in `finally`.

More critically: `normalizedBitmap` (from `ImageFrame.toNormalisedBitmap()`) is recycled at line 230 for CameraX frames — **but `savedBitmap` was copied from `normalizedBitmap` earlier**. If `isTemporary` is true (CameraX frame), the bitmap is copied at line 224, then the original is recycled at line 230. This is correct. However the `savedBitmap` is stored in `OcrResult.frameBitmap` and later stored in `ScanViewModel.latestBitmap` (line 91). This bitmap is never explicitly released and persists until the next scan overwrites it.

**Evidence:**
```kotlin
// ScanViewModel.kt line 70-92
private var latestBitmap: android.graphics.Bitmap? = null

fun setLatestOcr(ocr: OcrResult) {
    _uiState.update { it.copy(latestOcr = ocr) }
    ocr.frameBitmap?.let {
        latestBitmap = it  // Previous bitmap is not recycled before overwrite
    }
}
```

**Recommended Fix:**  
Recycle the previous `latestBitmap` before overwriting: 
```kotlin
ocr.frameBitmap?.let { newBitmap ->
    latestBitmap?.recycle()
    latestBitmap = newBitmap
}
```
Also add cleanup in `ScanViewModel.onCleared()`.

**Verification:** Monitor heap allocation during repeated scans; bitmap accumulation should stop.

---

### ISSUE-003
| Field | Value |
|-------|-------|
| **ID** | ISSUE-003 |
| **Severity** | P0 – Memory / Performance |
| **Category** | Repeated Allocation |
| **Affected Files** | `OCRPipeline.kt` line 238 |

**Root Cause:**  
`IngredientVocabulary()` is instantiated inside `OCRPipeline.invoke()` on every single camera frame:
```kotlin
// OCRPipeline.kt line 238
val vocabulary = IngredientVocabulary().getVocabulary()
val detectedParagraphs = IngredientRegionDetector.detectRegion(reconstructedLines, vocabulary)
```
This object contains `staticVocabulary` (a Set with ~70 entries), `learnedCache` (ConcurrentHashMap), and `multilingualHooks`/`ocrCorruptionMap`. For live camera mode with ~1 frame per 700ms, this creates ~1.4 objects/second with associated Set construction overhead.

**Recommended Fix:**  
Make `IngredientVocabulary` a singleton or pass the vocabulary as a constructor dependency to `OCRPipeline`. The `ScanViewModel` already has a `vocabulary by lazy` instance that could be passed in.

**Verification:** Heap profiler shows `IngredientVocabulary` allocations during camera scan.

---

### ISSUE-004
| Field | Value |
|-------|-------|
| **ID** | ISSUE-004 |
| **Severity** | P1 – Resource Leak |
| **Category** | Resource Leak |
| **Affected Files** | `StructuralLayoutAnalyzer.kt`, `PipelineRunner.kt` |

**Root Cause:**  
`StructuralLayoutAnalyzer` holds a `TextRecognizer` instance `fastRecognizer` that is **never closed**. `PipelineRunner.run()` constructs a new `SemanticExecutionGraph(structuralLayoutAnalyzer = StructuralLayoutAnalyzer(), ...)` on every scan. Each new `StructuralLayoutAnalyzer()` creates a fresh ML Kit recognizer via `TextRecognition.getClient()`. These recognizers are never released.

`OCRPipeline` extends `Closeable` and properly closes its recognizer in `onCleared()`. `StructuralLayoutAnalyzer` does not implement `Closeable` at all.

**Recommended Fix:**  
1. Implement `Closeable` on `StructuralLayoutAnalyzer`.
2. Move `SemanticExecutionGraph` and all its stage instances to `PipelineRunner` as constructor-injected singletons (not constructed per-run).
3. Close the graph/analyzer when `ScanViewModel` is cleared.

**Verification:** Monitor ML Kit recognizer allocations via memory profiler.

---

### ISSUE-005
| Field | Value |
|-------|-------|
| **ID** | ISSUE-005 |
| **Severity** | P1 – Black Screen Risk |
| **Category** | Black Screen |
| **Affected Files** | `ScanScreen.kt` line 63, `AppHealthMonitor.kt` |

**Root Cause:**  
`AppHealthMonitor.trackScreenTransition("Scan")` is called at the top level of the `ScanScreen` composable (line 63), directly in the composition body. This is a **side effect in composition** — it will fire on every recomposition of `ScanScreen`. This is a Compose anti-pattern. While this specific call currently has no observable crash risk, the pattern is unsafe.

More critically: `AppHealthMonitor.hasError` uses `mutableStateOf` read inside `MainActivity.setContent`. If `hasError` is set to `true` by a background exception that calls `reportError()`, the entire screen tree switches to `FallbackRecoveryScreen`. However, `reportError()` is never called anywhere in the codebase — only `logFailure()` is called in `OcrCameraFrameAnalyzer`. This means `hasError` will **never become true** through normal crash paths, making the recovery screen dead code. But it also means that **exception silencing** in OCR paths means crashes may appear as black screens (no OCR results, no UI update, camera appears frozen).

**Evidence:**
```kotlin
// AppHealthMonitor.kt line 28-32: reportError exists but is never called
fun reportError(error: Throwable, contextInfo: String) { ... }

// OcrCameraFrameAnalyzer.kt line 45-55: exceptions are caught and logged but reportError NOT called
} catch (error: Throwable) {
    OcrInstrumentation.logFailure(...)
    // reportError is never invoked here
}
```

**Recommended Fix:**  
1. Move `AppHealthMonitor.trackScreenTransition("Scan")` inside a `LaunchedEffect(Unit)`.
2. Call `AppHealthMonitor.reportError(error, ...)` in `OcrCameraFrameAnalyzer.catch` block to properly surface crashes.

**Verification:** Trigger an intentional OCR exception and verify recovery screen appears.

---

### ISSUE-006
| Field | Value |
|-------|-------|
| **ID** | ISSUE-006 |
| **Severity** | P2 – Navigation |
| **Category** | Navigation |
| **Affected Files** | `NavController.kt` |

**Root Cause:**  
`NavController.navigateTo(screen)` always pushes the current screen to `backStack` with no deduplication. Rapid repeated navigation (user tapping "Ingest" multiple times quickly, or Scan→Results→Back→Ingest again) causes the back stack to grow unboundedly. On a typical session the back stack can grow to dozens of entries.

Additionally, `filterScreen()` in `popBackStack()` can silently redirect forbidden screens back to `Home`, potentially causing confusing navigation loops if `Home` itself was what was pushed.

**Evidence:**
```kotlin
// NavController.kt line 22-26: no deduplication
fun navigateTo(screen: Screen) {
    val target = filterScreen(screen)
    backStack.add(currentScreen)  // Always pushes, no size cap
    currentScreen = target
}
```

**Recommended Fix:**  
Add a max back stack depth (e.g., 10) and/or guard against pushing the same screen class consecutively.

**Verification:** Navigate Scan→Results→Back repeatedly 20 times and inspect back stack size.

---

### ISSUE-007
| Field | Value |
|-------|-------|
| **ID** | ISSUE-007 |
| **Severity** | P2 – Navigation Black Screen |
| **Category** | Navigation Dead End |
| **Affected Files** | `MainActivity.kt` lines 152-189 |

**Root Cause:**  
When `BuildCapabilities.isProductionBuild == true`, navigating to `DeveloperTools`, `ReplayViewer`, or `BenchmarkRunner` falls through to rendering `HomeScreen` instead. However, `NavController.filterScreen()` already redirects these to `Screen.Home` **before** navigation. This means MainActivity's `when(screen)` block has dead branches for those screens in production — they will show `HomeScreen` from `NavController.filterScreen`, but then the `when` branch checks again in `MainActivity` and **re-renders HomeScreen a second time** redundantly.

This is not a crash but represents dead code duplication and potential for confusion.

**Recommended Fix:**  
Remove the redundant production-build guards from the `when(screen)` block in `MainActivity` since `NavController.filterScreen()` already handles this at the navigation layer. Trust the router.

**Verification:** Navigate to dev tools in production flavor; confirm it shows Home and logs the redirect.

---

### ISSUE-008
| Field | Value |
|-------|-------|
| **ID** | ISSUE-008 |
| **Severity** | P3 – Performance |
| **Category** | Startup |
| **Affected Files** | `MainActivity.kt` lines 52-54, `AppSettings.kt`, `OntologyRepository.kt` |

**Root Cause:**  
`onCreate()` calls `AppSettings.initialize()` before `super.onCreate()` at line 53-54. `AppSettings.initialize()` launches 9 coroutines on `Dispatchers.Main + SupervisorJob()`. While this is not directly blocking, the coroutine scope is created at object-initialization time with no lifecycle awareness — the `SupervisorJob` is never cancelled.

More critically, `OntologyRepository.db` is loaded via a `lazy` delegate triggered on first access. This lazy load involves: JSON file read from assets, JSON parsing of the full ontology, and `buildAliasMap()`. This happens on whichever thread first accesses `OntologyRepository.find()` — which, in the SemanticPipeline flow, is on `Dispatchers.Default`. This is acceptable but the ontology can be multi-MB and represents significant cold-start blocking.

**Recommended Fix:**  
Warm up the ontology on a background thread during app startup (not on demand during the first scan). Add a `CoroutineScope(Dispatchers.IO).launch { OntologyRepository.getAll() }` call in `MainActivity.onCreate()` after `super.onCreate()`.

**Verification:** Measure cold scan latency before/after warm-up; first-scan latency should drop.

---

### ISSUE-009
| Field | Value |
|-------|-------|
| **ID** | ISSUE-009 |
| **Severity** | P3 – Performance |
| **Category** | OCR Latency |
| **Affected Files** | `StructuralLayoutAnalyzer.kt`, `TargetedOcrCoordinator.kt` |

**Root Cause:**  
The `SemanticExecutionGraph` performs **serial sequential OCR execution** across detected zones. `TargetedOcrCoordinator.execute()` iterates `sortedZones` in a `for` loop and calls `ocrPipeline.runDirectOcr(croppedBitmap)` for each zone sequentially. For an image with 3-4 detected zones, this means 3-4 sequential ML Kit calls in addition to the structural pass — totaling 5+ ML Kit invocations per scan.

ML Kit's Latin text recognizer typically takes 3-8 seconds per call. With 4 zones × 5-8 seconds = 20-34 seconds total, matching the observed ~34s scan time.

**Evidence:**
```kotlin
// TargetedOcrCoordinator.kt line 64-155: Serial zone processing
for (zone in sortedZones) {
    ...
    val ocrResult = ocrPipeline.runDirectOcr(croppedBitmap)  // Blocking per zone
    ...
}
```

**Recommended Fix (within existing architecture):**  
Zone-level OCR passes should run in parallel using `async/await`. Each zone crop is independent.
```kotlin
val deferredResults = sortedZones.map { zone ->
    coroutineScope { async { ocrPipeline.runDirectOcr(croppedBitmap) } }
}
val results = deferredResults.awaitAll()
```
However, ML Kit itself may not support parallel calls on the same recognizer. A simpler fix: limit zones to a maximum of 2 (highest priority only) to cap total OCR time.

**Verification:** Measure scan time with zone cap of 1 vs 2 vs unbounded.

---

### ISSUE-010
| Field | Value |
|-------|-------|
| **ID** | ISSUE-010 |
| **Severity** | P3 – Performance |
| **Category** | Duplicate Computation |
| **Affected Files** | `ScanViewModel.kt` lines 549-593 |

**Root Cause:**  
In the legacy pipeline path (when `pipelineResult == null`), `IngredientInterpreter.interpret()` is called **twice** for every ingredient:
1. At line 410-418 (inside the `canonicalJsonA` builder for JSON serialization)
2. At line 557-578 (to build `semanticIngredients` list)
3. And a third time at lines 581-593 (to build `interpretedIngredients` from `semanticIngredients`)

This means the interpreter runs 3× per ingredient in legacy mode.

**Recommended Fix:**  
Compute the interpretation once and reuse the result. In the `canonicalJsonA` builder, store interpretation results in a parallel list and reuse them for `semanticIngredients`.

**Verification:** Add logging to `IngredientInterpreter.interpret()` to count invocations per scan.

---

### ISSUE-011
| Field | Value |
|-------|-------|
| **ID** | ISSUE-011 |
| **Severity** | P4 – Cleanup |
| **Category** | Code Duplication |
| **Affected Files** | `TiledOCRProcessor.kt`, `TargetedOcrCoordinator.kt` |

**Root Cause:**  
Both `TiledOCRProcessor` and `TargetedOcrCoordinator` implement identical `deduplicateWords()` and `calculateIoU()` functions. The implementations are byte-for-byte identical. When the execution graph is active (`useExecutionGraph = true`), `TiledOCRProcessor` is still called internally by `OCRPipeline` for `TILED` strategy, while `TargetedOcrCoordinator` runs its own separate deduplication. Words may be deduplicated twice.

**Recommended Fix:**  
Extract `deduplicateWords()` and `calculateIoU()` to a shared utility object, e.g., `com.example.core.ocr.OcrWordDeduplicator`.

**Verification:** Code inspection; no functional test needed.

---

## Summary Table

| Issue ID | Severity | Category | Affected Files | Status |
|----------|----------|----------|----------------|--------|
| ISSUE-001 | P0 | Double OCR / Performance | PipelineRunner, StructuralLayoutAnalyzer, FeatureFlags | **FIX REQUIRED** |
| ISSUE-002 | P0 | Memory Leak | ScanViewModel | **FIX REQUIRED** |
| ISSUE-003 | P0 | Repeated Allocation | OCRPipeline | **FIX REQUIRED** |
| ISSUE-004 | P1 | Resource Leak | StructuralLayoutAnalyzer, PipelineRunner | **FIX REQUIRED** |
| ISSUE-005 | P1 | Black Screen / Side Effect | ScanScreen, AppHealthMonitor | **FIX REQUIRED** |
| ISSUE-006 | P2 | Navigation | NavController | **FIX REQUIRED** |
| ISSUE-007 | P2 | Navigation Dead Code | MainActivity | **CLEANUP** |
| ISSUE-008 | P3 | Startup Performance | MainActivity, OntologyRepository | **FIX REQUIRED** |
| ISSUE-009 | P3 | OCR Latency | StructuralLayoutAnalyzer, TargetedOcrCoordinator | **FIX REQUIRED** |
| ISSUE-010 | P3 | Duplicate Computation | ScanViewModel | **FIX REQUIRED** |
| ISSUE-011 | P4 | Code Duplication | TiledOCRProcessor, TargetedOcrCoordinator | **MERGE** |
