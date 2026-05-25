package com.example.core.intelligence.calibration

import java.util.Locale

object ConfidenceCalibrationEngine {

    /**
     * Exact semantic meaning of normalized metrics:
     * - normalizedBlur: 0.0 (completely blurry/low Sobel variance) to 1.0 (very sharp).
     * - normalizedBrightness: 0.0 (pitch black/underexposed) to 1.0 (pure white/overexposed/glow).
     * - normalizedContrast: 0.0 (no variance/flat washed colors) to 1.0 (high dynamic range contrast).
     */
    data class NormalizedVisualMetrics(
        val normalizedBlur: Float,
        val normalizedBrightness: Float,
        val normalizedContrast: Float
    )

    data class CalibrationProfile(
        val name: String,
        val maxEditDistance: Int,
        val minimumConfidenceThreshold: Float,
        val allowAmbiguousCorrection: Boolean,
        val safeguardStrictness: Float // 0.0 (loose, aggressive) to 1.0 (extremely conservative/strict)
    )

    val DEFAULT_PROFILE = CalibrationProfile(
        name = "DEFAULT",
        maxEditDistance = 2,
        minimumConfidenceThreshold = 0.80f,
        allowAmbiguousCorrection = false,
        safeguardStrictness = 0.60f
    )

    val BLURRY_PROFILE = CalibrationProfile(
        name = "BLURRY",
        maxEditDistance = 3,
        minimumConfidenceThreshold = 0.70f,
        allowAmbiguousCorrection = true,
        safeguardStrictness = 0.40f
    )

    val LOW_LIGHT_PROFILE = CalibrationProfile(
        name = "LOW_LIGHT",
        maxEditDistance = 2,
        minimumConfidenceThreshold = 0.75f,
        allowAmbiguousCorrection = true,
        safeguardStrictness = 0.50f
    )

    val WIDE_LABEL_PROFILE = CalibrationProfile(
        name = "WIDE_LABEL",
        maxEditDistance = 2,
        minimumConfidenceThreshold = 0.78f,
        allowAmbiguousCorrection = false,
        safeguardStrictness = 0.55f
    )

    val LOW_CONFIDENCE_PROFILE = CalibrationProfile(
        name = "LOW_CONFIDENCE",
        maxEditDistance = 1,
        minimumConfidenceThreshold = 0.85f,
        allowAmbiguousCorrection = false,
        safeguardStrictness = 0.85f // prefer raw/ambiguous output when OCR is untrusted
    )

    val ADDITIVE_HEAVY_PROFILE = CalibrationProfile(
        name = "ADDITIVE_HEAVY",
        maxEditDistance = 2,
        minimumConfidenceThreshold = 0.80f,
        allowAmbiguousCorrection = true,
        safeguardStrictness = 0.50f
    )

    /**
     * Normalizes raw metrics into 0.0 -> 1.0 ranges.
     */
    fun normalizeMetrics(
        blurScore: Float,
        brightnessScore: Float,
        contrastScore: Float
    ): NormalizedVisualMetrics {
        return NormalizedVisualMetrics(
            normalizedBlur = (blurScore / 30f).coerceIn(0f, 1f),
            normalizedBrightness = (brightnessScore / 255f).coerceIn(0f, 1f),
            normalizedContrast = (contrastScore / 80f).coerceIn(0f, 1f)
        )
    }

    /**
     * Determines the correction calibration profile based on raw visual metrics and context.
     */
    fun calibrate(
        ocrConfidence: Float,
        blurScore: Float,
        contrastScore: Float,
        brightnessScore: Float,
        additiveRatio: Float
    ): CalibrationProfile {
        val norm = normalizeMetrics(blurScore, brightnessScore, contrastScore)

        return when {
            // 1. Very low raw OCR confidence -> strict low confidence profile
            ocrConfidence < 0.6f -> LOW_CONFIDENCE_PROFILE
            
            // 2. High density of additives detected
            additiveRatio > 0.20f -> ADDITIVE_HEAVY_PROFILE

            // 3. Low normalized blur indicates a blurry image
            norm.normalizedBlur < 0.2f -> BLURRY_PROFILE

            // 4. Low normalized brightness indicates a dim low-light image
            norm.normalizedBrightness < 0.3f -> LOW_LIGHT_PROFILE

            // 5. Default fallback
            else -> DEFAULT_PROFILE
        }
    }
}
