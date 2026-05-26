package com.example.verification.workflows

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.verification.ScreenshotFailureWatcher
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
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule
                .onAllNodesWithTag("scan_ingest_button")
                .fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Disabled) == null }
        }
        composeTestRule.onNodeWithTag("scan_ingest_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Results screen is shown (wait for pipeline processing)
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule
                .onAllNodesWithTag("results_screen")
                .fetchSemanticsNodes()
                .isNotEmpty()
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
