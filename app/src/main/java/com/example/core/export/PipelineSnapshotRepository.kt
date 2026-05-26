package com.example.core.export

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

object PipelineSnapshotRepository {

    private val history = Collections.synchronizedList(mutableListOf<PipelineSnapshot>())
    private const val MAX_HISTORY = 15

    fun add(snapshot: PipelineSnapshot) {
        history.add(0, snapshot) // Insert at beginning (newest first)
        pruneHistory()
    }

    fun update(snapshot: PipelineSnapshot) {
        add(snapshot)
    }

    fun latest(): PipelineSnapshot? {
        return history.firstOrNull()
    }

    fun get(executionId: String): PipelineSnapshot? {
        return history.find { it.executionId == executionId }
    }

    fun getHistory(): List<PipelineSnapshot> {
        return ArrayList(history)
    }

    fun clear() {
        history.forEach { snapshot ->
            snapshot.rawImagePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            snapshot.preprocessedImagePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
        }
        history.clear()
    }

    private fun pruneHistory() {
        while (history.size > MAX_HISTORY) {
            val removed = history.removeAt(history.size - 1)
            removed.rawImagePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            removed.preprocessedImagePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
        }
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

    /**
     * Renames static preview images to unique, session-scoped names.
     */
    fun renameTempFiles(context: Context, executionId: String): Pair<String?, String?> {
        val cacheDir = context.cacheDir
        val rawTemp = File(cacheDir, "temp_snapshot_raw.png")
        val prepTemp = File(cacheDir, "temp_snapshot_prep.png")

        val rawDest = File(cacheDir, "temp_snapshot_${executionId}_raw.png")
        val prepDest = File(cacheDir, "temp_snapshot_${executionId}_prep.png")

        val rawPath = if (rawTemp.exists()) {
            if (rawTemp.renameTo(rawDest)) rawDest.absolutePath else rawTemp.absolutePath
        } else {
            null
        }

        val prepPath = if (prepTemp.exists()) {
            if (prepTemp.renameTo(prepDest)) prepDest.absolutePath else prepTemp.absolutePath
        } else {
            null
        }

        return Pair(rawPath, prepPath)
    }
}
