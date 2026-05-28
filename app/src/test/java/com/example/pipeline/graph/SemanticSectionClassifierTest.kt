package com.example.pipeline.graph

import android.graphics.Rect
import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord
import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.pipeline.graph.SemanticSectionClassifier
import com.example.core.pipeline.graph.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSectionClassifierTest {

    @Test
    fun testClassifierClassification() = kotlinx.coroutines.runBlocking {
        val classifier = SemanticSectionClassifier()
        val context = SemanticRoutingContext()
        val profiler = ExecutionProfiler()

        val lines = listOf(
            OCRLine(listOf(OCRWord("Ingredients:", 1.0f, Rect(0, 0, 50, 10))), Rect(0, 0, 50, 10), 1.0f),
            OCRLine(listOf(OCRWord("Sugar,", 1.0f, Rect(0, 15, 30, 25)), OCRWord("Salt", 1.0f, Rect(35, 15, 60, 25))), Rect(0, 15, 60, 25), 1.0f),
            OCRLine(listOf(OCRWord("Allergy", 1.0f, Rect(0, 50, 40, 60)), OCRWord("Advice:", 1.0f, Rect(45, 50, 80, 60))), Rect(0, 50, 80, 60), 1.0f),
            OCRLine(listOf(OCRWord("May", 1.0f, Rect(0, 65, 20, 75)), OCRWord("contain", 1.0f, Rect(25, 65, 60, 75)), OCRWord("peanuts", 1.0f, Rect(65, 65, 100, 75))), Rect(0, 65, 100, 75), 1.0f)
        )
        context.targetedOcrLines.addAll(lines)

        val result = classifier.execute(Unit, context, profiler)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        
        val sections = result.output!!
        assertEquals(2, sections.size)
        assertEquals(SectionType.INGREDIENTS, sections[0].type)
        assertEquals(SectionType.ALLERGENS, sections[1].type)
    }
}
