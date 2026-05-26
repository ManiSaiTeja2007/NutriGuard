package com.example.verification.workflows

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.verification.ScreenshotFailureWatcher
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val failureWatcher = ScreenshotFailureWatcher(composeTestRule) { "workflow_test" }

    @Test
    fun testScanToResultsWorkflow() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Scan Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drawer_scan_product").performClick()
        composeTestRule.waitForIdle()

        // 2. Wait until ingestion button is active and click it
        // OCR pipeline on AVD can take up to 20s to process the test image asset.
        composeTestRule.waitUntil(timeoutMillis = 20000) {
            composeTestRule
                .onAllNodesWithTag("scan_ingest_button")
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        // Scroll into view before clicking — button is inside a verticalScroll Column.
        composeTestRule.onNodeWithTag("scan_ingest_button").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("scan_ingest_button").performClick()
        composeTestRule.waitForIdle()

        // Confirm the click registered: isIngesting=true causes button to become disabled.
        // If the button is STILL enabled after clicking, the click didn't reach the handler.
        val clickRegistered = composeTestRule
            .onAllNodesWithTag("scan_ingest_button")
            .fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.Disabled) != null }
        if (!clickRegistered) {
            // Retry once — button may have flipped back immediately if ingest returned early
            android.util.Log.w("WorkflowTests", "Click may not have registered (button still enabled after click). Retrying.")
            composeTestRule.onNodeWithTag("scan_ingest_button").performScrollTo()
            composeTestRule.onNodeWithTag("scan_ingest_button").performClick()
            composeTestRule.waitForIdle()
        }

        // 3. Wait for pipeline to complete by manually pumping the main Looper.
        //    waitUntil in v1 does not yield to the Android Looper between polls, which
        //    means withContext(Dispatchers.Main) in processAndNavigate gets starved.
        //    Alternating Thread.sleep + waitForIdle() fixes this.
        val deadlineMs = System.currentTimeMillis() + 30_000L
        var resolved = false
        var pipelineError: String? = null

        while (System.currentTimeMillis() < deadlineMs) {
            composeTestRule.waitForIdle()

            val resultsVisible = composeTestRule
                .onAllNodesWithTag("results_screen")
                .fetchSemanticsNodes()
                .isNotEmpty()

            val errorNode = composeTestRule
                .onAllNodesWithTag("scan_ingest_error_label")
                .fetchSemanticsNodes()
                .firstOrNull()

            if (resultsVisible) {
                resolved = true
                break
            }
            if (errorNode != null) {
                pipelineError = errorNode.config.getOrNull(SemanticsProperties.Text)
                    ?.joinToString { it.text } ?: "Unknown pipeline error"
                break
            }

            Thread.sleep(500)
        }

        if (pipelineError != null) {
            fail("Ingestion pipeline failed — ResultsScreen never shown. Error: $pipelineError")
        }
        if (!resolved) {
            fail("Timed out (30s) waiting for results_screen after ingestion. Neither results_screen nor scan_ingest_error_label appeared.")
        }

        composeTestRule.onNodeWithTag("results_screen").assertExists()

        // 4. Go back to Home
        composeTestRule.onNodeWithTag("results_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("home_screen")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }
}
