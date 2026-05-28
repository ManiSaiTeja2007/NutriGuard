package com.example.regression

import com.example.core.intelligence.IngredientInterpreter
import com.example.core.confidence.ConfidenceBand
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class SafeRejectionTest {

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
}
