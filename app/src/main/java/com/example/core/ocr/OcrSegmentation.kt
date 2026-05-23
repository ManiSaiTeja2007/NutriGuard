package com.example.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF

object OcrSegmentation {
    private const val MIN_OCR_SIZE_PX = 32

    fun segment(bitmap: Bitmap): List<OcrSegment> {
        val width = bitmap.width
        val height = bitmap.height

        // If the bitmap is extremely small in both dimensions, pad it to the minimum OCR size (32x32)
        if (width < MIN_OCR_SIZE_PX && height < MIN_OCR_SIZE_PX) {
            return listOf(toPaddedPiece(bitmap, 0, 0, width, height))
        }

        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height).coerceAtLeast(1)
        val aspectRatio = longSide.toFloat() / shortSide

        // Implement adaptive segmentation thresholds
        val shouldSegment = when {
            // Narrow labels that are below ML Kit's minimum size on one dimension must be segmented/padded
            width < MIN_OCR_SIZE_PX || height < MIN_OCR_SIZE_PX -> true
            // Small/normal labels under 1200px width do not get tiled
            width < 1200 -> false
            // High aspect ratio labels get tiled
            aspectRatio > 3f -> true
            // Very large labels get tiled
            longSide > 2000 -> true
            else -> false
        }

        if (!shouldSegment) {
            // Full image OCR (returns 1 segment, no recycle required for the source bitmap)
            return listOf(OcrSegment(bitmap = bitmap, recycleAfterUse = false))
        }

        // Determine adaptive tile sizes and overlaps
        val tileLongSide = when {
            longSide > 2000 -> 512
            else -> 256
        }
        val overlapPx = (tileLongSide * 0.15f).toInt().coerceAtLeast(24)
        val step = (tileLongSide - overlapPx).coerceAtLeast(MIN_OCR_SIZE_PX)

        val pieces = mutableListOf<OcrSegment>()
        var start = 0
        try {
            while (start < longSide) {
                val end = minOf(start + tileLongSide, longSide)
                val chunkLength = end - start

                if (width >= height) {
                    pieces += toPaddedPiece(bitmap, start, 0, chunkLength, height)
                } else {
                    pieces += toPaddedPiece(bitmap, 0, start, width, chunkLength)
                }

                if (end == longSide) break
                start += step
            }
        } catch (error: Throwable) {
            // Memory safety: Recycle all created segment bitmaps if allocation fails mid-loop
            pieces.forEach { segment ->
                if (segment.recycleAfterUse && !segment.bitmap.isRecycled) {
                    segment.bitmap.recycle()
                }
            }
            throw error
        }

        return pieces
    }

    private fun toPaddedPiece(
        source: Bitmap,
        left: Int,
        top: Int,
        cropWidth: Int,
        cropHeight: Int
    ): OcrSegment {
        val outputWidth = cropWidth.coerceAtLeast(MIN_OCR_SIZE_PX)
        val outputHeight = cropHeight.coerceAtLeast(MIN_OCR_SIZE_PX)
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val destinationLeft = ((outputWidth - cropWidth) / 2f).coerceAtLeast(0f)
        val destinationTop = ((outputHeight - cropHeight) / 2f).coerceAtLeast(0f)
        canvas.drawBitmap(
            source,
            Rect(left, top, left + cropWidth, top + cropHeight),
            RectF(
                destinationLeft,
                destinationTop,
                destinationLeft + cropWidth,
                destinationTop + cropHeight
            ),
            null
        )

        return OcrSegment(bitmap = output, recycleAfterUse = true)
    }
}
