package com.example.platform.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nutriguard_settings")

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class OcrMode {
    ACCURACY,
    PERFORMANCE
}

class SettingsRepository(private val context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ADAPTIVE_OCR = booleanPreferencesKey("adaptive_ocr")
        private val OCR_MODE = stringPreferencesKey("ocr_mode")
        private val LARGER_TEXT = booleanPreferencesKey("larger_text")
        private val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        
        // Debugging flags (only exposed/modified under dev builds)
        private val SHOW_OVERLAYS = booleanPreferencesKey("show_overlays")
        private val REPLAY_TRACES = booleanPreferencesKey("replay_traces")
        private val OCR_DIAGNOSTICS = booleanPreferencesKey("ocr_diagnostics")
        private val PREPROCESSING_PREVIEWS = booleanPreferencesKey("preprocessing_previews")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val name = prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name
        try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    val adaptiveOcrFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ADAPTIVE_OCR] ?: true
    }

    val ocrModeFlow: Flow<OcrMode> = dataStore.data.map { prefs ->
        val name = prefs[OCR_MODE] ?: OcrMode.ACCURACY.name
        try { OcrMode.valueOf(name) } catch (e: Exception) { OcrMode.ACCURACY }
    }

    val largerTextFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LARGER_TEXT] ?: false
    }

    val highContrastFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HIGH_CONTRAST] ?: false
    }

    val showOverlaysFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_OVERLAYS] ?: true
    }

    val replayTracesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[REPLAY_TRACES] ?: true
    }

    val ocrDiagnosticsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[OCR_DIAGNOSTICS] ?: true
    }

    val preprocessingPreviewsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PREPROCESSING_PREVIEWS] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAdaptiveOcr(enabled: Boolean) {
        dataStore.edit { it[ADAPTIVE_OCR] = enabled }
    }

    suspend fun setOcrMode(mode: OcrMode) {
        dataStore.edit { it[OCR_MODE] = mode.name }
    }

    suspend fun setLargerText(enabled: Boolean) {
        dataStore.edit { it[LARGER_TEXT] = enabled }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        dataStore.edit { it[HIGH_CONTRAST] = enabled }
    }

    suspend fun setShowOverlays(enabled: Boolean) {
        dataStore.edit { it[SHOW_OVERLAYS] = enabled }
    }

    suspend fun setReplayTraces(enabled: Boolean) {
        dataStore.edit { it[REPLAY_TRACES] = enabled }
    }

    suspend fun setOcrDiagnostics(enabled: Boolean) {
        dataStore.edit { it[OCR_DIAGNOSTICS] = enabled }
    }

    suspend fun setPreprocessingPreviews(enabled: Boolean) {
        dataStore.edit { it[PREPROCESSING_PREVIEWS] = enabled }
    }
}
