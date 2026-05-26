package com.example.verification

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream

class ScreenshotFailureWatcher(
    private val composeTestRule: ComposeTestRule,
    private val executionIdProvider: () -> String = { "test" }
) : TestWatcher() {

    override fun failed(e: Throwable?, description: Description?) {
        val testName = description?.methodName ?: "unknown_test"
        val className = description?.className?.substringAfterLast('.') ?: "unknown_class"
        val executionId = executionIdProvider()
        val timestamp = System.currentTimeMillis()
        val screenshotName = "${executionId}_${timestamp}"
        
        try {
            val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val outputDir = File(targetContext.getExternalFilesDir(null), "verification/failures")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val file = File(outputDir, "${screenshotName}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            println("Saved test failure screenshot to: ${file.absolutePath}")
        } catch (ex: Exception) {
            System.err.println("Failed to capture failure screenshot: ${ex.message}")
        }
    }
}
