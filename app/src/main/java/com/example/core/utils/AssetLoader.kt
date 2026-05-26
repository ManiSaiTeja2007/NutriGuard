package com.example.core.utils

import android.content.Context
import java.io.File

object AssetLoader {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Loads the text content of an asset file from assets/knowledge/.
     * Supports Android runtime assets, JVM classloader resources, and JVM direct file reading.
     */
    fun loadAsset(path: String): String {
        val fullPath = "knowledge/$path"
        
        // 1. Try from Android Context assets
        appContext?.let { ctx ->
            try {
                ctx.assets.open(fullPath).use { stream ->
                    return stream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        // 2. Try JVM classloader
        try {
            val stream = javaClass.classLoader?.getResourceAsStream("assets/$fullPath") ?:
                         javaClass.classLoader?.getResourceAsStream(fullPath)
            if (stream != null) {
                return stream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            println("AssetLoader JVM classloader error: ${e.message}")
        }

        // 3. Try relative file system for JVM unit tests
        try {
            val file = File("app/src/main/assets/$fullPath")
            if (file.exists()) {
                return file.readText()
            }
            // Try parent directory lookup if running in module directory
            val fileInModule = File("src/main/assets/$fullPath")
            if (fileInModule.exists()) {
                return fileInModule.readText()
            }
        } catch (e: Exception) {
            println("AssetLoader relative file error: ${e.message}")
        }

        println("AssetLoader FAILED to locate asset: $fullPath (Cwd: ${System.getProperty("user.dir")})")
        throw IllegalStateException("Asset not found: $fullPath")
    }
}
