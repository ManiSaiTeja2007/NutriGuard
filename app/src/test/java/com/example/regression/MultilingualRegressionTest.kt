package com.example.regression

import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.ResolutionSource
import com.example.core.confidence.ConfidenceBand
import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MultilingualRegressionTest {

    @Test
    fun testMultilingualCalibration() {
        var file = File("benchmark/regression/multilingual_calibration_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/multilingual_calibration_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val vocab = IngredientVocabulary()
        val engine = OcrCorrectionEngine(vocab)
        val metadata = OcrMetadata(ocrConfidence = 0.95f)

        val cases = json.getJSONArray("test_cases")
        var passes = 0

        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val text = case.getString("text")
            val language = case.getString("language")
            val expected = case.getString("expected_canonical")

            // 1. Verify Correction Engine resolves the multilingual token
            val correctionResults = engine.correct(listOf(text), metadata)
            val correctRes = correctionResults.first()
            
            // 2. Verify Interpreter maps to correct canonical and caps confidence for transliterated words
            val interpreterResult = IngredientInterpreter.interpret(
                canonicalName = correctRes.canonical,
                confidence = correctRes.confidence,
                originalToken = correctRes.originalToken
            )

            val canonicalResult = interpreterResult.canonicalName ?: ""
            if (canonicalResult.lowercase() == expected.lowercase()) {
                passes++
                
                // Assert confidence capping rule for transliterated text
                if (language == "hi") {
                    assertEquals(
                        "Hindi transliterated text must cap at MODERATE confidence for safety",
                        ConfidenceBand.MODERATE,
                        interpreterResult.confidence
                    )
                    assertEquals(
                        "Resolution source must be TRANSLITERATION",
                        ResolutionSource.TRANSLITERATION,
                        interpreterResult.resolutionSource
                    )
                }
            } else {
                System.err.println("Multilingual Calibration FAIL: \"$text\" -> expected \"$expected\" but got \"$canonicalResult\"")
            }
        }

        val accuracy = passes.toFloat() / cases.length()
        println("Multilingual Calibration Accuracy: ${"%.2f%%".format(accuracy * 100)}")
        assertEquals("All multilingual calibration cases must pass", cases.length(), passes)
    }
}
