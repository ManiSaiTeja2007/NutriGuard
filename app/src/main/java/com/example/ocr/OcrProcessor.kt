package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.example.camera.FrameAnalysisResult
import com.example.camera.FrameInput
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

class OcrProcessor(
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
) : Closeable {

    suspend fun recognizeBitmap(frame: FrameInput, frameResult: FrameAnalysisResult): OcrResult {
        val bitmap = requireNotNull(frame.bitmap) {
            "Bitmap OCR requires FrameInput.bitmap."
        }
        val startedAtMs = SystemClock.elapsedRealtime()
        val pieces = bitmap.toOcrPieces()
        val textResults = mutableListOf<Text>()

        try {
            pieces.forEach { piece ->
                val image = InputImage.fromBitmap(piece.bitmap, frame.rotationDegrees)
                textResults += recognizer.process(image).await()
            }
        } finally {
            pieces
                .filter { it.recycleAfterUse }
                .forEach { it.bitmap.recycle() }
        }

        val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        val result = textResults.toMergedOcrResult(
            frameResult = frameResult,
            latencyMs = latencyMs,
            segmentsProcessed = pieces.size
        )

        Log.d(
            TAG,
            "OCR success source=${result.source} " +
                "resolution=${result.frame.width}x${result.frame.height} " +
                "rotation=${result.frame.rotationDegrees} " +
                "latencyMs=${result.processingLatencyMs} " +
                "segments=${result.segmentsProcessed} " +
                "blocks=${result.textBlockCount} " +
                "lines=${result.lineCount} " +
                "elements=${result.elementCount} " +
                "textLength=${result.text.length} " +
                "avgConfidence=${result.averageConfidence ?: "unavailable"}"
        )

        return result
    }

    fun recognizeAsync(
        image: InputImage,
        frameResult: FrameAnalysisResult,
        onResult: (OcrResult) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit
    ) {
        if (!frameResult.isOcrEligible()) {
            val result = OcrResult(
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

            Log.w(
                TAG,
                "OCR skipped source=${result.source} " +
                    "resolution=${result.frame.width}x${result.frame.height} " +
                    "reason=${result.skippedReason}"
            )
            onResult(result)
            onComplete()
            return
        }

        val startedAtMs = SystemClock.elapsedRealtime()
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                val result = text.toOcrResult(frameResult, latencyMs)

                Log.d(
                    TAG,
                    "OCR success source=${result.source} " +
                        "resolution=${result.frame.width}x${result.frame.height} " +
                        "rotation=${result.frame.rotationDegrees} " +
                        "latencyMs=${result.processingLatencyMs} " +
                        "segments=${result.segmentsProcessed} " +
                        "blocks=${result.textBlockCount} " +
                        "lines=${result.lineCount} " +
                        "elements=${result.elementCount} " +
                        "textLength=${result.text.length} " +
                        "avgConfidence=${result.averageConfidence ?: "unavailable"}"
                )

                onResult(result)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "OCR failed.", error)
                onError(error)
            }
            .addOnCompleteListener {
                onComplete()
            }
    }

    override fun close() {
        recognizer.close()
    }

    private fun Text.toOcrResult(
        frameResult: FrameAnalysisResult,
        latencyMs: Long
    ): OcrResult {
        val lines = textBlocks.flatMap { it.lines }
        val elements = lines.flatMap { it.elements }
        val confidenceValues = lines
            .map { it.confidence }
            .filter { it > 0f }

        return OcrResult(
            text = text,
            processingLatencyMs = latencyMs,
            averageConfidence = confidenceValues.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            textBlockCount = textBlocks.size,
            lineCount = lines.size,
            elementCount = elements.size,
            source = frameResult.source,
            frame = frameResult
        )
    }

    private fun List<Text>.toMergedOcrResult(
        frameResult: FrameAnalysisResult,
        latencyMs: Long,
        segmentsProcessed: Int
    ): OcrResult {
        val allBlocks = flatMap { it.textBlocks }
        val allLines = allBlocks.flatMap { it.lines }
        val allElements = allLines.flatMap { it.elements }
        val confidenceValues = allLines
            .map { it.confidence }
            .filter { it > 0f }

        return OcrResult(
            text = map { it.text }
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

    private fun FrameAnalysisResult.isOcrEligible(): Boolean {
        return width >= MIN_OCR_SIZE_PX && height >= MIN_OCR_SIZE_PX
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

    private fun Bitmap.toOcrPieces(): List<OcrBitmapPiece> {
        if (width >= MIN_OCR_SIZE_PX && height >= MIN_OCR_SIZE_PX) {
            return listOf(OcrBitmapPiece(bitmap = this, recycleAfterUse = false))
        }

        if (width <= MIN_OCR_SIZE_PX && height <= MIN_OCR_SIZE_PX) {
            return listOf(toPaddedPiece(0, 0, width, height))
        }

        val pieces = mutableListOf<OcrBitmapPiece>()
        val longSide = maxOf(width, height)
        val tileLongSide = minOf(MAX_TILE_LONG_SIDE_PX, longSide)
        val step = (tileLongSide - TILE_OVERLAP_PX).coerceAtLeast(MIN_OCR_SIZE_PX)
        var start = 0

        while (start < longSide) {
            val end = minOf(start + tileLongSide, longSide)
            if (width >= height) {
                pieces += toPaddedPiece(start, 0, end - start, height)
            } else {
                pieces += toPaddedPiece(0, start, width, end - start)
            }

            if (end == longSide) break
            start += step
        }

        return pieces
    }

    private fun Bitmap.toPaddedPiece(
        left: Int,
        top: Int,
        cropWidth: Int,
        cropHeight: Int
    ): OcrBitmapPiece {
        val outputWidth = cropWidth.coerceAtLeast(MIN_OCR_SIZE_PX)
        val outputHeight = cropHeight.coerceAtLeast(MIN_OCR_SIZE_PX)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val destinationLeft = ((outputWidth - cropWidth) / 2f).coerceAtLeast(0f)
        val destinationTop = ((outputHeight - cropHeight) / 2f).coerceAtLeast(0f)
        canvas.drawBitmap(
            this,
            Rect(left, top, left + cropWidth, top + cropHeight),
            RectF(
                destinationLeft,
                destinationTop,
                destinationLeft + cropWidth,
                destinationTop + cropHeight
            ),
            null
        )

        return OcrBitmapPiece(bitmap = output, recycleAfterUse = true)
    }

    private data class OcrBitmapPiece(
        val bitmap: Bitmap,
        val recycleAfterUse: Boolean
    )

    private companion object {
        const val TAG = "NutriGuardOcr"
        const val MIN_OCR_SIZE_PX = 32
        const val MAX_TILE_LONG_SIDE_PX = 160
        const val TILE_OVERLAP_PX = 24
    }
}
