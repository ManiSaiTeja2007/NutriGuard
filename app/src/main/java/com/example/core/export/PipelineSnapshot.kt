package com.example.core.export

import com.example.core.pipeline.PipelineResult

data class PipelineSnapshot(
    val executionId: String,
    val rawImagePath: String?,
    val preprocessedImagePath: String?,
    val result: PipelineResult,
    val timestamp: Long = System.currentTimeMillis(),
    val scanSource: String = "Unknown"
)
