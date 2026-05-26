package com.example.verification.screenshots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.verification.ScreenshotFailureWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ScreenshotTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val failureWatcher = ScreenshotFailureWatcher(composeTestRule) { "screenshot_test" }

    @Test
    fun testCaptureHomeAndSettingsScreenshots() {
        composeTestRule.waitForIdle()

        // Capture Home Screen
        captureScreenshot("home_screen_render")

        // Go to Settings Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drawer_settings").performClick()
        composeTestRule.waitForIdle()

        // Capture Settings Screen
        captureScreenshot("settings_screen_render")
    }

    private fun captureScreenshot(name: String) {
        try {
            val bitmap = composeTestRule.onRoot().captureToImage().asAndroidBitmap()
            val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
            val outputDir = File(targetContext.getExternalFilesDir(null), "verification/screenshots")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val file = File(outputDir, "${name}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            println("Successfully saved test rendering screenshot to: ${file.absolutePath}")
        } catch (e: Exception) {
            System.err.println("Failed to capture screenshot '$name': ${e.message}")
        }
    }
}
