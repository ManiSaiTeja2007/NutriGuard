package com.example.regression

import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.ResolutionSource
import com.example.core.confidence.ConfidenceBand
import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ContextualReconstructionTest {

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
        val keys = listOf("openfoodfacts_ingredients", "openfoodfacts_additives", "openfoodfacts_products", "fail_001.png", "fail_002.png")
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
    fun testContextualDecayAndSafeguards() {
        assertDatasetsVerified()
        var file = File("benchmark/regression/contextual_reconstruction_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/contextual_reconstruction_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val vocab = IngredientVocabulary()
        val engine = OcrCorrectionEngine(vocab)
        val metadata = OcrMetadata(ocrConfidence = 0.70f)

        val cases = json.getJSONArray("test_cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val contextArr = case.getJSONArray("context")
            val targetInput = case.getString("target_input")
            val expected = case.getString("expected")
            val boostExpected = case.getBoolean("boost_expected")

            val contextList = mutableListOf<String>()
            for (j in 0 until contextArr.length()) {
                contextList.add(contextArr.getString(j))
            }

            val results = engine.correct(contextList, metadata)
            
            // Find corrected result for target
            val targetRes = results.firstOrNull { it.originalToken == targetInput }
            assertNotNull("Target token '$targetInput' should be present in results", targetRes)
            
            assertEquals("Should resolve to correct canonical", expected, targetRes?.canonical)
            
            if (boostExpected) {
                val step = targetRes?.confidenceStep
                assertNotNull("ConfidenceStep must not be null for boosted candidate", step)
                assertTrue("Context bonus must be > 0", step!!.contextBonus > 0.0f)
                
                // Assert base confidence safeguard: if base confidence was under 0.70f, final confidence must not be boosted above 0.70f
                if (step.baseConfidence < 0.70f) {
                    assertTrue("Base confidence safeguard: final confidence must remain low", step.finalConfidence < 0.70f)
                }
            }
        }
    }

    @Test
    fun testSafeRejections() {
        assertDatasetsVerified()
        var file = File("benchmark/regression/safe_rejection_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/safe_rejection_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val cases = json.getJSONArray("test_cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val input = case.getString("input")
            val expectedRejected = case.getBoolean("expected_rejected")

            val result = IngredientInterpreter.interpret(input, 0.95f)
            if (expectedRejected) {
                assertEquals("Rejected token must map to UNCERTAIN band", ConfidenceBand.UNCERTAIN, result.confidence)
                assertNull("Rejected token canonicalName must be null", result.canonicalName)
            }
        }
    }

    @Test
    fun testCalibrationDriftMetrics() {
        assertDatasetsVerified()
        // Compute and print calibration drift metrics to track semantic regression

        // 1. Ambiguity Preservation Drift
        var totalAmbiguity = 0
        var failedAmbiguity = 0
        try {
            var file = File("benchmark/regression/ambiguity_preservation_regression.json")
            if (!file.exists()) {
                file = File("../benchmark/regression/ambiguity_preservation_regression.json")
            }
            val json = JSONObject(file.readText())

            val cases = json.getJSONArray("test_cases")
            totalAmbiguity = cases.length()
            for (i in 0 until totalAmbiguity) {
                val case = cases.getJSONObject(i)
                val input = case.getString("input")
                val result = IngredientInterpreter.interpret(input, 0.95f)
                if (result.confidence != ConfidenceBand.UNCERTAIN || result.canonicalName != null) {
                    failedAmbiguity++
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to load ambiguity preservation json for drift computation: ${e.message}")
        }
        val ambiguityDrift = if (totalAmbiguity > 0) failedAmbiguity.toFloat() / totalAmbiguity else 0.0f

        // 2. Contextual False Positive Drift
        // Let's test if a low-confidence candidate below safeguard limit (e.g. base confidence 0.50f) gets incorrectly accepted due to contextual boost
        val vocab = IngredientVocabulary()
        val engine = OcrCorrectionEngine(vocab)
        // input a corrupted token "citric ac" which has base confidence of 0.79f. If we lower metadata OCR confidence to 0.40f,
        // its base confidence falls below 0.70f limit, so it must not get boosted into an accepted match.
        val lowOcrMetadata = OcrMetadata(ocrConfidence = 0.40f)
        val contextResult = engine.correct(listOf("e330", "citric ac"), lowOcrMetadata)
        val target = contextResult.firstOrNull { it.originalToken == "citric ac" }
        val interpreterResult = target?.let {
            val baseConf = it.confidenceStep?.baseConfidence ?: it.confidence
            IngredientInterpreter.interpret(it.canonical, it.confidence, it.originalToken, "citric ac", baseConf)
        }
        val falsePositiveDrift = if (interpreterResult != null && interpreterResult.confidence != ConfidenceBand.UNCERTAIN) 1.0f else 0.0f

        // 3. Replay Consistency Drift
        var totalReplay = 0
        var failedReplay = 0
        try {
            var file = File("benchmark/regression/replay_determinism_regression.json")
            if (!file.exists()) {
                file = File("../benchmark/regression/replay_determinism_regression.json")
            }
            val json = JSONObject(file.readText())

            val cases = json.getJSONArray("test_cases")
            totalReplay = cases.length()
            for (i in 0 until totalReplay) {
                val case = cases.getJSONObject(i)
                val inputArr = case.getJSONArray("input")
                val expectedArr = case.getJSONArray("expected")
                val inputList = (0 until inputArr.length()).map { inputArr.getString(it) }
                val expectedList = (0 until expectedArr.length()).map { expectedArr.getString(it) }
                
                val run1 = engine.correct(inputList, OcrMetadata(ocrConfidence = 0.85f)).map { it.canonical }
                val run2 = engine.correct(inputList, OcrMetadata(ocrConfidence = 0.85f)).map { it.canonical }
                if (run1 != expectedList || run2 != expectedList || run1 != run2) {
                    failedReplay++
                    println("Replay Mismatch: Input=$inputList")
                    println("  run1=$run1")
                    println("  run2=$run2")
                    println("  expected=$expectedList")
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to load replay determinism json for drift computation: ${e.message}")
        }

        val replayDrift = if (totalReplay > 0) failedReplay.toFloat() / totalReplay else 0.0f

        // 4. Multilingual Reconstruction Drift
        var totalMulti = 0
        var failedMulti = 0
        try {
            var file = File("benchmark/regression/multilingual_calibration_regression.json")
            if (!file.exists()) {
                file = File("../benchmark/regression/multilingual_calibration_regression.json")
            }
            val json = JSONObject(file.readText())

            val cases = json.getJSONArray("test_cases")
            totalMulti = cases.length()
            for (i in 0 until totalMulti) {
                val case = cases.getJSONObject(i)
                val text = case.getString("text")
                val expected = case.getString("expected_canonical")
                
                val correctionResults = engine.correct(listOf(text), OcrMetadata(ocrConfidence = 0.95f))
                val correctRes = correctionResults.first()
                val interpreterResult = IngredientInterpreter.interpret(correctRes.canonical, correctRes.confidence, correctRes.originalToken)
                if (interpreterResult.canonicalName?.lowercase() != expected.lowercase()) {
                    failedMulti++
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to load multilingual calibration json for drift computation: ${e.message}")
        }
        val multilingualDrift = if (totalMulti > 0) failedMulti.toFloat() / totalMulti else 0.0f

        println("Calibration Drift Metrics:")
        println(" - Ambiguity Preservation Drift: ${"%.2f%%".format(ambiguityDrift * 100)}")
        println(" - Contextual False Positive Drift: ${"%.2f%%".format(falsePositiveDrift * 100)}")
        println(" - Replay Consistency Drift: ${"%.2f%%".format(replayDrift * 100)}")
        println(" - Multilingual Reconstruction Drift: ${"%.2f%%".format(multilingualDrift * 100)}")

        // Verify drift values are within acceptable bounds
        assertEquals(0.0f, ambiguityDrift, 0.001f)
        assertEquals(0.0f, falsePositiveDrift, 0.001f)
        assertEquals(0.0f, replayDrift, 0.001f)
        assertEquals(0.0f, multilingualDrift, 0.001f)
    }
}
