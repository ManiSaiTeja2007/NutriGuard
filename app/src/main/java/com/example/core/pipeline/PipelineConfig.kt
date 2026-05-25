package com.example.core.pipeline

enum class PipelineMode {
    PRODUCTION,
    DEVELOPER,
    BENCHMARK
}

data class PipelineConfig(
    val mode: PipelineMode = PipelineMode.PRODUCTION,
    val enableReplay: Boolean = false,
    val enableMetrics: Boolean = false,
    val enableOverlayData: Boolean = false
)
