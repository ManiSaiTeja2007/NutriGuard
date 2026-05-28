package com.example.regression

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

class ReplayConsistencyTest {

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
    fun testReplayConsistency() {
        assertDatasetsVerified()
        var file = File("benchmark/regression/replay_determinism_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/replay_determinism_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val vocab = IngredientVocabulary()
        val engine = OcrCorrectionEngine(vocab)
        val metadata = OcrMetadata(ocrConfidence = 0.85f)

        val cases = json.getJSONArray("test_cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val inputArr = case.getJSONArray("input")
            val expectedArr = case.getJSONArray("expected")

            val inputList = mutableListOf<String>()
            for (j in 0 until inputArr.length()) {
                inputList.add(inputArr.getString(j))
            }

            val expectedList = mutableListOf<String>()
            for (j in 0 until expectedArr.length()) {
                expectedList.add(expectedArr.getString(j))
            }

            // Run 1
            val run1 = engine.correct(inputList, metadata)
            val output1 = run1.map { it.canonical }
            assertEquals("Run 1 must match expected output", expectedList, output1)

            // Replay Runs 2 to 5
            for (runIdx in 2..5) {
                val runN = engine.correct(inputList, metadata)
                val outputN = runN.map { it.canonical }
                assertEquals("Replay run $runIdx must match run 1 output", output1, outputN)

                for (j in run1.indices) {
                    assertEquals("Confidence must match deterministically", run1[j].confidence, runN[j].confidence, 0.0001f)
                    assertEquals("Original token must match deterministically", run1[j].originalToken, runN[j].originalToken)
                }
            }
        }
    }
}
