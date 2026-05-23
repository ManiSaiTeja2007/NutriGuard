package com.example.core.frame

import android.os.SystemClock
import android.util.Log
import com.example.core.imaging.ImageFrame
import com.example.core.pipeline.PipelineStage

class FramePipeline(
    private val throttleMs: Long = DEFAULT_THROTTLE_MS,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) : PipelineStage<ImageFrame, FrameAnalysisResult?> {
    private var lastAcceptedAtMs: Long? = null

    override suspend fun invoke(input: ImageFrame): FrameAnalysisResult? {
        val startedAtMs = clockMs()
        val previousAcceptedAtMs = lastAcceptedAtMs
        if (previousAcceptedAtMs != null && startedAtMs - previousAcceptedAtMs < throttleMs) {
            return null
        }

        require(input.width > 0) { "Frame width must be positive." }
        require(input.height > 0) { "Frame height must be positive." }
        require(input.rotationDegrees in 0..359) { "Rotation must be between 0 and 359 degrees." }
        require(input.timestampNanos >= 0L) { "Timestamp must be non-negative." }

        lastAcceptedAtMs = startedAtMs
        val processingLatencyMs = (clockMs() - startedAtMs).coerceAtLeast(0L)

        val hasBitmap = when (input) {
            is ImageFrame.BitmapFrame -> true
            is ImageFrame.CameraXFrame -> false
        }

        val result = FrameAnalysisResult(
            width = input.width,
            height = input.height,
            rotationDegrees = input.rotationDegrees,
            timestampNanos = input.timestampNanos,
            source = input.source,
            hasBitmap = hasBitmap,
            processingLatencyMs = processingLatencyMs
        )

        Log.d(
            TAG,
            "Frame accepted source=${result.source} " +
                "resolution=${result.width}x${result.height} " +
                "rotation=${result.rotationDegrees} " +
                "timestampNanos=${result.timestampNanos} " +
                "pipelineLatencyMs=${result.processingLatencyMs}"
        )

        return result
    }

    companion object {
        const val DEFAULT_THROTTLE_MS = 700L
        private const val TAG = "NutriGuardFrame"
    }
}
