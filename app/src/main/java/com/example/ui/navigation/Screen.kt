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
    
    object Settings : Screen("settings")
    object About : Screen("about")
    
    // Developer & Benchmark capability-gated destinations
    object DeveloperTools : Screen("developer_tools")
    data class ReplayViewer(val replayId: String) : Screen("replay_viewer")
    object BenchmarkRunner : Screen("benchmark_runner")
}
