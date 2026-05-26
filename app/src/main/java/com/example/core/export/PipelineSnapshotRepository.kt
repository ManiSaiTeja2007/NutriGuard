package com.example.core.export

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

object PipelineSnapshotRepository {

    private val latestSnapshot = AtomicReference<PipelineSnapshot?>()

    fun update(snapshot: PipelineSnapshot) {
        latestSnapshot.set(snapshot)
    }

    fun latest(): PipelineSnapshot? {
        return latestSnapshot.get()
    }

    /**
     * Helper to write a temporary bitmap to the app's cache directory
     * to avoid keeping long-lived memory references that cause memory leaks.
     */
    fun saveTempBitmap(context: Context, bitmap: Bitmap, suffix: String): String? {
        return try {
            val cacheDir = context.cacheDir
            val tempFile = File(cacheDir, "temp_snapshot_$suffix.png")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
