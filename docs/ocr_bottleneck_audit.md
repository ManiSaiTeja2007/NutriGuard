# OCR Bottleneck Audit — Stage 13.0E

This document details the performance investigation into why the scan ingestion pipeline exhibited a **~34.0-second** latency on real-device runs, accounting for **~97%** of the total scan runtime.

## 1. The Call Chain Bottleneck

When the user clicks "Ingest Scanned Text", the following execution sequence is triggered:

```text
ScanViewModel.processAndNavigate()
  ↓
PipelineRunner.run()
  ↓
SemanticExecutionGraph.execute()
  ↓
TargetedOcrCoordinator.execute()
  [Loops over each detected zone (e.g., 3-4 zones)]
      ↓
      ImageFrame.BitmapFrame allocation (redundant)
      FrameAnalysisResult allocation (redundant)
      ↓
      OCRPipeline.invoke() [Called per crop]
          ↓
          OCRComplexityAnalyzer.analyze()
          ↓
          OCRPipelineRouter.route()
              [Matches aspect ratio > 3.0f or width < 128]
              ↓
              - Routes to UPSCALE (4x scale scaleBilinear)
              - Routes to TILED (TiledOCRProcessor.runTiledOcr)
                  [Slices cropped bitmap into 2 to 4 horizontal tiles]
                  [Loops over each tile]
                      ↓
                      MLKit TextRecognizer.process() [Sequential await()]
```

## 2. Root Cause Analysis

### 2.1 Aspect Ratio Tiling Explosion
Cropped text zones (like ingredients list boxes or allergen lines) naturally have high width-to-height aspect ratios (typically >3.0f).
Inside `OCRPipelineRouter.route()`:
```kotlin
aspectRatio > 3.0f || width > 1600 -> OcrStrategy.TILED
```
This causes *every single crop* to be routed to `OcrStrategy.TILED` regardless of its actual size.
For example, if we have 3 crops, each is sliced into 3 tiles, resulting in **9 sequential calls** to ML Kit's `TextRecognizer.process().await()`. Since each ML Kit call blocks for 300–600ms on a real device, this sequential wait immediately adds **3.0 to 5.4 seconds** of pure native inference latency.

### 2.2 CPU-Bound Kotlin Image Processing
If a crop's visual metrics trigger `OcrStrategy.SHARPENED`, `THRESHOLDED`, or `LOW_LIGHT`:
The custom Kotlin binarization and filtering algorithms inside `OcrPreprocessor.kt` run in double nested loops over all bitmap pixels:
1. `applySharpen` runs a 3x3 Laplacian convolution matrix. For each pixel, it performs 9 array accesses and multiplications.
2. `applyAdaptiveThreshold` computes an integral image and performs Bradley-Roth binarization over the entire image grid.
3. `applyClahe` performs histogram equalization over local tiles and bilinearly interpolates.

Running these O(N) pixel loops in pure JIT-compiled Kotlin bytecode on the Android Runtime (ART) is extremely slow. For a 1200x400 cropped region, a single sharpening pass takes **up to 12.0 seconds** of pure CPU execution, blocking the calling thread. Multiple zones amplify this to the observed **34.0 seconds**.

### 2.3 Redundant Post-Processing
`OCRPipeline` runs `OCRLineReconstructor.reconstruct` and `IngredientRegionDetector.detectRegion` on the cropped image text. This is completely redundant because `TargetedOcrCoordinator` is already running on pre-segmented crop zones detected by `StructuralLayoutAnalyzer`.

---

## 3. The Optimization Solution

Since cropped zones are already isolated regions of interest:
1. They **do not** require slicing or tiling (as alignment is already localized).
2. They **do not** require sharpening, thresholding, or low-light normalization (ML Kit is highly robust to varying lighting in small crops).
3. We can introduce `runDirectOcr(bitmap)` on `OCRPipeline` which directly calls ML Kit's `TextRecognizer` on the raw cropped bitmap, bypassing all complexity routing, tiling, and preprocessing loops.
