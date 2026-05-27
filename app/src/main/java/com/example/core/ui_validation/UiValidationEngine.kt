package com.example.core.ui_validation

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

object UiValidationEngine {
    /**
     * Computes the relative luminance of a Compose Color.
     * Standard formula from W3C WCAG 2.0.
     */
    fun calculateRelativeLuminance(color: Color): Double {
        val rSRGB = color.red.toDouble()
        val gSRGB = color.green.toDouble()
        val bSRGB = color.blue.toDouble()

        val r = if (rSRGB <= 0.03928) rSRGB / 12.92 else ((rSRGB + 0.055) / 1.055).pow(2.4)
        val g = if (gSRGB <= 0.03928) gSRGB / 12.92 else ((gSRGB + 0.055) / 1.055).pow(2.4)
        val b = if (bSRGB <= 0.03928) bSRGB / 12.92 else ((bSRGB + 0.055) / 1.055).pow(2.4)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Computes contrast ratio between two colors.
     * Returns ratio as a value between 1.0 and 21.0.
     */
    fun calculateContrastRatio(color1: Color, color2: Color): Double {
        val lum1 = calculateRelativeLuminance(color1)
        val lum2 = calculateRelativeLuminance(color2)
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Checks if a contrast ratio satisfies the standard (4.5 for normal text, 3.0 for large/bold text).
     */
    fun satisfiesContrast(color1: Color, color2: Color, isLargeText: Boolean = false, minRatioOverride: Double? = null): Boolean {
        val ratio = calculateContrastRatio(color1, color2)
        val threshold = minRatioOverride ?: if (isLargeText) 3.0 else 4.5
        return ratio >= threshold
    }
}
