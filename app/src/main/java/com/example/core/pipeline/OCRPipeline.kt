package com.example.core.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageFrame
import com.example.core.ocr.OCRBlock
import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord
import com.example.core.ocr.OcrResult
import com.example.core.ocr.OcrInstrumentation
import com.example.core.ocr.preprocessing.OcrPreprocessor
import com.example.core.ocr.reconstruction.OCRLineReconstructor
import com.example.core.ocr.reconstruction.IngredientRegionDetector
import com.example.core.ocr.validation.OcrInputValidator
import com.example.core.intelligence.correction.FailureType
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.ocr.routing.OCRComplexityAnalyzer
import com.example.core.ocr.routing.OCRPipelineRouter
import com.example.core.ocr.tiling.TiledOCRProcessor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class OCRPipeline(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : PipelineStage<Pair<ImageFrame, FrameAnalysisResult>, OcrResult>, Closeable {

    var context: android.content.Context? = null

    // ISSUE-003 FIX: Single vocabulary instance shared across all OCR invocations.
    // Previously IngredientVocabulary() was instantiated inside invoke() on every camera frame,
    // causing ~1.4 allocations/second during live camera scanning.
    private val vocabulary = IngredientVocabulary()

    /**
     * Executes the main OCR pipeline on the input frame.
     *
     * Steps:
     * 1. Validate image dimensions to ensure eligibility (>8x8 and >32x32 for ML Kit).
     * 2. Normalize and rotate the input frame into a standardized upright bitmap.
     * 3. Analyze image complexity metrics (blur, contrast, brightness).
     * 4. Route to the optimal OCR preprocessing strategy (UPSCALE, SHARPENED, THRESHOLDED, LOW_LIGHT, TILED, or STANDARD).
     * 5. Run ML Kit text recognition on the processed bitmap.
     * 6. Reconstruct individual line hierarchies and run layout structure zoning.
     * 7. Package results and log analytics instrumentation.
     * 8. Clean up intermediate temporary bitmaps to avoid memory leaks.
     *
     * @param input Pair of [ImageFrame] and [FrameAnalysisResult].
     * @return [OcrResult] containing recognized text, confidence, blocks, and preprocessing metadata.
     */
    override suspend fun invoke(input: Pair<ImageFrame, FrameAnalysisResult>): OcrResult {

        val (frame, frameResult) = input

        if (!isOcrEligible(frameResult)) {
            val skippedResult = OcrResult(
                text = "",
                processingLatencyMs = 0L,
                averageConfidence = null,
                textBlockCount = 0,
                lineCount = 0,
                elementCount = 0,
                source = frameResult.source,
                frame = frameResult,
                segmentsProcessed = 0,
                skippedReason = "Image is below minimum size of 8x8",
                failures = listOf(FailureType.INVALID_IMAGE_SIZE_FAILURE)
            )
            OcrInstrumentation.logSkipped(skippedResult)
            return skippedResult
        }

        if (frameResult.width < 32 && frameResult.height < 32) {
            val skippedResult = OcrResult(
                text = "",
                processingLatencyMs = 0L,
                averageConfidence = null,
                textBlockCount = 0,
                lineCount = 0,
                elementCount = 0,
                source = frameResult.source,
                frame = frameResult,
                segmentsProcessed = 0,
                skippedReason = "Image is below ML Kit minimum size of 32x32",
                failures = listOf(FailureType.INVALID_IMAGE_SIZE_FAILURE)
            )
            OcrInstrumentation.logSkipped(skippedResult)
            return skippedResult
        }

        val startedAtMs = SystemClock.elapsedRealtime()

        var normalizedBitmap: Bitmap? = null
        var isTemporary = false

        return try {
            normalizedBitmap = frame.toNormalisedBitmap()
            if (normalizedBitmap == null) {
                val skippedResult = OcrResult(
                    text = "",
                    processingLatencyMs = 0L,
                    averageConfidence = null,
                    textBlockCount = 0,
                    lineCount = 0,
                    elementCount = 0,
                    source = frameResult.source,
                    frame = frameResult,
                    segmentsProcessed = 0,
                    skippedReason = "Failed to convert frame to Bitmap",
                    failures = listOf(FailureType.INVALID_BITMAP_FAILURE)
                )
                OcrInstrumentation.logSkipped(skippedResult)
                return skippedResult
            }

            isTemporary = (frame is ImageFrame.CameraXFrame) || (frame is ImageFrame.BitmapFrame && frame.rotationDegrees != 0)

            val metrics = OCRComplexityAnalyzer.analyze(normalizedBitmap)
            val strategy = OCRPipelineRouter.route(normalizedBitmap.width, normalizedBitmap.height, metrics)

            // Downscale only for expensive JIT binarization/preprocessing strategies to prevent latency explosion
            if (strategy == OCRPipelineRouter.OcrStrategy.SHARPENED ||
                strategy == OCRPipelineRouter.OcrStrategy.THRESHOLDED ||
                strategy == OCRPipelineRouter.OcrStrategy.LOW_LIGHT) {
                val MAX_DIMENSION = 1200
                if (normalizedBitmap.width > MAX_DIMENSION || normalizedBitmap.height > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / maxOf(normalizedBitmap.width, normalizedBitmap.height)
                    val targetW = (normalizedBitmap.width * scale).toInt()
                    val targetH = (normalizedBitmap.height * scale).toInt()
                    val scaled = Bitmap.createScaledBitmap(normalizedBitmap, targetW, targetH, true)
                    if (isTemporary) {
                        normalizedBitmap.recycle()
                    }
                    isTemporary = true
                    normalizedBitmap = scaled
                }
            }

            var tileRegions = emptyList<Rect>()
            var words = emptyList<OCRWord>()
            var blocks = emptyList<OCRBlock>()
            val pipelineFailures = mutableListOf<FailureType>()
            var preprocessedBitmap: Bitmap? = null

            try {
                when (strategy) {
                    OCRPipelineRouter.OcrStrategy.UPSCALE -> {
                        val scale = 4.0f
                        val targetW = (normalizedBitmap.width * scale).toInt().coerceAtLeast(32)
                        val targetH = (normalizedBitmap.height * scale).toInt().coerceAtLeast(32)
                        preprocessedBitmap = OcrPreprocessor.upscaleBilinear(normalizedBitmap, targetW, targetH)
                        
                        val validation = OcrInputValidator.validate(preprocessedBitmap)
                        if (!validation.isValid) {
                            val failureType = validation.failureType ?: FailureType.PREPROCESSING_FAILURE
                            pipelineFailures.add(failureType)
                        } else {
                            val parsed = runOcrOnBitmap(preprocessedBitmap)
                            words = parsed.first
                            blocks = parsed.second
                        }
                    }
                    OCRPipelineRouter.OcrStrategy.SHARPENED -> {
                        preprocessedBitmap = if (metrics.brightness > 200f) {
                            OcrPreprocessor.applyEdgeEnhancement(normalizedBitmap)
                        } else {
                            OcrPreprocessor.applySharpen(normalizedBitmap)
                        }
                        
                        val validation = OcrInputValidator.validate(preprocessedBitmap)
                        if (!validation.isValid) {
                            val failureType = validation.failureType ?: FailureType.PREPROCESSING_FAILURE
                            pipelineFailures.add(failureType)
                        } else {
                            val parsed = runOcrOnBitmap(preprocessedBitmap)
                            words = parsed.first
                            blocks = parsed.second
                        }
                    }
                    OCRPipelineRouter.OcrStrategy.THRESHOLDED -> {
                        preprocessedBitmap = OcrPreprocessor.toGrayscale(normalizedBitmap)
                            .let { OcrPreprocessor.applyAdaptiveThreshold(it) }
                        
                        val validation = OcrInputValidator.validate(preprocessedBitmap)
                        if (!validation.isValid) {
                            val failureType = validation.failureType ?: FailureType.PREPROCESSING_FAILURE
                            pipelineFailures.add(failureType)
                        } else {
                            val parsed = runOcrOnBitmap(preprocessedBitmap)
                            words = parsed.first
                            blocks = parsed.second
                        }
                    }
                    OCRPipelineRouter.OcrStrategy.LOW_LIGHT -> {
                        preprocessedBitmap = OcrPreprocessor.toGrayscale(normalizedBitmap)
                            .let { OcrPreprocessor.normalizeBrightness(it) }
                            .let { OcrPreprocessor.applyClahe(it) }
                            .let { OcrPreprocessor.applySharpen(it) }
                        
                        val validation = OcrInputValidator.validate(preprocessedBitmap)
                        if (!validation.isValid) {
                            val failureType = validation.failureType ?: FailureType.PREPROCESSING_FAILURE
                            pipelineFailures.add(failureType)
                        } else {
                            val parsed = runOcrOnBitmap(preprocessedBitmap)
                            words = parsed.first
                            blocks = parsed.second
                        }
                    }
                    OCRPipelineRouter.OcrStrategy.TILED -> {
                        try {
                            val tileBlocksList = java.util.Collections.synchronizedList(mutableListOf<OCRBlock>())
                            val tiledResult = TiledOCRProcessor.runTiledOcr(normalizedBitmap) { tile ->
                                val (tileWords, tileBlocks) = runOcrOnBitmap(tile)
                                tileBlocksList.addAll(tileBlocks)
                                tileWords
                            }
                            words = tiledResult.first
                            tileRegions = tiledResult.second
                            blocks = tileBlocksList
                        } catch (e: Exception) {
                            pipelineFailures.add(FailureType.TILE_RECONSTRUCTION_FAILURE)
                        }
                    }
                    OCRPipelineRouter.OcrStrategy.STANDARD -> {
                        val validation = OcrInputValidator.validate(normalizedBitmap)
                        if (!validation.isValid) {
                            val failureType = validation.failureType ?: FailureType.PREPROCESSING_FAILURE
                            pipelineFailures.add(failureType)
                        } else {
                            val parsed = runOcrOnBitmap(normalizedBitmap)
                            words = parsed.first
                            blocks = parsed.second
                        }
                    }
                }
            } catch (e: Exception) {
                if (strategy == OCRPipelineRouter.OcrStrategy.TILED) {
                    pipelineFailures.add(FailureType.TILE_RECONSTRUCTION_FAILURE)
                } else {
                    pipelineFailures.add(FailureType.OCR_PIPELINE_ROUTING_FAILURE)
                }
            } finally {
                val toSave = preprocessedBitmap ?: normalizedBitmap
                if (context != null) {
                    com.example.core.export.PipelineSnapshotRepository.saveTempBitmap(context!!, toSave, "prep")
                }
                preprocessedBitmap?.recycle()
            }

            val savedBitmap = try {
                if (isTemporary) normalizedBitmap.copy(normalizedBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, true) else normalizedBitmap
            } catch (e: Exception) {
                null
            }

            val reconstructedLines = OCRLineReconstructor.reconstruct(words)
            if (words.isNotEmpty() && reconstructedLines.isEmpty()) {
                pipelineFailures.add(FailureType.LINE_RECONSTRUCTION_FAILURE)
            }

            val vocabSet = vocabulary.getVocabulary()
            val detectedParagraphs = IngredientRegionDetector.detectRegion(reconstructedLines, vocabSet)

            val finalOcrText = if (detectedParagraphs.isNotEmpty()) {
                detectedParagraphs.joinToString(separator = "\n") { line ->
                    line.words.joinToString(separator = " ") { it.text }
                }
            } else {
                reconstructedLines.joinToString(separator = "\n") { line ->
                    line.words.joinToString(separator = " ") { it.text }
                }
            }

            val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            
            val confidenceValues = detectedParagraphs.flatMap { it.words }.map { it.confidence }
            val averageConfidence = if (confidenceValues.isNotEmpty()) confidenceValues.average().toFloat() else 0.8f

            val ocrResult = OcrResult(
                text = finalOcrText,
                processingLatencyMs = latencyMs,
                averageConfidence = averageConfidence,
                textBlockCount = reconstructedLines.size,
                lineCount = reconstructedLines.size,
                elementCount = words.size,
                source = frameResult.source,
                frame = frameResult,
                segmentsProcessed = if (strategy == OCRPipelineRouter.OcrStrategy.TILED) tileRegions.size else 1,
                ocrBlocks = blocks,
                ocrWords = words,
                reconstructedLines = reconstructedLines,
                detectedParagraphs = detectedParagraphs,
                passesRun = listOf(strategy.name.lowercase()),
                failures = pipelineFailures,
                blurScore = metrics.blurScore,
                contrastScore = metrics.contrast,
                brightnessScore = metrics.brightness,
                complexityRating = metrics.complexityRating,
                routedStrategy = strategy.name,
                tileRegions = tileRegions,
                frameBitmap = savedBitmap
            )

            OcrInstrumentation.logSuccess(ocrResult)
            ocrResult
        } catch (error: Throwable) {
            OcrInstrumentation.logFailure(frameResult.source, frameResult, error)
            throw error
        } finally {
            if (isTemporary) {
                normalizedBitmap?.recycle()
            }
        }
    }

    /**
     * Runs direct OCR text recognition on a bitmap without layout analysis or routing.
     *
     * @param bitmap Raw image bitmap.
     * @return [OcrResult] wrapping standard OCR text outputs.
     */
    suspend fun runDirectOcr(bitmap: Bitmap): OcrResult {
        val startedAtMs = SystemClock.elapsedRealtime()
        val (words, blocks) = runOcrOnBitmap(bitmap)
        val reconstructedLines = OCRLineReconstructor.reconstruct(words)
        val finalOcrText = reconstructedLines.joinToString(separator = "\n") { line ->
            line.words.joinToString(separator = " ") { it.text }
        }
        val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        val confidenceValues = words.map { it.confidence }
        val averageConfidence = if (confidenceValues.isNotEmpty()) confidenceValues.average().toFloat() else 0.8f

        return OcrResult(
            text = finalOcrText,
            processingLatencyMs = latencyMs,
            averageConfidence = averageConfidence,
            textBlockCount = blocks.size,
            lineCount = reconstructedLines.size,
            elementCount = words.size,
            source = com.example.core.imaging.ImageSource.CAMERA_X,
            frame = FrameAnalysisResult(
                width = bitmap.width,
                height = bitmap.height,
                rotationDegrees = 0,
                timestampNanos = System.nanoTime(),
                source = com.example.core.imaging.ImageSource.CAMERA_X,
                hasBitmap = true,
                processingLatencyMs = latencyMs
            ),
            segmentsProcessed = 1,
            ocrBlocks = blocks,
            ocrWords = words,
            reconstructedLines = reconstructedLines,
            detectedParagraphs = reconstructedLines,
            passesRun = listOf("direct"),
            failures = emptyList(),
            blurScore = 0f,
            contrastScore = 0f,
            brightnessScore = 0f,
            complexityRating = "LOW",
            routedStrategy = "DIRECT"
        )
    }

    /**
     * Executes ML Kit text detection on the specified bitmap and maps GMS model outputs
     * to internal models ([OCRWord], [OCRBlock]).
     *
     * @param bitmap Preprocessed upright bitmap.
     * @return Pair of word list and block list.
     */
    private suspend fun runOcrOnBitmap(bitmap: Bitmap): Pair<List<OCRWord>, List<OCRBlock>> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val textResult = recognizer.process(image).await()
        val words = mutableListOf<OCRWord>()
        val blocks = mutableListOf<OCRBlock>()

        for (block in textResult.textBlocks) {
            val blockLines = mutableListOf<OCRLine>()
            for (line in block.lines) {
                val lineWords = mutableListOf<OCRWord>()
                for (element in line.elements) {
                    val bounds = element.boundingBox ?: continue
                    val confidence = element.confidence
                    val word = OCRWord(element.text, confidence, bounds)
                    lineWords.add(word)
                    words.add(word)
                }
                val lineBounds = line.boundingBox ?: continue
                blockLines.add(OCRLine(lineWords, lineBounds, line.confidence))
            }
            val blockBounds = block.boundingBox ?: continue
            val avgConf = if (blockLines.isNotEmpty()) blockLines.map { it.confidence }.average().toFloat() else 1.0f
            blocks.add(OCRBlock(blockLines, blockBounds, avgConf))
        }
        return Pair(words, blocks)
    }

    /**
     * Checks if the frame dimensions meet minimum size requirements for character recognition.
     */
    private fun isOcrEligible(frameResult: FrameAnalysisResult): Boolean {
        return frameResult.width >= 8 && frameResult.height >= 8
    }

    /**
     * Releases the ML Kit TextRecognizer engine instance.
     */
    override fun close() {
        recognizer.close()
    }

    /**
     * Converts a GMS Task into a suspendable coroutine continuation.
     */
    private suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

}
