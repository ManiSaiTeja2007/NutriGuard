package com.example.verification.exports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.core.config.BuildCapabilities
import com.example.verification.ScreenshotFailureWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val failureWatcher = ScreenshotFailureWatcher(composeTestRule) { "export_test" }

    @Test
    fun testContextualExportWorkflow() {
        composeTestRule.waitForIdle()

        // 1. Go to Scan Screen via Drawer
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drawer_scan_product").performClick()
        composeTestRule.waitForIdle()

        // 2. Ingest scan (wait for test image to load and OCR to finish if in test image mode)
        // Since test images might take a moment to run OCR:
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule
                .onAllNodesWithTag("scan_ingest_button")
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }

        composeTestRule.onNodeWithTag("scan_ingest_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Results Screen is displayed (wait for pipeline processing)
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule
                .onAllNodesWithTag("results_screen")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("results_screen").assertExists()

        // 4. If developer build, verify and click Developer Tools
        if (BuildCapabilities.isDeveloperBuild) {
            val devToolsExpandBtn = composeTestRule.onNodeWithTag("developer_tools_expand")
            devToolsExpandBtn.assertExists()
            devToolsExpandBtn.performClick()
            composeTestRule.waitForIdle()

            // Click Export Session button
            val exportSessionBtn = composeTestRule.onNodeWithTag("export_session_button")
            exportSessionBtn.assertExists()
            exportSessionBtn.performClick()
            composeTestRule.waitForIdle()

            // Wait for export success dialog to show
            composeTestRule.onNodeWithText("Export Complete").assertExists()

            // Click close dialog button
            composeTestRule.onNodeWithTag("export_close_dialog_button").performClick()
            composeTestRule.waitForIdle()
        }
    }
}
