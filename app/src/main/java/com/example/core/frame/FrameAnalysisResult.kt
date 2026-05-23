package com.example.core.frame

import com.example.core.imaging.ImageSource

data class FrameAnalysisResult(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val source: ImageSource,
    val hasBitmap: Boolean,
    val processingLatencyMs: Long
)
