package com.example.verification.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.verification.ScreenshotFailureWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val failureWatcher = ScreenshotFailureWatcher(composeTestRule) { "nav_test" }

    @Test
    fun testDrawerNavigationAndBackBehavior() {
        composeTestRule.waitForIdle()

        // 1. Verify Home Screen is initially displayed
        composeTestRule.onNodeWithTag("home_screen").assertExists()

        // 2. Open drawer and navigate to Settings Screen
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        // Click "Settings" drawer item
        composeTestRule.onNodeWithTag("drawer_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertExists()

        // Navigate back using onBackPressedDispatcher
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()

        // Assert we are back on Home Screen
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }

    @Test
    fun testAboutScreenNavigation() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("drawer_about_app").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_screen").assertExists()

        // Navigate back using onBackPressedDispatcher
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }
}
