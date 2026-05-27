package com.example.verification.accessibility

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.core.ui_validation.SemanticsAuditor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAccessibilityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAuditHomeScreenAccessibility() {
        composeTestRule.waitForIdle()

        // Fetch Root Semantics Node
        val rootNode = composeTestRule.onRoot().fetchSemanticsNode()
        val report = SemanticsAuditor.audit(rootNode)

        val issueDetails = report.issues.joinToString("\n") { 
            "  [${it.severity}] ${it.ruleName} - ${it.message} (Tag: ${it.nodeTag})" 
        }
        assertTrue("Home Screen visual trust score (${report.visualTrustScore}) must be >= 80.\nIssues:\n$issueDetails", report.visualTrustScore >= 80)
    }

    @Test
    fun testAuditScanScreenAccessibility() {
        composeTestRule.waitForIdle()

        // Navigate to Scan Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_scan_product").performClick()
        composeTestRule.waitForIdle()

        // Fetch and audit Scan Screen
        val rootNode = composeTestRule.onRoot().fetchSemanticsNode()
        val report = SemanticsAuditor.audit(rootNode)

        val issueDetails = report.issues.joinToString("\n") { 
            "  [${it.severity}] ${it.ruleName} - ${it.message} (Tag: ${it.nodeTag})" 
        }
        assertTrue("Scan Screen visual trust score (${report.visualTrustScore}) must be >= 75.\nIssues:\n$issueDetails", report.visualTrustScore >= 75)
    }

    @Test
    fun testAuditSettingsScreenAccessibility() {
        composeTestRule.waitForIdle()

        // Navigate to Settings Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_settings").performClick()
        composeTestRule.waitForIdle()

        // Fetch and audit Settings Screen
        val rootNode = composeTestRule.onRoot().fetchSemanticsNode()
        val report = SemanticsAuditor.audit(rootNode)

        val issueDetails = report.issues.joinToString("\n") { 
            "  [${it.severity}] ${it.ruleName} - ${it.message} (Tag: ${it.nodeTag})" 
        }
        assertTrue("Settings Screen visual trust score (${report.visualTrustScore}) must be >= 80.\nIssues:\n$issueDetails", report.visualTrustScore >= 80)
    }

    @Test
    fun testAuditAboutScreenAccessibility() {
        composeTestRule.waitForIdle()

        // Navigate to About Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_about_app").performClick()
        composeTestRule.waitForIdle()

        // Fetch and audit About Screen
        val rootNode = composeTestRule.onRoot().fetchSemanticsNode()
        val report = SemanticsAuditor.audit(rootNode)

        val issueDetails = report.issues.joinToString("\n") { 
            "  [${it.severity}] ${it.ruleName} - ${it.message} (Tag: ${it.nodeTag})" 
        }
        assertTrue("About Screen visual trust score (${report.visualTrustScore}) must be >= 80.\nIssues:\n$issueDetails", report.visualTrustScore >= 80)
    }
}
