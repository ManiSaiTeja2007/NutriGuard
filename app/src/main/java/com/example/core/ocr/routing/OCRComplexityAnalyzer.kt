package com.example.core.ocr.routing

import android.graphics.Bitmap
import kotlin.math.sqrt

object OCRComplexityAnalyzer {

    data class AnalysisMetrics(
        val brightness: Float,
        val contrast: Float,
        val blurScore: Float,
        val estimatedTextDensity: Float,
        val complexityRating: String
    )

    /**
     * Runs a fast, low-resolution visual check on a downsampled version of the source image.
     */
    fun analyze(bitmap: Bitmap): AnalysisMetrics {
        val width = bitmap.width
        val height = bitmap.height
        
        // 1. Scaled thumbnail downsampling for low latency
        val maxSide = 128
        val scale = if (width > height) {
            maxSide.toFloat() / width
        } else {
            maxSide.toFloat() / height
        }.coerceAtMost(1.0f)

        val sw = (width * scale).toInt().coerceAtLeast(32)
        val sh = (height * scale).toInt().coerceAtLeast(32)
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, sw, sh, false)
        val pixels = IntArray(sw * sh)
        scaledBitmap.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        // 2. Luminance calculation
        val luma = FloatArray(sw * sh)
        var sumLuma = 0f
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            luma[i] = l
            sumLuma += l
        }
        val meanLuma = sumLuma / pixels.size

        // 3. Contrast computation (luminance standard deviation)
        var sumVariance = 0f
        for (i in luma.indices) {
            val diff = luma[i] - meanLuma
            sumVariance += diff * diff
        }
        val contrast = sqrt(sumVariance / luma.size)

        // 4. Sobel edge detection & blur score (standard deviation of gradient)
        val gradients = FloatArray(sw * sh)
        var sumGrad = 0f
        var edgePixelsCount = 0
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                val gx = (luma[idx + 1] - luma[idx - 1])
                val gy = (luma[idx + sw] - luma[idx - sw])
                val g = sqrt(gx * gx + gy * gy)
                gradients[idx] = g
                sumGrad += g
                
                // Estimate text density via high-gradient pixels
                if (g > 20f) {
                    edgePixelsCount++
                }
            }
        }
        val meanGrad = sumGrad / (sw * sh)

        var gradVarSum = 0f
        for (i in gradients.indices) {
            val diff = gradients[i] - meanGrad
            gradVarSum += diff * diff
        }
        val blurScore = sqrt(gradVarSum / gradients.size)
        val estimatedTextDensity = edgePixelsCount.toFloat() / (sw * sh)

        // 5. Categorize complexity
        val complexityRating = when {
            blurScore < 4.0f || contrast < 15.0f -> "HIGH"
            estimatedTextDensity > 0.25f || (sw.toFloat() / sh) > 3.0f || (sh.toFloat() / sw) > 3.0f -> "HIGH"
            blurScore < 8.0f || contrast < 25.0f || estimatedTextDensity > 0.15f -> "MEDIUM"
            else -> "LOW"
        }

        return AnalysisMetrics(
            brightness = meanLuma,
            contrast = contrast,
            blurScore = blurScore,
            estimatedTextDensity = estimatedTextDensity,
            complexityRating = complexityRating
        )
    }
}
