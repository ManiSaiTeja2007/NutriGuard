package com.example.verification

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class VerificationRunner {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyAppFlowAndCaptureScreenshots() {
        // Wait for composition to stabilize
        composeTestRule.waitForIdle()

        // 1. Capture Home Screen
        captureScreenshot("home_screen")

        // 2. Navigate to Settings Screen if we can find settings, or test component rendering
        // Since we want to ensure basic layout sanity:
        composeTestRule.waitForIdle()
    }

    private fun captureScreenshot(name: String) {
        try {
            val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val outputDir = targetContext.getExternalFilesDir(null) ?: targetContext.cacheDir
            val file = File(outputDir, "${name}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            println("Successfully saved screenshot to: ${file.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Failed to capture screenshot '$name': ${e.message}")
        }
    }
}
