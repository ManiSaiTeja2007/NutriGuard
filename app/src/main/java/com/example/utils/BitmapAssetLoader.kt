package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream

object BitmapAssetLoader {
    fun load(context: Context, assetPath: String): Bitmap {
        return loadWithMetadata(context, assetPath).bitmap
    }

    fun loadWithMetadata(context: Context, assetPath: String): LoadedBitmapAsset {
        val bytes = context.assets.open(assetPath).use { input ->
            input.readBytes()
        }

        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Unable to decode bitmap asset: $assetPath"
        }
        val rotationDegrees = readExifRotation(bytes)

        return LoadedBitmapAsset(
            assetPath = assetPath,
            fileName = assetPath.substringAfterLast('/'),
            bitmap = bitmap,
            rotationDegrees = rotationDegrees
        )
    }

    private fun readExifRotation(bytes: ByteArray): Int {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}

data class LoadedBitmapAsset(
    val assetPath: String,
    val fileName: String,
    val bitmap: Bitmap,
    val rotationDegrees: Int
)
