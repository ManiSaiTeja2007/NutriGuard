package com.example.platform.health

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppHealthMonitor {
    var lastScreenTransition by mutableStateOf<String?>("Init")
        private set

    var lastOcrState by mutableStateOf<String?>("Idle")
        private set

    var lastError by mutableStateOf<Throwable?>(null)
        private set

    var hasError by mutableStateOf(false)
        private set

    fun trackScreenTransition(screen: String) {
        lastScreenTransition = screen
    }

    fun trackOcrState(state: String) {
        lastOcrState = state
    }

    fun reportError(error: Throwable, contextInfo: String) {
        lastError = error
        hasError = true
        android.util.Log.e("AppHealthMonitor", "CRITICAL Telemetry Error: $contextInfo", error)
    }

    fun clearError() {
        lastError = null
        hasError = false
    }
}
