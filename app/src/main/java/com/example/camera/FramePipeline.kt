package com.example.camera

import android.os.SystemClock
import android.util.Log

class FramePipeline(
    private val throttleMs: Long = DEFAULT_THROTTLE_MS,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var lastAcceptedAtMs: Long? = null

    fun process(frame: FrameInput): FrameAnalysisResult? {
        val startedAtMs = clockMs()
        val previousAcceptedAtMs = lastAcceptedAtMs
        if (previousAcceptedAtMs != null && startedAtMs - previousAcceptedAtMs < throttleMs) {
            return null
        }

        require(frame.width > 0) { "Frame width must be positive." }
        require(frame.height > 0) { "Frame height must be positive." }
        require(frame.rotationDegrees in 0..359) { "Rotation must be between 0 and 359 degrees." }
        require(frame.timestampNanos >= 0L) { "Timestamp must be non-negative." }

        lastAcceptedAtMs = startedAtMs
        val processingLatencyMs = (clockMs() - startedAtMs).coerceAtLeast(0L)

        val result = FrameAnalysisResult(
            width = frame.width,
            height = frame.height,
            rotationDegrees = frame.rotationDegrees,
            timestampNanos = frame.timestampNanos,
            source = frame.source,
            hasBitmap = frame.bitmap != null,
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
