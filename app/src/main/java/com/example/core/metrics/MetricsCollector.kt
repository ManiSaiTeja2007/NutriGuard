package com.example.core.metrics

class MetricsCollector {
    private val latencies = mutableMapOf<String, Long>()
    private var memoryUsageKb: Long = 0L

    fun recordLatency(stageName: String, latencyMs: Long) {
        latencies[stageName] = latencyMs
    }

    fun setMemoryUsage(kb: Long) {
        memoryUsageKb = kb
    }

    fun getLatencies(): Map<String, Long> = latencies.toMap()
    fun getMemoryUsage(): Long = memoryUsageKb

    fun clear() {
        latencies.clear()
        memoryUsageKb = 0
    }
}
