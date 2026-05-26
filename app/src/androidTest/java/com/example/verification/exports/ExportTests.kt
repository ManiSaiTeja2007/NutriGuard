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

        // 2. Ingest scan: wait up to 20s for OCR to complete and button to enable on AVD.
        composeTestRule.waitUntil(timeoutMillis = 20000) {
            composeTestRule
                .onAllNodesWithTag("scan_ingest_button")
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }

        // Scroll into view — button is inside a verticalScroll Column and may be below the fold.
        composeTestRule.onNodeWithTag("scan_ingest_button").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scan_ingest_button").performClick()

        // 3. Wait for pipeline to complete. Use Thread.sleep+waitForIdle loop to pump the
        //    Android Looper so withContext(Dispatchers.Main) in processAndNavigate can execute.
        val deadlineMs = System.currentTimeMillis() + 30_000L
        var resultsVisible = false
        while (System.currentTimeMillis() < deadlineMs) {
            composeTestRule.waitForIdle()
            resultsVisible = composeTestRule
                .onAllNodesWithTag("results_screen")
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (resultsVisible) break
            Thread.sleep(500)
        }
        org.junit.Assert.assertTrue("Timed out waiting for results_screen", resultsVisible)
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
