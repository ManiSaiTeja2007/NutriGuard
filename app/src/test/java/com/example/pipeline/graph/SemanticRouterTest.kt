package com.example.pipeline.graph

import android.graphics.Rect
import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord
import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.pipeline.graph.SemanticRouter
import com.example.core.pipeline.graph.ClassifiedSection
import com.example.core.pipeline.graph.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticRouterTest {

    @Test
    fun testRoutingSeparation() = kotlinx.coroutines.runBlocking {
        val router = SemanticRouter()
        val context = SemanticRoutingContext()
        val profiler = ExecutionProfiler()

        val ingredientsLine = OCRLine(listOf(OCRWord("sugar,", 1.0f, Rect(0, 0, 10, 10))), Rect(0, 0, 10, 10), 1.0f)
        val allergensLine = OCRLine(listOf(OCRWord("may contain milk", 1.0f, Rect(0, 20, 10, 30))), Rect(0, 20, 10, 30), 1.0f)

        context.classifiedSections.add(
            ClassifiedSection(SectionType.INGREDIENTS, null, listOf(ingredientsLine), 1.0f, "test")
        )
        context.classifiedSections.add(
            ClassifiedSection(SectionType.ALLERGENS, null, listOf(allergensLine), 1.0f, "test")
        )

        val result = router.execute(Unit, context, profiler)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        
        val routing = result.output!!
        assertNotNull(routing.allergenInterpretation)
        assertTrue(routing.allergenInterpretation!!.isMayContain)
        assertTrue(routing.allergenInterpretation!!.allergensDetected.contains("milk"))
        
        assertEquals(1, routing.ingredientTextBlocks.size)
        assertEquals("sugar,", routing.ingredientTextBlocks[0])
    }
}
