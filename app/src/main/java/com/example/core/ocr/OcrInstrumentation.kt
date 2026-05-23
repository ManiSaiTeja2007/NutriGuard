package com.example.core.ocr

import android.util.Log
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageSource

object OcrInstrumentation {
    private const val TAG = "NutriGuardOcr"

    fun logSuccess(result: OcrResult) {
        val area = result.frame.width * result.frame.height
        val density = if (area > 0) {
            result.text.length.toFloat() / area
        } else {
            0f
        }

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
                "textDensity=${"%.6f".format(density)} " +
                "avgConfidence=${result.averageConfidence ?: "unavailable"}"
        )
    }

    fun logSkipped(result: OcrResult) {
        Log.w(
            TAG,
            "OCR skipped source=${result.source} " +
                "resolution=${result.frame.width}x${result.frame.height} " +
                "reason=${result.skippedReason}"
        )
    }

    fun logFailure(source: ImageSource, frame: FrameAnalysisResult, error: Throwable) {
        Log.e(
            TAG,
            "OCR failed source=$source " +
                "resolution=${frame.width}x${frame.height} " +
                "error=${error.message}",
            error
        )
    }
}
