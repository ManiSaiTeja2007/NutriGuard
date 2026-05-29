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
class PipelineIntegrationSmokeTest {

    @Test
    fun testPipelineRunnerIntegrationSmoke() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Instantiate core pipeline components
        val ocrPipeline = OCRPipeline()
        val vocabulary = IngredientVocabulary()
        val semanticPipeline = SemanticPipeline(vocabulary)
        val pipelineRunner = PipelineRunner(ocrPipeline, semanticPipeline)

        try {
            // 2. Load the known test image
            val imagePath = "datasets/raw/clean_labels/label_000006.jpg"
            val bitmap = context.assets.open(imagePath).use {
                BitmapFactory.decodeStream(it)
            }
            assertNotNull("Test bitmap must load successfully: $imagePath", bitmap)

            // 3. Execute PipelineRunner
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

            // 4. Verify non-empty output verification
            assertNotNull("PipelineResult should not be null", result)
            assertNotNull("Execution ID should be populated", result.executionId)
            assertTrue("OCR lines should not be empty", result.ocrLines.isNotEmpty())
            assertTrue("Semantic ingredients output should not be empty", result.semanticIngredients.isNotEmpty())

            // 5. Verify non-empty interpretations
            assertTrue("Interpreted ingredients list should not be empty", result.interpretedIngredients.isNotEmpty())

            // 6. Verify non-empty replay trace logs
            assertTrue("Replay trace steps list should not be empty", result.replayTrace.isNotEmpty())

            // 7. Verify valid navigation payload construction
            val canonicalJson = JSONArray().apply {
                result.semanticIngredients.forEach { ingredient ->
                    put(JSONObject().apply {
                        put("canonical", ingredient.canonical)
                        put("confidence", ingredient.confidence.toDouble())
                        put("originalToken", ingredient.originalToken)
                        put("ontologyCategory", ingredient.ontologyCategory ?: "")
                        put("disambiguationRule", ingredient.disambiguationRule ?: "")
                        put("groupPath", ingredient.groupPath ?: "")
                        put("interpretedCategory", ingredient.interpretedCategory ?: "")
                        put("additiveCode", ingredient.additiveCode ?: "")
                        put("explanation", ingredient.explanation ?: "")
                        
                        val warningsArr = JSONArray()
                        ingredient.warnings.forEach { warningsArr.put(it) }
                        put("warnings", warningsArr)

                        val debugStepsArr = JSONArray()
                        ingredient.debugSteps.forEach { debugStepsArr.put(it) }
                        put("debugSteps", debugStepsArr)

                        val failuresArr = JSONArray()
                        ingredient.failures.forEach { failuresArr.put(it.name) }
                        put("failures", failuresArr)

                        val phraseArr = JSONArray()
                        ingredient.phraseWindow.forEach { phraseArr.put(it) }
                        put("phraseWindow", phraseArr)
                    })
                }
            }.toString()

            assertNotNull("Navigation payload JSON string should not be null", canonicalJson)
            assertTrue("Navigation payload JSON string should have content", canonicalJson.isNotBlank())
            
            val testArray = JSONArray(canonicalJson)
            assertTrue("Navigation payload JSON array must contain records", testArray.length() > 0)
            val firstRecord = testArray.getJSONObject(0)
            assertTrue("Navigation records must contain 'canonical' tag", firstRecord.has("canonical"))
            assertTrue("Navigation records must contain 'confidence' tag", firstRecord.has("confidence"))
            assertTrue("Navigation records must contain 'originalToken' tag", firstRecord.has("originalToken"))
            
            println("Smoke test passed: valid navigation payload of size ${testArray.length()} verified.")

        } finally {
            ocrPipeline.close()
        }
    }
}
