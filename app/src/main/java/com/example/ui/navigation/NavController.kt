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
