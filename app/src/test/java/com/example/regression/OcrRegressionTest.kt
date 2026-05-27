package com.example.regression

import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse

class OcrRegressionTest {

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
    fun testOcrConfusionHandling() {
        assertDatasetsVerified()
        var file = File("benchmark/regression/ocr_confusion_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/ocr_confusion_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val vocab = IngredientVocabulary()
        val engine = OcrCorrectionEngine(vocab)
        val metadata = OcrMetadata(ocrConfidence = 0.85f)

        val cases = json.getJSONArray("test_cases")
        var passes = 0
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val input = case.getString("input")
            val expected = case.getString("expected")

            val results = engine.correct(listOf(input), metadata)
            val res = results.first()
            
            if (res.canonical.lowercase() == expected.lowercase()) {
                passes++
            } else {
                System.err.println("OCR Confusion Regression FAIL: \"$input\" -> expected \"$expected\" but got \"${res.canonical}\"")
            }
        }
        val accuracy = passes.toFloat() / cases.length()
        println("OCR Confusion Accuracy: ${"%.2f%%".format(accuracy * 100)}")
        assertEquals("All OCR confusion cases must pass", cases.length(), passes)
    }
}
