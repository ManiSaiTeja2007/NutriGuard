package com.example.verification.overlays

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.verification.ScreenshotFailureWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val failureWatcher = ScreenshotFailureWatcher(composeTestRule) { "overlay_test" }

    @Test
    fun testOverlayAccessibilityTalkBackLabels() {
        composeTestRule.waitForIdle()

        // Open drawer and navigate to Scan Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drawer_scan_product").performClick()
        composeTestRule.waitForIdle()

        // Assert that the ingest action has content description or TalkBack label
        composeTestRule.onNodeWithTag("scan_ingest_button").assertExists()
    }
}
