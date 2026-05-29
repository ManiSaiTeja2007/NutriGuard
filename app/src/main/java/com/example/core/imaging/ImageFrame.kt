package com.example.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream

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

    fun toNormalisedBitmap(): Bitmap? {
        return when (this) {
            is BitmapFrame -> {
                rotateBitmap(this.bitmap, this.rotationDegrees)
            }
            is CameraXFrame -> {
                val bitmap = this.imageProxy.toBitmapCompat() ?: return null
                rotateBitmap(bitmap, this.rotationDegrees)
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun androidx.camera.core.ImageProxy.toBitmapCompat(): Bitmap? {
        val image = this.image ?: return null
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun yuv420ToNv21(image: android.media.Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val yPixelStride = image.planes[0].pixelStride

        val uRowStride = image.planes[1].rowStride
        val uPixelStride = image.planes[1].pixelStride

        val vRowStride = image.planes[2].rowStride
        val vPixelStride = image.planes[2].pixelStride

        var pos = 0
        if (yRowStride == width && yPixelStride == 1) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get()
                }
            }
        }

        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            val uRowStart = row * uRowStride
            val vRowStart = row * vRowStride
            for (col in 0 until uvWidth) {
                val uVal = uBuffer.get(uRowStart + col * uPixelStride)
                val vVal = vBuffer.get(vRowStart + col * vPixelStride)
                nv21[pos++] = vVal
                nv21[pos++] = uVal
            }
        }
        return nv21
    }
}
