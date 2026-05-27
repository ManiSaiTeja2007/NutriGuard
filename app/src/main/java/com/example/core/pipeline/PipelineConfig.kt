package com.example.core.pipeline

import com.example.core.intelligence.confidence.DatasetProvenance

enum class PipelineMode {
    PRODUCTION,
    DEVELOPER,
    BENCHMARK
}

data class PipelineConfig(
    val mode: PipelineMode = PipelineMode.PRODUCTION,
    val enableReplay: Boolean = false,
    val enableMetrics: Boolean = false,
    val enableOverlayData: Boolean = false,
    val provenance: DatasetProvenance = DatasetProvenance.REAL_WORLD,
    val calibrationEligible: Boolean = true
)

