package com.example.regression

import com.example.core.intelligence.IngredientInterpreter
import com.example.core.confidence.ConfidenceBand
import com.example.core.ontology.IngredientCategory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class AmbiguityRegressionTest {

    @Test
    fun testAmbiguityPreservation() {
        var file = File("benchmark/regression/ambiguity_preservation_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/ambiguity_preservation_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val cases = json.getJSONArray("test_cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val input = case.getString("input")
            val expectedBand = case.getString("band") // e.g. "UNCERTAIN"

            val result = IngredientInterpreter.interpret(input, 0.95f)
            
            assertEquals("Ambiguous input '$input' must resolve to UNCERTAIN", ConfidenceBand.valueOf(expectedBand), result.confidence)
            assertEquals("Ambiguous input '$input' must resolve to UNKNOWN category", IngredientCategory.UNKNOWN, result.category)
            assertNull("Ambiguous input '$input' canonical name must be null", result.canonicalName)
        }
    }

    @Test
    fun testAdditiveParsing() {
        var file = File("benchmark/regression/additive_parsing_regression.json")
        if (!file.exists()) {
            file = File("../benchmark/regression/additive_parsing_regression.json")
        }
        val json = JSONObject(file.readText())

        assertEquals("1.0.0", json.getString("schema_version"))

        val cases = json.getJSONArray("test_cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val input = case.getString("input")
            val expected = case.getString("expected")

            val result = IngredientInterpreter.interpret(input, 0.95f)

            assertNotNull("Interpretation for '$input' should succeed", result.canonicalName)
            assertEquals("Additive parsing for '$input' must match expected canonical", expected.lowercase(), result.canonicalName?.lowercase())
        }
    }
}
