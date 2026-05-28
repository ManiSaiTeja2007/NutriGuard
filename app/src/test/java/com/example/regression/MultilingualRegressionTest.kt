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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MultilingualRegressionTest {

    private fun assertDatasetsVerified() {
        var dir: File? = File(".").absoluteFile
        var benchmarkDir: File? = null
        while (dir != null) {
            val candidate = File(dir, "benchmark")
            if (candidate.exists() && candidate.isDirectory) {
                benchmarkDir = candidate
                break
            }
            dir = dir.parentFile
        }
        val manifestFile = File(benchmarkDir, "semantic/manifests/dataset_versions.json")
        assertTrue("Manifest dataset_versions.json must exist. Run downloader script first.", manifestFile.exists())
        val json = JSONObject(manifestFile.readText())
        val keys = listOf("openfoodfacts_ingredients", "openfoodfacts_additives", "openfoodfacts_products", "fail_001.png", "fail_002.png", "fail_003.png", "fail_004.png")
        for (key in keys) {
            val dataset = json.optJSONObject(key)
            assertNotNull("Dataset entry for $key must exist in manifest", dataset)
            assertTrue("Dataset $key must be verified", dataset!!.optBoolean("verified", false))
            assertFalse("Dataset $key must not use fallback", dataset.optBoolean("fallback_used", true))
            assertTrue("Dataset $key must be calibration eligible", dataset.optBoolean("calibration_eligible", false))
            assertEquals("Dataset $key provenance must be REAL_WORLD", "REAL_WORLD", dataset.optString("dataset_type"))
        }
    }

    @Test
    fun testMultilingualCalibration() {
        assertDatasetsVerified()
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
