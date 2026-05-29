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

        return try {
            val normalizedBitmap = frame.toNormalisedBitmap()
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

            val isTemporary = (frame is ImageFrame.CameraXFrame) || (frame is ImageFrame.BitmapFrame && frame.rotationDegrees != 0)

            val metrics = OCRComplexityAnalyzer.analyze(normalizedBitmap)
            val strategy = OCRPipelineRouter.route(normalizedBitmap.width, normalizedBitmap.height, metrics)

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
                            val tileBlocksList = mutableListOf<OCRBlock>()
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
                if (context != null && toSave != null) {
                    com.example.core.export.PipelineSnapshotRepository.saveTempBitmap(context!!, toSave, "prep")
                }
                preprocessedBitmap?.recycle()
            }

            val savedBitmap = try {
                if (isTemporary) normalizedBitmap.copy(normalizedBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, true) else normalizedBitmap
            } catch (e: Exception) {
                null
            }

            if (isTemporary) {
                normalizedBitmap.recycle()
            }

            val reconstructedLines = OCRLineReconstructor.reconstruct(words)
            if (words.isNotEmpty() && reconstructedLines.isEmpty()) {
                pipelineFailures.add(FailureType.LINE_RECONSTRUCTION_FAILURE)
            }

            val vocabulary = IngredientVocabulary().getVocabulary()
            val detectedParagraphs = IngredientRegionDetector.detectRegion(reconstructedLines, vocabulary)

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
        }
    }

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

    private fun isOcrEligible(frameResult: FrameAnalysisResult): Boolean {
        return frameResult.width >= 8 && frameResult.height >= 8
    }

    override fun close() {
        recognizer.close()
    }

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

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun ImageFrame.toNormalisedBitmap(): Bitmap? {
        return when (this) {
            is ImageFrame.BitmapFrame -> {
                rotateBitmap(this.bitmap, this.rotationDegrees)
            }
            is ImageFrame.CameraXFrame -> {
                val bitmap = this.imageProxy.toBitmapCompat() ?: return null
                rotateBitmap(bitmap, this.rotationDegrees)
            }
        }
    }

    private fun ImageProxy.toBitmapCompat(): Bitmap? {
        val image = this.image ?: return null
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun yuv420ToNv21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val yPixelStride = image.planes[0].pixelStride

        val uRowStride = image.planes[1].rowStride
        val uPixelStride = image.planes[1].pixelStride

        val vRowStride = image.planes[2].rowStride
        val vPixelStride = image.planes[2].pixelStride

        var pos = 0
        if (yRowStride == width && yPixelStride == 1) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get()
                }
            }
        }

        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until uvWidth) {
                val uVal = uBuffer.get(uRowStart + col * uPixelStride)
                val vVal = vBuffer.get(vRowStart + col * vPixelStride)
                nv21[pos++] = vVal
                nv21[pos++] = uVal
            }
        }
        return nv21
    }
}
