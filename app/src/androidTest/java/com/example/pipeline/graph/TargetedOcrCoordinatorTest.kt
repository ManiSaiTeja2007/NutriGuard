package com.example.pipeline.graph

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.pipeline.OCRPipeline
import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.pipeline.graph.TargetedOcrCoordinator
import com.example.core.pipeline.graph.LayoutZone
import com.example.core.pipeline.graph.ZoneType
import com.example.core.pipeline.graph.ZonePriority
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TargetedOcrCoordinatorTest {

    @Test
    fun testTargetedOcrExecution() = kotlinx.coroutines.runBlocking {
        val ocrPipeline = OCRPipeline()
        val coordinator = TargetedOcrCoordinator(ocrPipeline)
        val context = SemanticRoutingContext()
        val profiler = ExecutionProfiler()

        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        context.detectedZones.add(
            LayoutZone(Rect(10, 10, 190, 190), ZoneType.INGREDIENTS, ZonePriority.HIGH, 1.0f)
        )

        val result = coordinator.execute(bitmap, context, profiler)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        ocrPipeline.close()
    }
}
