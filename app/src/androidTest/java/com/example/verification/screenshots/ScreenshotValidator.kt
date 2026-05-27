package com.example.verification.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.config.BuildCapabilities
import com.example.data.AppSettings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ScreenshotValidator {

    /**
     * Captures a screenshot from the given semantics node interaction, and writes
     * both the image and its descriptive metadata JSON to the instrumentation directory.
     */
    fun capture(
        nodeInteraction: SemanticsNodeInteraction,
        name: String,
        screenLabel: String
    ) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = File(targetContext.getExternalFilesDir(null), "verification/screenshots")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // 1. Save Screenshot PNG
        val pngFile = File(outputDir, "${name}.png")
        try {
            val bitmap = nodeInteraction.captureToImage().asAndroidBitmap()
            FileOutputStream(pngFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            println("Saved verification screenshot to: ${pngFile.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Failed to save screenshot '$name': ${e.message}")
        }

        // 2. Save Metadata JSON
        val jsonFile = File(outputDir, "${name}_metadata.json")
        try {
            val metadata = JSONObject().apply {
                put("theme", AppSettings.themePreference.name.lowercase())
                put("highContrast", AppSettings.highContrastEnabled)
                put("largerText", AppSettings.largerTextEnabled)
                put("screen", screenLabel)
                put("buildType", when {
                    BuildCapabilities.isDeveloperBuild -> "developer"
                    BuildCapabilities.isProductionBuild -> "production"
                    BuildCapabilities.isBenchmarkBuild -> "benchmark"
                    BuildCapabilities.isInternalBuild -> "internal"
                    else -> "unknown"
                })
                put("timestamp", System.currentTimeMillis())
            }
            FileOutputStream(jsonFile).use { out ->
                out.write(metadata.toString(4).toByteArray())
            }
            println("Saved verification screenshot metadata to: ${jsonFile.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Failed to save screenshot metadata '$name': ${e.message}")
        }
    }
}
