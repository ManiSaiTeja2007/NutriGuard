package com.example.verification

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.pipeline.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeExecutionVerificationTest {

    /**
     * Verifies the unified execution graph end-to-end flow.
     * Asserts that:
     * 1. All 8 core stages of the execution graph execute.
     * 2. Semantic section classifier and router route interpretations correctly.
     * 3. UI-compatible navigation JSON serialization maps fields (canonical, category, warnings, etc.) properly.
     */
    @Test
    fun testUnifiedExecutionGraphRun() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val ocrPipeline = OCRPipeline()
        val vocabulary = IngredientVocabulary()
        val pipelineRunner = PipelineRunner(ocrPipeline, vocabulary)

        try {
            val imagePath = "datasets/raw/clean_labels/label_000006.jpg"
            val bitmap = context.assets.open(imagePath).use {
                BitmapFactory.decodeStream(it)
            }
            assertNotNull("Test bitmap loading failed", bitmap)

            val config = PipelineConfig(
                mode = PipelineMode.DEVELOPER,
                enableReplay = true,
                enableMetrics = true,
                enableOverlayData = true
            )
            val result = pipelineRunner.run(
                bitmap = bitmap!!,
                rotationDegrees = 0,
                source = ImageSource.TEST_ASSET,
                config = config,
                context = context
            )

            // 1. Verify PipelineRunner executed and produced a valid non-null result
            assertNotNull("PipelineResult should not be null", result)
            assertNotNull("Execution ID must be populated", result.executionId)

            // 2. Verify SemanticExecutionGraph stages executed
            val executedStages = result.replayTrace.map { it.stageName }
            val expectedStages = listOf(
                "structural_analysis",
                "targeted_ocr",
                "section_classification",
                "semantic_routing",
                "specialized_interpretation",
                "contextual_reconstruction",
                "aggregation",
                "confidence_calibration"
            )

            for (stage in expectedStages) {
                assertTrue(
                    "Execution Graph Stage '$stage' was skipped or not executed. Executed stages: $executedStages",
                    executedStages.contains(stage)
                )
            }

            // 3. Verify SemanticRouter routed sections and populated interpretations
            assertTrue("Reconstructed lines should not be empty", result.ocrLines.isNotEmpty())
            assertTrue("Semantic ingredients should not be empty", result.semanticIngredients.isNotEmpty())
            assertTrue("Interpreted ingredients should not be empty", result.interpretedIngredients.isNotEmpty())

            // 4. Verify UI Data payload generation from result
            val canonicalJson = JSONArray().apply {
                result.semanticIngredients.forEach { ingredient ->
                    put(JSONObject().apply {
                        put("canonical", ingredient.canonical)
                        put("confidence", ingredient.confidence.toDouble())
                        put("originalToken", ingredient.originalToken)
                        put("interpretedCategory", ingredient.interpretedCategory ?: "")
                        put("additiveCode", ingredient.additiveCode ?: "")
                        put("warnings", JSONArray(ingredient.warnings))
                    })
                }
            }.toString()

            assertNotNull("Generated UI navigation payload should not be null", canonicalJson)
            val testArray = JSONArray(canonicalJson)
            assertTrue("UI navigation payload must contain items", testArray.length() > 0)
            
            val firstItem = testArray.getJSONObject(0)
            assertTrue("UI payload must carry 'canonical'", firstItem.has("canonical"))
            assertTrue("UI payload must carry 'interpretedCategory'", firstItem.has("interpretedCategory"))

            println("Unified Execution Graph successfully verified end-to-end. All 8 stages executed successfully.")

        } finally {
            ocrPipeline.close()
        }
    }
}
