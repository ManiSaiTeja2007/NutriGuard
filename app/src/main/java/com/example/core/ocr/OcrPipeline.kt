package com.example.core.ocr

import android.os.SystemClock
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageFrame
import com.example.core.pipeline.PipelineStage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Task
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class OcrPipeline(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : PipelineStage<Pair<ImageFrame, FrameAnalysisResult>, OcrResult>, Closeable {

    override suspend fun invoke(input: Pair<ImageFrame, FrameAnalysisResult>): OcrResult {
        val (frame, frameResult) = input

        // 1. Eligibility validation
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
                skippedReason = "Image is below ML Kit minimum size of 32x32"
            )
            OcrInstrumentation.logSkipped(skippedResult)
            return skippedResult
        }

        val startedAtMs = SystemClock.elapsedRealtime()

        return try {
            when (frame) {
                is ImageFrame.BitmapFrame -> {
                    require(!frame.bitmap.isRecycled) { "Bitmap frame has already been recycled." }

                    // Tiled / Segmented OCR
                    val pieces = OcrSegmentation.segment(frame.bitmap)
                    val textResults = mutableListOf<Text>()
                    try {
                        pieces.forEach { piece ->
                            val image = InputImage.fromBitmap(piece.bitmap, frame.rotationDegrees)
                            textResults += recognizer.process(image).await()
                        }
                    } finally {
                        // Memory safety: Recycle intermediate bitmaps
                        pieces.filter { it.recycleAfterUse }.forEach { it.bitmap.recycle() }
                    }

                    val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                    val mergedResult = toMergedOcrResult(textResults, frameResult, latencyMs, pieces.size)
                    OcrInstrumentation.logSuccess(mergedResult)
                    mergedResult
                }
                is ImageFrame.CameraXFrame -> {
                    // Full-image OCR direct from CameraX
                    val inputImage = frame.toInputImage()
                    val text = recognizer.process(inputImage).await()
                    val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                    val result = toSingleOcrResult(text, frameResult, latencyMs)
                    OcrInstrumentation.logSuccess(result)
                    result
                }
            }
        } catch (error: Throwable) {
            OcrInstrumentation.logFailure(frameResult.source, frameResult, error)
            throw error
        }
    }

    private fun isOcrEligible(frameResult: FrameAnalysisResult): Boolean {
        return frameResult.width >= 32 || frameResult.height >= 32
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

    private fun toSingleOcrResult(
        text: Text,
        frameResult: FrameAnalysisResult,
        latencyMs: Long
    ): OcrResult {
        val lines = text.textBlocks.flatMap { it.lines }
        val elements = lines.flatMap { it.elements }
        val confidenceValues = lines
            .map { it.confidence }
            .filter { it > 0f }

        return OcrResult(
            text = text.text,
            processingLatencyMs = latencyMs,
            averageConfidence = confidenceValues.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            textBlockCount = text.textBlocks.size,
            lineCount = lines.size,
            elementCount = elements.size,
            source = frameResult.source,
            frame = frameResult
        )
    }

    private fun toMergedOcrResult(
        results: List<Text>,
        frameResult: FrameAnalysisResult,
        latencyMs: Long,
        segmentsProcessed: Int
    ): OcrResult {
        val allBlocks = results.flatMap { it.textBlocks }
        val allLines = allBlocks.flatMap { it.lines }
        val allElements = allLines.flatMap { it.elements }
        val confidenceValues = allLines
            .map { it.confidence }
            .filter { it > 0f }

        return OcrResult(
            text = results.map { it.text }
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n"),
            processingLatencyMs = latencyMs,
            averageConfidence = confidenceValues.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            textBlockCount = allBlocks.size,
            lineCount = allLines.size,
            elementCount = allElements.size,
            source = frameResult.source,
            frame = frameResult,
            segmentsProcessed = segmentsProcessed
        )
    }
}
