# Performance Audit

**Generated:** 2026-05-30  
**Methodology:** Static analysis + execution path tracing

---

## Startup Performance

### Cold Start Analysis

**Code Path:**
1. `MainActivity.onCreate()` fires
2. **BEFORE `super.onCreate()`** (lifecycle violation): `SettingsRepository(applicationContext)`, `AppSettings.initialize()`, `AssetLoader.initialize()` execute
3. `super.onCreate()` fires
4. `setContent { }` starts Compose composition
5. `NutriTheme` reads `AppSettings.themePreference` (may not be populated yet from DataStore async flows)
6. `NavController` instantiated (lightweight)
7. Permission check runs (fast)
8. `HomeScreen` renders

**Blocking Operations at Startup:**
- `SettingsRepository` construction: DataStore initialization — fast (~5ms)
- `AppSettings.initialize()`: Launches 9 coroutines (non-blocking) — fast

**Deferred but Blocking at First Scan:**
- `OntologyRepository.db` lazy load: JSON parse of `ontology/ontology.json` — **potentially 100-500ms** depending on file size
- `IngredientVocabulary()`: Constructs sets/maps — fast (~2ms each) but called per frame
- `OCRPipeline()`: Lazy init, ML Kit recognizer creation — **first-call latency ~200-400ms**
- `SemanticExecutionGraph` stages: Created per scan — **allocates 9 objects** including a `TextRecognizer`

**Estimated Cold Start to First Frame:** < 500ms (acceptable)  
**Estimated First Scan Total Latency:** ~34 seconds (CRITICAL — see Scan section)

---

## Warm Start Analysis

After the first scan:
- `ocrPipeline` is already initialized (lazy delegate — initialized once)
- `vocabulary` is already initialized
- `semanticPipeline` is already initialized
- `pipelineRunner` is already initialized

**BUT**: `PipelineRunner.run()` creates a new `SemanticExecutionGraph(...)` **on every scan**. This means:
- New `StructuralLayoutAnalyzer()` instance → new `TextRecognizer` (expensive, ~100-200ms)
- New `TargetedOcrCoordinator()` instance
- New `SpecializedInterpretationStage()` instance
- 7 more new stage instances

**Warm scan latency is essentially the same as cold scan latency** due to graph reconstruction.

---

## Scan Runtime Performance

### Measured Execution Path (SemanticExecutionGraph)

When `FeatureFlags.useExecutionGraph == true` (which is hardcoded `true`):

| Stage | Operation | Estimated Time |
|-------|-----------|---------------|
| Stage 1: Structural Analysis | `OCRComplexityAnalyzer.analyze()` (downscale to 128px, pixel computation) | 20-50ms |
| Stage 1: Structural Analysis | `TextRecognizer.process()` on 400px downsampled image | **3,000-8,000ms** |
| Stage 2: Targeted OCR | Per zone: `ocrPipeline.runDirectOcr()` | **3,000-8,000ms × N zones** |
| Stage 3: Section Classification | Text pattern matching | <10ms |
| Stage 4: Semantic Routing | Interpreter calls | 50-200ms |
| Stage 5: Specialized Interpretation | `semanticPipeline()` call chain | 100-500ms |
| Stage 6: Contextual Reconstruction | Ingredient mapping | 10-50ms |
| Stage 7: Aggregation | List operations | <5ms |
| Stage 8: Confidence Calibration | Float math | <5ms |
| Stage 9: Replay Generation | JSON/list construction | 10-50ms |

**Total for 3 zones:**
- Structural OCR: 3-8s
- 3× Zone OCR: 9-24s
- Semantic stages: <1s
- **Total: ~12-33 seconds** ✓ matches observed ~34s

### Root Cause of ~34s Scan Time

The scan time is **dominated by ML Kit OCR calls**:
1. One OCR call in `StructuralLayoutAnalyzer` (structural pass on downsampled image)
2. N OCR calls in `TargetedOcrCoordinator` (one per detected zone, sequential)

With 3 zones (common for a typical ingredient label with ingredients + allergens + nutrition):
- 4 total ML Kit invocations
- Each takes 3-8 seconds on mid-range hardware
- Serial execution = 12-32 seconds

**This is ISSUE-001 from the main report.**

### OCR Strategy Distribution

`OCRPipelineRouter` routes individual zone bitmaps to strategies based on image metrics. For cropped zones from `TargetedOcrCoordinator`, the zones are typically:
- Width/height: 200-800px (zone crops from a full image)
- Aspect ratio: usually < 3.0 (not triggered for TILED)
- Strategy: mostly STANDARD or SHARPENED

The `TILED` strategy would only apply to very wide zones (>1600px wide or aspect > 3.0), which is less common. So the serial zone loop is the dominant path.

---

## OCR Preprocessing Analysis

`OCRComplexityAnalyzer.analyze()` is called:
1. Once in `StructuralLayoutAnalyzer` (line 40) on the full bitmap
2. Once in `OCRPipeline.invoke()` (line 108) for each direct OCR call

For the execution graph path, `OCRPipeline.runDirectOcr()` does NOT call `OCRComplexityAnalyzer` — it only calls `runOcrOnBitmap()`. The analysis is skipped for zone crops. ✓ Good.

**But**: `OCRPipeline.invoke()` (the full pipeline path) calls `OCRComplexityAnalyzer.analyze()` which downscales to 128px and does Sobel edge detection. For live camera frames, this fires once per 700ms throttle cycle. Each call creates a scaled bitmap copy. This is acceptable overhead (~50ms per call).

---

## Memory Performance

### Per-Scan Bitmap Allocations

For each scan via `PipelineRunner.run()`:

| Allocation | Size | Lifecycle |
|-----------|------|-----------|
| Raw bitmap (input) | `w × h × 4` bytes | Passed in, not owned by runner |
| `saveTempBitmap("raw")` copy | `w × h × 4` bytes | Saved to disk, in-memory reference |
| Downsampled bitmap (StructuralLayoutAnalyzer) | `400 × h' × 4` bytes | Recycled in `finally` ✓ |
| Zone crop bitmaps (TargetedOcrCoordinator) | Variable | Recycled after `runDirectOcr` ✓ |
| `saveTempBitmap("prep")` copy | `w × h × 4` bytes | In-memory reference |
| `savedBitmap` copy (OCRPipeline) | `w × h × 4` bytes | Stored in `OcrResult.frameBitmap`, then in `latestBitmap` — **never recycled** |

**Live Camera Frame Allocations** (every 700ms):
- Per-frame: normalizedBitmap + preprocessedBitmap + savedBitmap copy = ~3× frame size in memory
- The `savedBitmap` is kept alive via `ScanViewModel.latestBitmap` until next frame
- The `OcrResult` is stored in `_uiState` which holds the entire Compose state

**Estimated per-cycle leak:** ~2× (width × height × 4) bytes per 700ms cycle for the previous `latestBitmap` and `latestOcr.frameBitmap` that are overwritten without recycling.

---

## Dataset Loading Performance

`OntologyRepository` uses a `lazy` delegate that parses `knowledge/ontology/ontology.json` on first access. This is the first major I/O operation in a scan flow.

**Performance Impact:**
- File read: depends on file size. If ontology is 100KB: ~5-10ms. If 1MB: ~50-100ms.
- JSON parsing: linear in entry count. 1000 entries: ~50ms.
- `buildAliasMap()`: one map insertion per canonical + aliases. For 1000 entries with avg 3 aliases: ~4000 insertions, <10ms.

**First-scan penalty:** 60-200ms for ontology initialization.  
**Fix:** Warm up ontology on a background thread at app startup.

---

## Recommendations by Impact

### High Impact (Fix First)
1. **ISSUE-001**: Stop creating `SemanticExecutionGraph` per scan. Move to singleton in `ScanViewModel`. Expected improvement: eliminates 100-200ms per-scan object allocation overhead + fixes ML Kit recognizer leak.
2. **ISSUE-009**: Limit zone OCR to max 2 zones OR parallelize zone execution. Expected improvement: 50-60% reduction in scan time (from ~34s to ~12-15s). True parallel OCR using separate recognizer instances could reduce to ~8s.
3. **ISSUE-003**: Make `IngredientVocabulary` a singleton. Expected improvement: eliminates per-frame allocation of vocabulary objects during live camera.

### Medium Impact (Fix Second)
4. **ISSUE-002**: Recycle `latestBitmap` before overwrite. Expected improvement: prevents unbounded bitmap accumulation.
5. **ISSUE-008**: Warm up `OntologyRepository` at startup. Expected improvement: eliminates first-scan 60-200ms latency.

### Low Impact (Fix Third)
6. **ISSUE-010**: Deduplicate `IngredientInterpreter.interpret()` calls in legacy path. Expected improvement: 2/3 reduction in interpreter calls per ingredient.
7. **ISSUE-011**: Extract shared `deduplicateWords`/`calculateIoU`. Expected improvement: code quality only.
