package com.example.camera

import android.graphics.Bitmap

data class FrameInput(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNanos: Long,
    val source: FrameSource,
    val bitmap: Bitmap? = null
)

enum class FrameSource {
    CAMERA_X,
    TEST_ASSET
}
