package com.example.regression

import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReplayRegressionTest {

    @Test
    fun testReplayDeterminism() {
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

            // Run 2 to 5: Verify 100% replay consistency/determinism
            for (runIdx in 2..5) {
                val runN = engine.correct(inputList, metadata)
                val outputN = runN.map { it.canonical }
                assertEquals("Replay run $runIdx must be 100% identical to run 1", output1, outputN)

                
                // Assert other metadata fields are deterministic
                for (j in run1.indices) {
                    assertEquals("Confidence must match deterministically", run1[j].confidence, runN[j].confidence, 0.0001f)
                    assertEquals("Original token must match deterministically", run1[j].originalToken, runN[j].originalToken)
                }
            }
        }
        println("Replay Determinism: 100% pass across all test cases.")
    }
}
