package com.example.verification

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.pipeline.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadlessPipelineTest {

    @Test
    fun testPipelineHeadlessExecutionOnLabel000006() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Instantiate core pipeline dependencies
        val ocrPipeline = OCRPipeline()
        val vocabulary = IngredientVocabulary()
        val semanticPipeline = SemanticPipeline(vocabulary)
        val pipelineRunner = PipelineRunner(ocrPipeline, semanticPipeline)

        try {
            // Load test image from assets
            val imagePath = "datasets/raw/clean_labels/label_000006.jpg"
            val bitmap = context.assets.open(imagePath).use {
                BitmapFactory.decodeStream(it)
            }
            assertNotNull("Test bitmap should be loaded successfully: $imagePath", bitmap)

            // Execute canonical pipeline runner
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
                config = config
            )

            // Asserts on execution outcome
            assertNotNull("Pipeline result should not be null", result)
            assertNotNull("Execution ID should be generated", result.executionId)
            assertTrue("OCR should recognize some lines", result.ocrLines.isNotEmpty())
            assertTrue("Should extract some ingredients", result.semanticIngredients.isNotEmpty())

            val canonicalList = result.semanticIngredients.map { it.canonical.trim().lowercase() }
            assertTrue(
                "Canonical ingredients should contain 'myfíne' or 'tdéal'",
                canonicalList.any { it.contains("myfíne") || it.contains("tdéal") || it.contains("sgaall3yl") }
            )

            // Validate that telemetry metrics are populated
            assertTrue("OCR latency should be tracked", result.metrics.ocrLatencyMs >= 0)
            assertTrue("Total latency should be tracked", result.metrics.totalLatencyMs > 0)
            assertTrue("Memory usage should be tracked", result.metrics.memoryUsageKb >= 0)

        } finally {
            ocrPipeline.close()
        }
    }
}
