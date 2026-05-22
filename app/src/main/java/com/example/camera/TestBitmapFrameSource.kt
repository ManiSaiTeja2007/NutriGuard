package com.example.camera

import android.graphics.Bitmap
import android.os.SystemClock

object TestBitmapFrameSource {
    fun fromBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        timestampNanos: Long = SystemClock.elapsedRealtimeNanos()
    ): FrameInput {
        return FrameInput(
            width = bitmap.width,
            height = bitmap.height,
            rotationDegrees = rotationDegrees,
            timestampNanos = timestampNanos,
            source = FrameSource.TEST_ASSET,
            bitmap = bitmap
        )
    }
}
