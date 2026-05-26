package com.example.verification.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.verification.ScreenshotFailureWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val failureWatcher = ScreenshotFailureWatcher(composeTestRule) { "settings_test" }

    @Test
    fun testSettingsToggles() {
        composeTestRule.waitForIdle()

        // Open drawer and navigate to Settings Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drawer_settings").performClick()
        composeTestRule.waitForIdle()

        // Verify we are on Settings screen
        composeTestRule.onNodeWithTag("settings_screen").assertExists()

        // Toggle Adaptive OCR switch
        val adaptiveOcrSwitch = composeTestRule.onNodeWithTag("setting_toggle_adaptive_ocr_engine")
        adaptiveOcrSwitch.assertExists()
        
        adaptiveOcrSwitch.performClick()
        composeTestRule.waitForIdle()
    }
}
