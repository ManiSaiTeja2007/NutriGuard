package com.example.core.ocr.tiling

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.intelligence.correction.FailureType
import com.example.core.ocr.OCRWord
import com.example.core.ocr.validation.OcrInputValidator

object TiledOCRProcessor {

    /**
     * Determines tile boundaries for horizontal slicing with a 30% overlap.
     */
    fun slice(width: Int, height: Int): List<Rect> {
        if (width <= 0 || height <= 0) return emptyList()

        val numTiles = when {
            width > 2400 -> 4
            width > 1600 -> 3
            else -> 2
        }

        val overlapFraction = 0.3f
        val tileWidth = (width / (numTiles - (numTiles - 1) * overlapFraction)).toInt().coerceAtLeast(32)
        val step = (tileWidth * (1f - overlapFraction)).toInt().coerceAtLeast(1)

        val rects = mutableListOf<Rect>()
        for (i in 0 until numTiles) {
            val left = i * step
            val right = (left + tileWidth).coerceAtMost(width)
            // Ensure tile width is at least 32 pixels
            val adjustedLeft = if (right - left < 32) {
                (right - 32).coerceAtLeast(0)
            } else {
                left
            }
            rects.add(Rect(adjustedLeft, 0, right, height))
        }
        return rects
    }

    /**
     * Executes the Tiled OCR Pipeline.
     */
    suspend fun runTiledOcr(
        bitmap: Bitmap,
        ocrRunner: suspend (Bitmap) -> List<OCRWord>
    ): Pair<List<OCRWord>, List<Rect>> {
        val width = bitmap.width
        val height = bitmap.height
        val rects = slice(width, height)
        val allWords = mutableListOf<OCRWord>()

        for (rect in rects) {
            // Validate crop parameters before creating a cropped bitmap
            val cropValidation = OcrInputValidator.validateCrop(bitmap, rect)
            if (!cropValidation.isValid) {
                throw IllegalStateException(
                    "Invalid crop bounds for tile: $rect. Error: ${cropValidation.message}"
                )
            }

            var tileBitmap: Bitmap? = null
            try {
                tileBitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                val tileValidation = OcrInputValidator.validate(tileBitmap)
                if (!tileValidation.isValid) {
                    throw IllegalStateException(
                        "Invalid tile bitmap generated: ${tileValidation.message}"
                    )
                }

                val words = ocrRunner(tileBitmap)
                // Offset coordinates back to global space
                val offsetWords = words.map { word ->
                    val globalBounds = Rect(
                        word.bounds.left + rect.left,
                        word.bounds.top + rect.top,
                        word.bounds.right + rect.left,
                        word.bounds.bottom + rect.top
                    )
                    OCRWord(word.text, word.confidence, globalBounds)
                }
                allWords.addAll(offsetWords)
            } finally {
                tileBitmap?.recycle()
            }
        }

        // Deduplicate words based on Intersection-over-Union (IoU)
        val deduplicated = deduplicateWords(allWords)
        return Pair(deduplicated, rects)
    }

    private fun deduplicateWords(words: List<OCRWord>): List<OCRWord> {
        val merged = mutableListOf<OCRWord>()
        val sorted = words.sortedByDescending { it.confidence }

        for (word in sorted) {
            var hasOverlap = false
            for (existing in merged) {
                val iou = calculateIoU(word.bounds, existing.bounds)
                if (iou > 0.4f) {
                    hasOverlap = true
                    break
                }
            }
            if (!hasOverlap) {
                merged.add(word)
            }
        }
        return merged
    }

    private fun calculateIoU(rectA: Rect, rectB: Rect): Float {
        val left = maxOf(rectA.left, rectB.left)
        val top = maxOf(rectA.top, rectB.top)
        val right = minOf(rectA.right, rectB.right)
        val bottom = minOf(rectA.bottom, rectB.bottom)

        if (left >= right || top >= bottom) return 0f

        val intersectionArea = (right - left) * (bottom - top)
        val areaA = rectA.width() * rectA.height()
        val areaB = rectB.width() * rectB.height()
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea > 0) intersectionArea.toFloat() / unionArea else 0f
    }
}
