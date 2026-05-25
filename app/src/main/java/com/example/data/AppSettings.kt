package com.example.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.core.config.BuildCapabilities
import com.example.platform.settings.OcrMode
import com.example.platform.settings.SettingsRepository
import com.example.platform.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppSettings {
    private lateinit var repository: SettingsRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI state flags backed by mutableStateOf for Compose recomposition
    var themePreference by mutableStateOf(ThemeMode.SYSTEM)
        private set
        
    var enableAdaptiveOcr by mutableStateOf(true)
        private set
        
    var ocrMode by mutableStateOf(OcrMode.ACCURACY)
        private set

    var largerTextEnabled by mutableStateOf(false)
        private set

    var highContrastEnabled by mutableStateOf(false)
        private set

    // Debugging flags (active in developer/internal builds only)
    var showOverlays by mutableStateOf(true)
        private set

    var replaySaving by mutableStateOf(true)
        private set

    var ocrDiagnostics by mutableStateOf(true)
        private set

    var preprocessingPreviews by mutableStateOf(true)
        private set

    // Legacy debugMode flag compatibility (returns true if we have developer capability)
    val debugMode: Boolean
        get() = BuildCapabilities.isDeveloperBuild

    fun initialize(context: Context, repository: SettingsRepository) {
        this.repository = repository

        // Collect repository flows reactively to update compose states
        scope.launch {
            repository.themeModeFlow.collect { themePreference = it }
        }
        scope.launch {
            repository.adaptiveOcrFlow.collect { enableAdaptiveOcr = it }
        }
        scope.launch {
            repository.ocrModeFlow.collect { ocrMode = it }
        }
        scope.launch {
            repository.largerTextFlow.collect { largerTextEnabled = it }
        }
        scope.launch {
            repository.highContrastFlow.collect { highContrastEnabled = it }
        }
        scope.launch {
            repository.showOverlaysFlow.collect { showOverlays = it }
        }
        scope.launch {
            repository.replayTracesFlow.collect { replaySaving = it }
        }
        scope.launch {
            repository.ocrDiagnosticsFlow.collect { ocrDiagnostics = it }
        }
        scope.launch {
            repository.preprocessingPreviewsFlow.collect { preprocessingPreviews = it }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch { repository.setThemeMode(mode) }
    }

    fun setAdaptiveOcrEnabled(enabled: Boolean) {
        scope.launch { repository.setAdaptiveOcr(enabled) }
    }

    fun setOcrPerformanceMode(mode: OcrMode) {
        scope.launch { repository.setOcrMode(mode) }
    }

    fun setLargerText(enabled: Boolean) {
        scope.launch { repository.setLargerText(enabled) }
    }

    fun setHighContrast(enabled: Boolean) {
        scope.launch { repository.setHighContrast(enabled) }
    }

    fun setShowOverlaysEnabled(enabled: Boolean) {
        scope.launch { repository.setShowOverlays(enabled) }
    }

    fun setReplaySavingEnabled(enabled: Boolean) {
        scope.launch { repository.setReplayTraces(enabled) }
    }

    fun setOcrDiagnosticsEnabled(enabled: Boolean) {
        scope.launch { repository.setOcrDiagnostics(enabled) }
    }

    fun setPreprocessingPreviewsEnabled(enabled: Boolean) {
        scope.launch { repository.setPreprocessingPreviews(enabled) }
    }
}
