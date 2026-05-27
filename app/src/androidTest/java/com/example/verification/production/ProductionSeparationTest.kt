package com.example.verification.production

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.core.config.BuildCapabilities
import com.example.ui.navigation.NavController
import com.example.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionSeparationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyDrawerItemsBasedOnFlavor() {
        composeTestRule.waitForIdle()

        // Open navigation drawer
        composeTestRule.onNodeWithContentDescription("Open Menu").performClick()
        composeTestRule.waitForIdle()

        if (BuildCapabilities.isProductionBuild) {
            // In production mode, developer diagnostics and console items MUST NOT exist
            composeTestRule.onNodeWithTag("drawer_dev_console").assertDoesNotExist()
            composeTestRule.onNodeWithTag("drawer_benchmark_run").assertDoesNotExist()
        } else {
            // In developer mode, they should be present
            composeTestRule.onNodeWithTag("drawer_dev_console").assertExists()
        }
    }

    @Test
    fun testNavControllerGating() {
        // Instantiate a NavController and verify it gates destinations
        val navController = NavController(Screen.Home)
        
        // Navigate to developer tools
        navController.navigateTo(Screen.DeveloperTools)
        
        if (BuildCapabilities.isProductionBuild) {
            // Should redirect to Home
            assertEquals(Screen.Home, navController.currentScreen)
        } else {
            // Should allow in developer build
            assertEquals(Screen.DeveloperTools, navController.currentScreen)
        }
    }
}
