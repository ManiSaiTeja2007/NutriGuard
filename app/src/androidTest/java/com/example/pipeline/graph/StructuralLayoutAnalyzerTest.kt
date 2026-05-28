package com.example.pipeline.graph

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.pipeline.graph.StructuralLayoutAnalyzer
import com.example.core.pipeline.graph.ZoneType
import com.example.core.pipeline.graph.ZonePriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StructuralLayoutAnalyzerTest {

    @Test
    fun testExecute() = kotlinx.coroutines.runBlocking {
        val analyzer = StructuralLayoutAnalyzer()
        val context = SemanticRoutingContext()
        val profiler = ExecutionProfiler()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val result = analyzer.execute(bitmap, context, profiler)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertNotNull(result.output)
        
        val analysis = result.output!!
        assertEquals(8, analysis.heatmap.size)
        assertEquals(8, analysis.heatmap[0].size)
        
        assertEquals(1, analysis.zones.size)
        assertEquals(ZoneType.UNKNOWN, analysis.zones[0].type)
        assertEquals(ZonePriority.HIGH, analysis.zones[0].priority)
    }
}
