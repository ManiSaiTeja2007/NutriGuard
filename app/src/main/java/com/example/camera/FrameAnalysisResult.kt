package com.example.camera

data class FrameAnalysisResult(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val source: FrameSource,
    val hasBitmap: Boolean,
    val processingLatencyMs: Long
)
