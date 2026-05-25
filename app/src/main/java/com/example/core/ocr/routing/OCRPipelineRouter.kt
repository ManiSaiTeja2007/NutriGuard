package com.example.core.ocr.routing

object OCRPipelineRouter {

    enum class OcrStrategy {
        STANDARD,
        TILED,
        SHARPENED,
        THRESHOLDED,
        LOW_LIGHT,
        UPSCALE
    }

    /**
     * Determines the optimal OCR strategy based on visual metrics.
     */
    fun route(
        width: Int,
        height: Int,
        metrics: OCRComplexityAnalyzer.AnalysisMetrics
    ): OcrStrategy {
        val aspectRatio = if (height > 0) width.toFloat() / height else 1.0f

        return when {
            // 1. Tiny images need to be upscaled to prevent ML Kit minimum sizing errors (32x32)
            width < 128 || height < 128 -> OcrStrategy.UPSCALE
            
            // 2. Wide labels need horizontal slicing (tiled OCR) to maintain line horizontal alignment
            aspectRatio > 3.0f || width > 1600 -> OcrStrategy.TILED
            
            // 3. Low Sobel gradient variance indicates blurry text -> Route to SHARPENED
            metrics.blurScore < 6.0f -> OcrStrategy.SHARPENED
            
            // 4. Low average luminance indicates dim lighting -> Route to LOW_LIGHT
            metrics.brightness < 80f -> OcrStrategy.LOW_LIGHT
            
            // 5. Low standard deviation of luminance indicates washed-out colors -> Route to THRESHOLDED
            metrics.contrast < 22f -> OcrStrategy.THRESHOLDED
            
            // 6. Good lighting, high contrast, sharp labels -> Route to standard processing
            else -> OcrStrategy.STANDARD
        }
    }
}
