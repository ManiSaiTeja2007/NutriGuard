package com.example.pipeline.graph

import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.pipeline.graph.ReplayGenerationStage
import com.example.core.pipeline.graph.StageResult
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionGraphReplayTest {

    @Test
    fun testReplayGeneration() = kotlinx.coroutines.runBlocking {
        val stage = ReplayGenerationStage()
        val context = SemanticRoutingContext()
        val profiler = ExecutionProfiler()

        val mockStageResult = object : StageResult {
            override val executionId: UUID = context.executionId
            override val stageName: String = "structural_analysis"
            override val latencyMs: Long = 150L
            override val replayArtifacts: Map<String, Any> = mapOf("zonesCount" to 3)
            override val failures: List<String> = emptyList()
            override val isSuccess: Boolean = true
        }

        context.metadata["stageResults"] = listOf(mockStageResult)

        val result = stage.execute(Unit, context, profiler)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        
        val traces = result.output!!
        assertEquals(1, traces.size)
        assertEquals("structural_analysis", traces[0].stageName)
        assertEquals(150L, traces[0].latencyMs)
        assertTrue(traces[0].output.contains("zonesCount=3"))
    }
}
