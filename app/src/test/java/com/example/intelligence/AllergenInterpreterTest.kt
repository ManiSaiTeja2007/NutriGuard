package com.example.intelligence

import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord
import android.graphics.Rect
import com.example.core.intelligence.AllergenInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergenInterpreterTest {

    @Test
    fun testAllergenParsing() {
        val words = listOf(
            OCRWord("Allergy", 0.9f, Rect(0, 0, 10, 10)),
            OCRWord("Advice:", 0.9f, Rect(15, 0, 30, 10)),
            OCRWord("May", 0.9f, Rect(35, 0, 50, 10)),
            OCRWord("contain", 0.9f, Rect(55, 0, 70, 10)),
            OCRWord("peanut", 0.9f, Rect(75, 0, 90, 10)),
            OCRWord("and", 0.9f, Rect(95, 0, 110, 10)),
            OCRWord("milk", 0.9f, Rect(115, 0, 130, 10))
        )
        val line = OCRLine(words, Rect(0, 0, 130, 10), 0.9f)
        val result = AllergenInterpreter.interpret(listOf(line))

        assertTrue(result.isMayContain)
        assertEquals(2, result.allergensDetected.size)
        assertTrue(result.allergensDetected.contains("peanut"))
        assertTrue(result.allergensDetected.contains("milk"))
        assertEquals(2, result.warnings.size)
    }
}
