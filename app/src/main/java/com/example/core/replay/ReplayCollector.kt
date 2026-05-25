package com.example.core.replay

data class ReplayStageTrace(
    val stageName: String,
    val input: String,
    val output: String,
    val latencyMs: Long
)

class ReplayCollector {
    private val stages = mutableListOf<ReplayStageTrace>()

    fun addStage(stageName: String, input: String, output: String, latencyMs: Long) {
        stages.add(ReplayStageTrace(stageName, input, output, latencyMs))
    }

    fun getStages(): List<ReplayStageTrace> = stages.toList()

    fun clear() {
        stages.clear()
    }
}
