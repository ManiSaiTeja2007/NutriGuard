package com.example.pipeline.graph

import com.example.core.pipeline.graph.ExecutionProfiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionProfilerTest {

    @Test
    fun testProfilerRecording() {
        val profiler = ExecutionProfiler()
        profiler.startStage("test_stage")
        Thread.sleep(50)
        profiler.endStage("test_stage")

        val metrics = profiler.getMetrics("test_stage")
        assertNotNull(metrics)
        assertEquals("test_stage", metrics?.stageName)
        assertTrue(metrics!!.latencyMs >= 40L) // Allow slight variance in sleep timing
    }
}
