package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scan : Screen("scan")
    
    data class Results(
        val rawOcrText: String,
        val normalizedText: String,
        val extractedTokens: List<String>,
        val canonicalJson: String,      // JSON string of List<CorrectionResult>
        val latencyJson: String         // JSON string of stage latencies
    ) : Screen("results")
    
    object DebugReplay : Screen("debug_replay")
    data class ReplayViewer(val replayId: String) : Screen("replay_viewer")
    object Settings : Screen("settings")
}
