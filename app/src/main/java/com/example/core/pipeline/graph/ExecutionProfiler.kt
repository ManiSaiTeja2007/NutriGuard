package com.example.core.pipeline.graph

import android.os.SystemClock

class ExecutionProfiler {
    private val latencies = mutableMapOf<String, Long>()
    private val memoryBefore = mutableMapOf<String, Long>()
    private val memoryAfter = mutableMapOf<String, Long>()
    private val stageMetrics = mutableMapOf<String, StageMetrics>()

    data class StageMetrics(
        val stageName: String,
        val latencyMs: Long,
        val memoryAllocatedKb: Long
    )

    fun startStage(stageName: String) {
        latencies[stageName] = SystemClock.elapsedRealtime()
        val runtime = Runtime.getRuntime()
        memoryBefore[stageName] = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
    }

    fun endStage(stageName: String) {
        val startTime = latencies[stageName] ?: return
        val endTime = SystemClock.elapsedRealtime()
        val latency = endTime - startTime
        latencies[stageName] = latency

        val runtime = Runtime.getRuntime()
        val memory = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        memoryAfter[stageName] = memory

        val startMem = memoryBefore[stageName] ?: 0L
        val memoryAllocated = (memory - startMem).coerceAtLeast(0L)

        stageMetrics[stageName] = StageMetrics(stageName, latency, memoryAllocated)
    }

    fun getMetrics(stageName: String): StageMetrics? = stageMetrics[stageName]
    fun getAllMetrics(): Map<String, StageMetrics> = stageMetrics
}
