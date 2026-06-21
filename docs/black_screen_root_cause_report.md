# Black Screen Root Cause Report

**Generated:** 2026-05-30  
**Scope:** All black-screen and navigation dead-end issues

---

## Black Screen Taxonomy

A "black screen" in this application manifests as one of:
- **Camera overlay black**: CameraX starts but no preview renders (surface not attached)
- **Composable void**: Navigation sets a screen but the composable renders nothing (empty column, loading forever)
- **Stuck state**: A state update never fires, leaving the UI in its previous (possibly empty) state

---

## BLACK-001: Camera Black Screen on Permission Transition
**File:** `ScanScreen.kt` lines 160-230, `MainActivity.kt` lines 68-80  
**Risk Level:** P1

**Root Cause:**  
When the user grants camera permission from the permission request UI, `hasCameraPermission` in `MainActivity` is updated via the `ActivityResultContracts.RequestPermission` result callback. This triggers a recomposition of `ScanScreen`.

In `LiveCameraPanel`, the check at line 160 (`if (!hasCameraPermission)`) returns `false`, so the camera preview block runs. `CameraPreview` uses `AndroidView` with a `factory` lambda that sets up CameraX. The `factory` is only called once per `AndroidView` instantiation — **not** on recomposition.

**Issue:** If `ScanScreen` is recomposed with `hasCameraPermission = true` but `CameraPreview` was previously not composed (the `if (!hasCameraPermission)` branch was showing the permission prompt), `CameraPreview` is created fresh. This is correct — but the `AndroidView` factory's `cameraProviderFuture.addListener` runs asynchronously. There is a **time window** between when the composable first renders (showing a blank `PreviewView`) and when CameraX binds. During this window, the camera preview box shows black.

This is **expected behavior** but is perceived by users as a black screen bug.

**Fix:** Add a loading indicator or placeholder to `CameraPreview` composable while the CameraX provider is initializing:
```kotlin
var isCameraReady by remember { mutableStateOf(false) }
// In the AndroidView factory, set isCameraReady = true after bindToLifecycle
if (!isCameraReady) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }
}
```

---

## BLACK-002: Silent OCR Exception → Frozen Overlay
**File:** `OcrCameraFrameAnalyzer.kt` lines 45-55  
**Risk Level:** P1

**Root Cause:**  
When an exception occurs during OCR processing, `OcrCameraFrameAnalyzer` catches it and calls `OcrInstrumentation.logFailure()`. No state update is sent to `ScanViewModel`. The UI remains showing:
- "Looking for ingredients label..." (if no previous OCR result)
- The last successful OCR text (if a previous result exists)

If this exception is persistent (e.g., ML Kit recognizer returns error repeatedly due to resource exhaustion from the unreleased recognizer allocations in ISSUE-004), the UI appears **permanently frozen** with no indication of the error.

**Evidence:**
```kotlin
// OcrCameraFrameAnalyzer.kt
} catch (error: Throwable) {
    OcrInstrumentation.logFailure(...)
    // No ScanViewModel.setLatestOcr() called
    // No AppHealthMonitor.reportError() called
    // UI receives no update
}
```

**Fix:**  
1. Report persistent OCR failures to `AppHealthMonitor.reportError()` after N consecutive failures.
2. Or add a `onOcrError: (Throwable) -> Unit` callback to `OcrCameraFrameAnalyzer` and display a UI error state.

---

## BLACK-003: Empty Scan State After Navigation
**File:** `ScanViewModel.kt` lines 204-219  
**Risk Level:** P1

**Root Cause:**  
`ingestLiveCamera()` returns early if `ocrResult == null || ocrResult.text.isBlank()`. This is correct guard logic. However, the function is called from a Button `onClick` in `LiveCameraPanel`. The button is `enabled = ocrText.isNotBlank()`. So in theory, the button is only active when OCR text exists.

**Issue:** There is a race condition. The user taps "Ingest Scanned Text" at the exact moment the OCR result is updated to an empty result (frame re-evaluation). `ocrText.isNotBlank()` was true when the button was rendered, enabling it. Then the tap callback fires, but by the time `ingestLiveCamera()` runs, `_uiState.value.latestOcr` has been updated to an empty result. The function returns early. **No navigation occurs. No error is shown.** The user sees no feedback.

Additionally, `ingestLiveCamera()` does not set `isIngesting = true`, so there is no loading indicator while the pipeline processes — unlike `ingestTestImage()` which correctly sets `isIngesting`.

**Fix:**  
1. Add `isIngesting` state management to `ingestLiveCamera()` identical to `ingestTestImage()`.
2. Show a UI error/toast if `ocrResult == null || ocrResult.text.isBlank()` at ingestion time.

---

## BLACK-004: Navigation to Forbidden Screen Shows Nothing
**File:** `NavController.kt`, `MainActivity.kt`  
**Risk Level:** P2

**Root Cause:**  
`NavController.filterScreen()` redirects forbidden screens to `Screen.Home`. When `Screen.Home` is set as `currentScreen`, `MainActivity` renders `HomeScreen`. This is correct.

However, when `navigateTo(Screen.DeveloperTools)` is called in a production build:
1. `filterScreen()` converts `Screen.DeveloperTools` → `Screen.Home`
2. `backStack.add(currentScreen)` pushes the original current screen
3. `currentScreen = Screen.Home`

If the user was already on `Screen.Home` and somehow triggers dev tools navigation (e.g., via deep link or test runner), the back stack now has `Screen.Home` twice. `popBackStack()` will pop to `Screen.Home` from `Screen.Home` — no visual change. User appears stuck.

**Current Status:** Low probability in normal usage. The drawer correctly hides dev items in production builds.

---

## BLACK-005: ResultsScreen Blank if canonicalJson is Invalid
**File:** `ScanViewModel.kt` lines 274-304  
**Risk Level:** P1

**Root Cause:**  
`Screen.Results.canonicalJson` is built from a `JSONArray().toString()`. If `pipelineResult.semanticIngredients` is empty (which happens when `TargetedOcrCoordinator` finds no zones, returns an empty result, and `SpecializedInterpretationStage` gets blank `ingredientText`), `canonicalJson` will be `"[]"`.

`ResultsScreen` must parse this JSON to display results. If `ResultsScreen` does not handle the empty array case (`"[]"`), it may display a blank results list with no indication that no ingredients were found. This is a "soft black screen" — the screen renders but shows nothing useful.

**Evidence (Execution Path for Empty Result):**
1. `StructuralLayoutAnalyzer` runs but finds no semantic zones → adds one full-image fallback zone
2. `TargetedOcrCoordinator` runs OCR on the fallback zone → gets some text
3. `SemanticSectionClassifier` classifies sections → finds no INGREDIENTS section
4. `SemanticRouter` finds no ingredient text blocks
5. Fallback: if `targetedOcrLines.isNotEmpty()` and `!hasKnownSections`, routes all lines to ingredients
6. `SpecializedInterpretationStage` processes the fallback text
7. Result: may produce ingredients or empty list

The black-screen risk is minimal here as the fallback is in place. But the presentation of empty results needs proper UX handling.

**Fix:** `ResultsScreen` should show an explicit "No ingredients detected" state instead of an empty list.

---

## BLACK-006: ScanScreen Shows Camera Permission Request During Recomposition Loop
**File:** `ScanScreen.kt` line 63  
**Risk Level:** P2

**Root Cause:**  
`AppHealthMonitor.trackScreenTransition("Scan")` at line 63 is called in the composable body. This writes to `lastScreenTransition`, a `mutableStateOf`. If `AppHealthMonitor` itself is read in `MainActivity`'s composition (only `hasError` is read there, not `lastScreenTransition`), this does not directly cause a recomposition loop.

However, if any future code reads `AppHealthMonitor.lastScreenTransition` inside the `ScanScreen` or a parent composable, the write in the body → read in parent → recomposition → write again pattern would create an infinite recomposition loop. Currently safe but architecturally dangerous.

**Fix:** Move to `LaunchedEffect(Unit) { AppHealthMonitor.trackScreenTransition("Scan") }`.

---

## Summary

| Issue ID | Type | Manifestation | Risk | Fix |
|----------|------|---------------|------|-----|
| BLACK-001 | Camera initialization gap | Black camera preview | P1 | Add loading indicator |
| BLACK-002 | Silent OCR exception | Frozen UI / no update | P1 | Surface errors to UI |
| BLACK-003 | Race condition on ingest | Silent no-op navigation | P1 | Add isIngesting + feedback |
| BLACK-004 | Forbidden nav backstack | Stuck on Home | P2 | Nav guard improvement |
| BLACK-005 | Empty results rendering | Blank ResultsScreen | P1 | Add empty state to ResultsScreen |
| BLACK-006 | Composition side effect | Potential recomposition loop | P2 | Move to LaunchedEffect |
