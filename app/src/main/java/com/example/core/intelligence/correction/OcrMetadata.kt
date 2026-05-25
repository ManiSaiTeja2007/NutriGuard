package com.example.core.intelligence.correction

/**
 * Contains raw and normalized visual characteristics used to calibrate
 * semantic correction strictness.
 */
data class OcrMetadata(
    val ocrConfidence: Float = 0.8f,
    val blurScore: Float = 10f,
    val contrastScore: Float = 30f,
    val brightnessScore: Float = 120f
)
