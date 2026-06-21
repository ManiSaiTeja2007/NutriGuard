# Resource Audit

**Generated:** 2026-05-30  
**Scope:** Bitmap lifecycle, OCR engine lifecycle, camera resources, ViewModel lifecycle, dataset caches, replay caches

---

## 1. Bitmap Lifecycle Audit

### 1.1 OCRPipeline — `normalizedBitmap`

**Source:** `ImageFrame.toNormalisedBitmap()` (line 388-398)  
**Lifecycle:**
- For `ImageFrame.BitmapFrame`: returns the original bitmap (no copy, no allocation)
- For `ImageFrame.CameraXFrame`: calls `imageProxy.toBitmapCompat()` → creates new `Bitmap` via JPEG decode

**Release:**
- CameraX path (`isTemporary = true`): recycled at line 230 after copying to `savedBitmap`
- Bitmap path (`isTemporary = false`): NOT recycled — the original caller retains ownership

**Status:** Correctly managed for CameraX frames. Bitmap frames not owned by OCR pipeline (correct).

---

### 1.2 OCRPipeline — `preprocessedBitmap`

**Source:** Allocated in each strategy branch (UPSCALE, SHARPENED, THRESHOLDED, LOW_LIGHT)  
**Lifecycle:** Recycled in `finally` block at line 220.

**Status:** Correctly recycled.

---

### 1.3 OCRPipeline — `savedBitmap` (in OcrResult.frameBitmap)

**Source:** Copied from `normalizedBitmap` at line 224 (CameraX path only)  
**Lifecycle:**
1. Stored in `OcrResult.frameBitmap`
2. `ScanViewModel.setLatestOcr()` stores it in `latestBitmap` field
3. Also stored in `_uiState.latestOcr.frameBitmap`
4. Overwritten on next frame — **previous not recycled** LEAK

**Status:** LEAK. Old `latestBitmap` is overwritten without `recycle()`. Accumulates over scanning session.

**Fix:**
```kotlin
fun setLatestOcr(ocr: OcrResult) {
    _uiState.update { it.copy(latestOcr = ocr) }
    ocr.frameBitmap?.let { newBitmap ->
        latestBitmap?.recycle()
        latestBitmap = newBitmap
    }
}

override fun onCleared() {
    super.onCleared()
    ocrPipeline.close()
    latestBitmap?.recycle()
    latestBitmap = null
}
```

---

### 1.4 StructuralLayoutAnalyzer — `downsampledBitmap`

**Lifecycle:** Recycled in `finally` block at line 58-60: `if (downsampledBitmap != input) downsampledBitmap.recycle()`  
**Status:** Correctly recycled.

---

### 1.5 OCRComplexityAnalyzer — `scaledBitmap`

**Lifecycle:** Recycled at line 37-39: `if (scaledBitmap != bitmap) scaledBitmap.recycle()`  
**Status:** Correctly recycled.

---

### 1.6 TargetedOcrCoordinator — Zone Crop Bitmaps

**Lifecycle:** Recycled in `finally` block at line 89: `croppedBitmap.recycle()`  
**Status:** Correctly recycled.

---

### 1.7 TiledOCRProcessor — Tile Bitmaps

**Lifecycle:** Recycled in `finally` at line 86: `tileBitmap?.recycle()`  
**Status:** Correctly recycled.

---

### 1.8 ScanScreen — TestImagePreview `preprocessedBitmap`

**Source:** `remember(asset.bitmap, filter) { OcrPreprocessor.* }` (lines 538-552)  
**Lifecycle:** When the user switches preprocessing filter, a new bitmap is created. The old one is not recycled — it relies on GC.

**Status:** Potential memory pressure. For large images (multi-megapixel labels), this can cause OOM on low-memory devices. The composable does not implement `RememberObserver` to trigger explicit recycle.

**Fix:** Since Compose `remember` does not offer `onForgotten` for arbitrary objects, a pragmatic fix is to always apply preprocessing on-demand without caching:
```kotlin
val preprocessedBitmap = when (filter) {
    PreprocessingFilter.Raw -> asset.bitmap  // No allocation
    else -> /* compute without remember, accept recompute cost */
}
```
Or wrap in a `RememberObserver` that calls `recycle()` on `onForgotten`.

---

## 2. OCR Engine Lifecycle Audit

### 2.1 OCRPipeline TextRecognizer

**Lifecycle:** Created via `ScanViewModel.ocrPipeline by lazy`. Closed in `ScanViewModel.onCleared()`.  
**Status:** Correctly managed.

---

### 2.2 StructuralLayoutAnalyzer TextRecognizer (`fastRecognizer`)

**Source:** `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` (line 28)  
**Lifecycle:** Created when `StructuralLayoutAnalyzer()` is instantiated. Never closed. A new instance is created per `PipelineRunner.run()` call.

**Status:** LEAK. Every scan creates and abandons one `TextRecognizer`. ML Kit recognizers hold native resources.

**Fix:**
```kotlin
class StructuralLayoutAnalyzer : ExecutionStage<Bitmap, StructuralAnalysisResult>, Closeable {
    private val fastRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    override fun close() {
        fastRecognizer.close()
    }
}
```
`PipelineRunner` must hold the graph as a field and close it in `ScanViewModel.onCleared()`.

---

### 2.3 PipelineRunner — Per-Scan Graph Allocation

**Source:** `PipelineRunner.run()` constructs 9 new stage objects per scan.  
**Status:** Inefficient. `StructuralLayoutAnalyzer.fastRecognizer` is the critical leaking resource. Other stages have no native resources and will be GC'd.

**Fix:** Move graph construction to `PipelineRunner` constructor; hold as field.

---

## 3. Camera Resource Audit

### 3.1 CameraExecutor

**Source:** `Executors.newSingleThreadExecutor()` in `CameraPreview.kt` line 36  
**Lifecycle:** Shut down in `DisposableEffect.onDispose()`.  
**Status:** Correctly managed.

---

### 3.2 ProcessCameraProvider — Not Unbound on Navigation

**Issue:** When `ScanScreen` leaves composition (user navigates to `ResultsScreen`), `CameraPreview` is removed from the tree. The `DisposableEffect.onDispose()` shuts down the executor but does **not call `cameraProvider.unbindAll()`**. The camera device remains active at the OS level until the Activity lifecycle ends.

**Evidence:**
```kotlin
// CameraPreview.kt: DisposableEffect
DisposableEffect(Unit) {
    onDispose {
        cameraExecutor.shutdown()
        // cameraProvider NOT unbound
    }
}
```

**Status:** Resource waste (battery drain, camera hardware lock).

**Fix:**
```kotlin
var cameraProviderRef: ProcessCameraProvider? = null

DisposableEffect(Unit) {
    onDispose {
        cameraProviderRef?.unbindAll()
        cameraExecutor.shutdown()
    }
}
// Store ref when obtained: cameraProviderRef = cameraProvider
```

---

### 3.3 OcrCameraFrameAnalyzer — isOcrRunning Flag

**Status:** Correctly manages concurrent frame analysis. No issues.

---

## 4. ViewModel Lifecycle Audit

### 4.1 ScanViewModel

**onCleared() current state:**
```kotlin
override fun onCleared() {
    super.onCleared()
    ocrPipeline.close()
    // Missing: latestBitmap?.recycle()
    // Missing: pipelineRunner.close()
}
```

**Status:** Incomplete. Missing bitmap recycle and pipelineRunner close.

---

### 4.2 AppSettings.scope

**Status:** Non-cancellable global scope. Acceptable for app-scoped singleton. No crash risk.

---

## 5. Dataset Cache Audit

### 5.1 OntologyRepository

**Cache:** `db` and `aliasMap` as `lazy` vals.  
**Lifecycle:** Process lifetime (singleton).  
**Status:** Acceptable. Singleton appropriate for ontology database.

---

### 5.2 IngredientVocabulary.learnedCache

**Issue:** Each `IngredientVocabulary()` instance has its own `learnedCache`. Since vocabulary is instantiated per OCR call (ISSUE-003), the learned cache is **never persisted** between invocations.

**Status:** Learned vocabulary is silently discarded. Fix by making vocabulary singleton.

---

### 5.3 Replay Cache (ReplayStorageHelper)

**Cache:** Files written to `context.cacheDir` as `{replayId}_replay.json`  
**Issue:** No cache size limit is enforced. Over many scans, cache directory can grow to many MB.

**Status:** No max-size limit. Consider pruning old replay files.

---

## 6. Summary

| Resource | Status | Action |
|----------|--------|--------|
| `latestBitmap` in ScanViewModel | LEAK | Recycle on overwrite + onCleared |
| `preprocessedBitmap` in ScanScreen | Potential pressure | Use RememberObserver or avoid caching |
| `StructuralLayoutAnalyzer.fastRecognizer` | LEAK per scan | Implement Closeable, move to singleton |
| Per-scan graph construction | Wasteful + leak | Move to PipelineRunner constructor |
| CameraX provider unbind | Not unbound on nav | Add unbindAll to DisposableEffect |
| `ocrPipeline` close | OK | No action |
| `cameraExecutor` shutdown | OK | No action |
| OntologyRepository cache | OK | No action |
| `learnedCache` per IngredientVocabulary | Lost per call | Make vocabulary singleton |
| Replay file cache | Unbounded | Add size limit |
| AppSettings.scope | Non-cancellable | Acceptable as app singleton |
