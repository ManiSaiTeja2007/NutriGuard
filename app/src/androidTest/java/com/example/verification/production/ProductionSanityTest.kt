package com.example.verification.production

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.core.config.BuildCapabilities
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionSanityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyNoDeveloperToolsInProduction() {
        composeTestRule.waitForIdle()

        // 1. Open drawer
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        if (!BuildCapabilities.isDeveloperBuild) {
            composeTestRule.onNodeWithTag("drawer_dev_console").assertDoesNotExist()
            composeTestRule.onNodeWithTag("drawer_session_exporter").assertDoesNotExist()
        } else {
            composeTestRule.onNodeWithTag("drawer_dev_console").assertExists()
        }
    }
}
