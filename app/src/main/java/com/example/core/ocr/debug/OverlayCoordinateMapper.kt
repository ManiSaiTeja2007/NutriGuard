package com.example.core.ocr.debug

import android.graphics.Rect
import androidx.compose.ui.geometry.Rect as ComposeRect

object OverlayCoordinateMapper {

    /**
     * Maps a Rect in raw image coordinate space to screen coordinate space,
     * taking fit-scale, container dimensions, zoom scale, and panning offsets into account.
     */
    fun mapRect(
        rawRect: Rect,
        srcWidth: Float,
        srcHeight: Float,
        containerWidth: Float,
        containerHeight: Float,
        zoomScale: Float,
        panX: Float,
        panY: Float
    ): ComposeRect {
        if (srcWidth <= 0 || srcHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return ComposeRect(0f, 0f, 0f, 0f)
        }

        // 1. Calculate fit scale and offsets (fits image within container constraints)
        val bitmapRatio = srcWidth / srcHeight
        val containerRatio = containerWidth / containerHeight

        val fitScale = if (bitmapRatio > containerRatio) {
            containerWidth / srcWidth
        } else {
            containerHeight / srcHeight
        }

        val actualW = srcWidth * fitScale
        val actualH = srcHeight * fitScale

        val offsetX = (containerWidth - actualW) / 2f
        val offsetY = (containerHeight - actualH) / 2f

        // 2. Map raw coordinates to intermediate fit-coordinates
        val fitLeft = offsetX + rawRect.left * fitScale
        val fitTop = offsetY + rawRect.top * fitScale
        val fitRight = offsetX + rawRect.right * fitScale
        val fitBottom = offsetY + rawRect.bottom * fitScale

        // 3. Apply zoom & pan transformations relative to container center (matching Compose pivot behavior)
        val centerX = containerWidth / 2f
        val centerY = containerHeight / 2f

        val screenLeft = (fitLeft - centerX) * zoomScale + centerX + panX
        val screenTop = (fitTop - centerY) * zoomScale + centerY + panY
        val screenRight = (fitRight - centerX) * zoomScale + centerX + panX
        val screenBottom = (fitBottom - centerY) * zoomScale + centerY + panY

        return ComposeRect(screenLeft, screenTop, screenRight, screenBottom)
    }
}
