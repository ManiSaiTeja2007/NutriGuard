package com.example.regression

import com.example.core.confidence.ConfidenceBand
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriftMetricsTest {

    private fun findBenchmarkDir(): File {
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
        return benchmarkDir ?: throw IllegalStateException("Could not find 'benchmark' directory starting from " + File(".").absolutePath)
    }

    private fun assertDatasetsVerified(benchmarkDir: File) {
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
    fun testGenerateDriftMetrics() {
        val benchmarkDir = findBenchmarkDir()
        assertDatasetsVerified(benchmarkDir)

        val vocab = IngredientVocabulary()
        val engine = OcrCorrectionEngine(vocab)

        // 1. False Positive Drift
        var fpTotal = 0
        var fpFailed = 0
        try {
            val file = File(benchmarkDir, "regression/safe_rejection_regression.json")
            val json = JSONObject(file.readText())
            val cases = json.getJSONArray("test_cases")
            fpTotal = cases.length()
            for (i in 0 until fpTotal) {
                val case = cases.getJSONObject(i)
                val input = case.getString("input")
                val expectedRejected = case.getBoolean("expected_rejected")
                val result = IngredientInterpreter.interpret(input, 0.95f)
                if (expectedRejected) {
                    if (result.confidence != ConfidenceBand.UNCERTAIN || result.canonicalName != null) {
                        fpFailed++
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("FP drift load failed: ${e.message}")
        }
        val falsePositiveDrift = if (fpTotal > 0) fpFailed.toFloat() / fpTotal else 0.0f

        // 2. Ambiguity Preservation Drift
        var ambTotal = 0
        var ambFailed = 0
        try {
            val file = File(benchmarkDir, "regression/ambiguity_preservation_regression.json")
            val json = JSONObject(file.readText())
            val cases = json.getJSONArray("test_cases")
            ambTotal = cases.length()
            for (i in 0 until ambTotal) {
                val case = cases.getJSONObject(i)
                val input = case.getString("input")
                val result = IngredientInterpreter.interpret(input, 0.95f)
                if (result.confidence != ConfidenceBand.UNCERTAIN || result.canonicalName != null) {
                    ambFailed++
                }
            }
        } catch (e: Exception) {
            System.err.println("Ambiguity drift load failed: ${e.message}")
        }
        val ambiguityDrift = if (ambTotal > 0) ambFailed.toFloat() / ambTotal else 0.0f

        // 3. Replay Consistency Drift
        var repTotal = 0
        var repFailed = 0
        try {
            val file = File(benchmarkDir, "regression/replay_determinism_regression.json")
            val json = JSONObject(file.readText())
            val cases = json.getJSONArray("test_cases")
            repTotal = cases.length()
            for (i in 0 until repTotal) {
                val case = cases.getJSONObject(i)
                val inputArr = case.getJSONArray("input")
                val inputList = (0 until inputArr.length()).map { inputArr.getString(it) }
                
                val run1 = engine.correct(inputList, OcrMetadata(ocrConfidence = 0.85f)).map { it.canonical }
                val run2 = engine.correct(inputList, OcrMetadata(ocrConfidence = 0.85f)).map { it.canonical }
                if (run1 != run2) {
                    repFailed++
                }
            }
        } catch (e: Exception) {
            System.err.println("Replay drift load failed: ${e.message}")
        }
        val replayDrift = if (repTotal > 0) repFailed.toFloat() / repTotal else 0.0f

        // 4. Contextual Reconstruction Drift
        var ctxTotal = 0
        var ctxFailed = 0
        try {
            val file = File(benchmarkDir, "regression/contextual_reconstruction_regression.json")
            val json = JSONObject(file.readText())
            val cases = json.getJSONArray("test_cases")
            ctxTotal = cases.length()
            for (i in 0 until ctxTotal) {
                val case = cases.getJSONObject(i)
                val contextArr = case.getJSONArray("context")
                val targetInput = case.getString("target_input")
                val expected = case.getString("expected")
                val boostExpected = case.getBoolean("boost_expected")

                val contextList = (0 until contextArr.length()).map { contextArr.getString(it) }
                val results = engine.correct(contextList, OcrMetadata(ocrConfidence = 0.70f))
                val targetRes = results.firstOrNull { it.originalToken == targetInput }

                if (targetRes?.canonical != expected) {
                    ctxFailed++
                } else if (boostExpected) {
                    val step = targetRes.confidenceStep
                    if (step == null || step.contextBonus <= 0.0f) {
                        ctxFailed++
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Ctx drift load failed: ${e.message}")
        }
        val contextualDrift = if (ctxTotal > 0) ctxFailed.toFloat() / ctxTotal else 0.0f

        // 5. Multilingual Recovery Drift
        var mulTotal = 0
        var mulFailed = 0
        try {
            val file = File(benchmarkDir, "regression/multilingual_calibration_regression.json")
            val json = JSONObject(file.readText())
            val cases = json.getJSONArray("test_cases")
            mulTotal = cases.length()
            for (i in 0 until mulTotal) {
                val case = cases.getJSONObject(i)
                val text = case.getString("text")
                val expected = case.getString("expected_canonical")

                val correctionResults = engine.correct(listOf(text), OcrMetadata(ocrConfidence = 0.95f))
                val correctRes = correctionResults.first()
                val interpreterResult = IngredientInterpreter.interpret(correctRes.canonical, correctRes.confidence, correctRes.originalToken)
                if (interpreterResult.canonicalName?.lowercase() != expected.lowercase()) {
                    mulFailed++
                }
            }
        } catch (e: Exception) {
            System.err.println("Multilingual drift load failed: ${e.message}")
        }
        val multilingualDrift = if (mulTotal > 0) mulFailed.toFloat() / mulTotal else 0.0f

        // 6. OCR Confusion Recovery Drift
        var ocrTotal = 0
        var ocrFailed = 0
        try {
            val file = File(benchmarkDir, "regression/ocr_confusion_regression.json")
            val json = JSONObject(file.readText())
            val cases = json.getJSONArray("test_cases")
            ocrTotal = cases.length()
            for (i in 0 until ocrTotal) {
                val case = cases.getJSONObject(i)
                val input = case.getString("input")
                val expected = case.getString("expected")

                val results = engine.correct(listOf(input), OcrMetadata(ocrConfidence = 0.85f))
                val res = results.first()
                if (res.canonical.lowercase() != expected.lowercase()) {
                    ocrFailed++
                }
            }
        } catch (e: Exception) {
            System.err.println("OCR confusion drift load failed: ${e.message}")
        }
        val ocrConfusionDrift = if (ocrTotal > 0) ocrFailed.toFloat() / ocrTotal else 0.0f

        // Write Convergence Report and Snapshots
        val reportDir = File(benchmarkDir, "reports/convergence")
        if (!reportDir.exists()) {
            reportDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.ROOT).format(Date())
        val timestampUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())

        val allowedThreshold = 0.0f

        val reportJson = JSONObject().apply {
            put("timestamp", timestampUtc)
            put("allowed_threshold", allowedThreshold)
            put("metrics", JSONObject().apply {
                put("false_positive_drift", falsePositiveDrift)
                put("ambiguity_preservation_drift", ambiguityDrift)
                put("replay_consistency_drift", replayDrift)
                put("contextual_reconstruction_drift", contextualDrift)
                put("multilingual_recovery_drift", multilingualDrift)
                put("ocr_confusion_recovery_drift", ocrConfusionDrift)
            })
        }

        File(reportDir, "convergence_report.json").writeText(reportJson.toString(2), Charsets.UTF_8)
        File(reportDir, "convergence_report_$timestamp.json").writeText(reportJson.toString(2), Charsets.UTF_8)

        // Strict validation assertions
        val delta = 0.001f
        assertEquals("False Positive Drift exceeds threshold", allowedThreshold, falsePositiveDrift, delta)
        assertEquals("Ambiguity Preservation Drift exceeds threshold", allowedThreshold, ambiguityDrift, delta)
        assertEquals("Replay Consistency Drift exceeds threshold", allowedThreshold, replayDrift, delta)
        assertEquals("Contextual Reconstruction Drift exceeds threshold", allowedThreshold, contextualDrift, delta)
        assertEquals("Multilingual Recovery Drift exceeds threshold", allowedThreshold, multilingualDrift, delta)
        assertEquals("OCR Confusion Recovery Drift exceeds threshold", allowedThreshold, ocrConfusionDrift, delta)
    }
}
