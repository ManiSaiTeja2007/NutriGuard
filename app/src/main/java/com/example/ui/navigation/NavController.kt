package com.example.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavController(initialScreen: Screen = Screen.Home) {
    var currentScreen by mutableStateOf(initialScreen)
        private set

    private val backStack = mutableListOf<Screen>()

    private fun filterScreen(screen: Screen): Screen {
        val isForbidden = com.example.core.config.BuildCapabilities.isProductionBuild && (
            screen is Screen.DeveloperTools ||
            screen is Screen.ReplayViewer ||
            screen is Screen.BenchmarkRunner
        )
        return if (isForbidden) Screen.Home else screen
    }

    fun navigateTo(screen: Screen) {
        val target = filterScreen(screen)
        // ISSUE-006 FIX: Prevent pushing the same screen class consecutively (no-op duplicate nav).
        // Also cap backstack depth at 10 to prevent unbounded growth during rapid navigation.
        if (backStack.isNotEmpty() && backStack.last()::class == target::class) {
            currentScreen = target
            return
        }
        if (backStack.size >= 10) {
            backStack.removeAt(0)  // Drop oldest entry to maintain the cap
        }
        backStack.add(currentScreen)
        currentScreen = target
    }


    fun popBackStack(): Boolean {
        if (backStack.isNotEmpty()) {
            currentScreen = filterScreen(backStack.removeAt(backStack.size - 1))
            return true
        }
        return false
    }

    fun clearBackStackAndNavigate(screen: Screen) {
        backStack.clear()
        currentScreen = filterScreen(screen)
    }
}
