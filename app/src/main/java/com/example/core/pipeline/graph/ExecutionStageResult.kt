package com.example.core.pipeline.graph

import java.util.UUID

interface StageResult {
    val executionId: UUID
    val stageName: String
    val latencyMs: Long
    val replayArtifacts: Map<String, Any>
    val failures: List<String>
    val isSuccess: Boolean
}

data class ExecutionStageResult<out T>(
    override val executionId: UUID,
    override val stageName: String,
    val output: T?,
    override val latencyMs: Long,
    override val replayArtifacts: Map<String, Any>,
    override val failures: List<String> = emptyList()
) : StageResult {
    override val isSuccess: Boolean = output != null && failures.isEmpty()
}
