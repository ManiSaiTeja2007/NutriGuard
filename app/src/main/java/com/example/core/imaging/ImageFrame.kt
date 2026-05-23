package com.example.core.imaging

import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

enum class ImageSource {
    CAMERA_X,
    TEST_ASSET
}

/**
 * Canonical image frame abstraction.
 *
 * IMPORTANT LIFECYCLE RULE:
 * CameraXFrame is ONLY valid until [ImageProxy.close] is called on the underlying proxy.
 * Do not store CameraXFrame or pass it outside the current frame analysis callback context.
 */
sealed class ImageFrame {
    abstract val width: Int
    abstract val height: Int
    abstract val rotationDegrees: Int
    abstract val timestampNanos: Long
    abstract val source: ImageSource

    abstract fun toInputImage(): InputImage

    data class BitmapFrame(
        val bitmap: Bitmap,
        override val rotationDegrees: Int,
        override val timestampNanos: Long,
        override val source: ImageSource = ImageSource.TEST_ASSET
    ) : ImageFrame() {
        override val width: Int get() = bitmap.width
        override val height: Int get() = bitmap.height
        override fun toInputImage(): InputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
    }

    data class CameraXFrame(
        val imageProxy: ImageProxy
    ) : ImageFrame() {
        override val width: Int get() = imageProxy.width
        override val height: Int get() = imageProxy.height
        override val rotationDegrees: Int get() = imageProxy.imageInfo.rotationDegrees
        override val timestampNanos: Long get() = imageProxy.imageInfo.timestamp
        override val source: ImageSource get() = ImageSource.CAMERA_X
        
        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun toInputImage(): InputImage {
            val mediaImage = checkNotNull(imageProxy.image) { "CameraX ImageProxy has no mediaImage" }
            return InputImage.fromMediaImage(mediaImage, rotationDegrees)
        }
    }
}
