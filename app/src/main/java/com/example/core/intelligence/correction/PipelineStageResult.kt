package com.example.core.intelligence.correction

data class PipelineStageResult<T>(
    val output: T,
    val latencyMs: Long,
    val debugTrace: List<String>,
    val failures: List<FailureType>
)
