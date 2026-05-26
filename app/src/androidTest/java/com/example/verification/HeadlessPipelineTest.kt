package com.example.verification

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.ontology.IngredientCategory
import com.example.core.confidence.ConfidenceBand
import com.example.core.intelligence.InterpretationFailure
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

            // Verify that interpreted ingredients are populated in the pipeline result
            assertNotNull(result.interpretedIngredients)
            assertTrue(result.interpretedIngredients.isNotEmpty())

        } finally {
            ocrPipeline.close()
        }
    }

    @Test
    fun testPipelineExportIntegrity() = runBlocking {
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
            assertNotNull("Test bitmap should be loaded successfully", bitmap)

            // Run pipeline, passing the context so a snapshot is cached
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

            // 1. Verify snapshot is stored in the repository
            val latestSnapshot = com.example.core.export.PipelineSnapshotRepository.latest()
            assertNotNull("Latest snapshot should be stored in repository", latestSnapshot)
            assertEquals(result.executionId.toString(), latestSnapshot?.executionId)

            // 2. Perform export
            val writer = com.example.core.export.ExportFileWriter(context)
            val exporter = com.example.core.export.SessionExporter(writer)
            val exportPath = exporter.export(latestSnapshot!!)
            assertNotNull("Export path should not be null", exportPath)

            val exportDir = java.io.File(exportPath!!)
            assertTrue("Export directory should exist", exportDir.exists())

            // 3. Verify directory structures
            val expectedDirs = listOf("raw", "preprocessed", "overlays", "replay", "semantic", "metrics", "metadata")
            for (dirName in expectedDirs) {
                val subDir = java.io.File(exportDir, dirName)
                assertTrue("Subdirectory '$dirName' should exist", subDir.exists() && subDir.isDirectory)
            }

            // 4. Verify manifest file and contents
            val manifestFile = java.io.File(exportDir, "manifest.json")
            assertTrue("manifest.json should exist", manifestFile.exists())
            
            val manifestContent = manifestFile.readText()
            val manifestJson = org.json.JSONObject(manifestContent)
            assertEquals(result.executionId.toString(), manifestJson.getString("executionId"))
            
            val hashes = manifestJson.getJSONObject("fileHashes")
            assertTrue("Hashes should contain overlays/overlay_bounds.json", hashes.has("overlays/overlay_bounds.json"))
            assertTrue("Hashes should contain replay/replay_trace.json", hashes.has("replay/replay_trace.json"))
            assertTrue("Hashes should contain semantic/semantic_interpretation.json", hashes.has("semantic/semantic_interpretation.json"))
            assertTrue("Hashes should contain metrics/metrics.json", hashes.has("metrics/metrics.json"))
            assertTrue("Hashes should contain metadata/metadata.json", hashes.has("metadata/metadata.json"))

            // Verify file hashes integrity
            val iter = hashes.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                val expectedHash = hashes.getString(key)
                val file = java.io.File(exportDir, key)
                assertTrue("File '$key' listed in manifest should exist", file.exists())
                val actualHash = writer.calculateSha256(file)
                assertEquals("Hash for '$key' should match manifest", expectedHash, actualHash)
            }

        } finally {
            ocrPipeline.close()
        }
    }

    @Test
    fun testIngredientInterpreterDirectly() {
        // 1. Verify E-number interpretation (Citric Acid)
        val citricAcidInterpreted = IngredientInterpreter.interpret(
            canonicalName = "citric acid",
            confidence = 0.95f,
            originalToken = "citnc acid"
        )
        assertEquals("citric acid", citricAcidInterpreted.canonicalName)
        assertEquals(IngredientCategory.ACIDITY_REGULATOR, citricAcidInterpreted.category)
        assertEquals("E330", citricAcidInterpreted.additiveCode)
        assertEquals(ConfidenceBand.HIGH, citricAcidInterpreted.confidenceBand)
        assertTrue(citricAcidInterpreted.explanation!!.contains("citric acid (E330) is acidity regulator"))
        assertTrue(citricAcidInterpreted.warnings.isEmpty()) // Citric acid has no warning tags assigned

        // 2. Verify flavour enhancer (MSG) warning mapping
        val msgInterpreted = IngredientInterpreter.interpret(
            canonicalName = "monosodium glutamate",
            confidence = 0.90f,
            originalToken = "msg"
        )
        assertEquals("monosodium glutamate", msgInterpreted.canonicalName)
        assertEquals(IngredientCategory.FLAVOUR_ENHANCER, msgInterpreted.category)
        assertEquals("E621", msgInterpreted.additiveCode)
        assertTrue(msgInterpreted.warnings.contains("contains artificial flavouring or flavor enhancers"))
        assertTrue(msgInterpreted.warnings.contains("commonly found in ultra-processed foods"))

        // 3. Verify uncertainty warning mapping for moderate/low confidence
        val lowConfInterpreted = IngredientInterpreter.interpret(
            canonicalName = "citric acid",
            confidence = 0.60f,
            originalToken = "cltrlc"
        )
        assertEquals(ConfidenceBand.LOW, lowConfInterpreted.confidenceBand)
        assertTrue(lowConfInterpreted.warnings.any { it.contains("Possible match with moderate or low confidence") })
        assertTrue(lowConfInterpreted.failures.contains(InterpretationFailure.LOW_CONFIDENCE_MATCH))

        // 4. Verify safety fallback state for unknown/garbage inputs (retains raw name, unknown category, doesn't guess)
        val unknownInterpreted = IngredientInterpreter.interpret(
            canonicalName = "unknown_garbage_token_x123",
            confidence = 0.40f,
            originalToken = "unknown_garbage_token_x123"
        )
        assertEquals("unknown_garbage_token_x123", unknownInterpreted.canonicalName)
        assertEquals(IngredientCategory.UNKNOWN, unknownInterpreted.category)
        assertEquals(ConfidenceBand.UNCERTAIN, unknownInterpreted.confidenceBand)
        assertNull(unknownInterpreted.additiveCode)
        assertTrue(unknownInterpreted.failures.contains(InterpretationFailure.ONTOLOGY_MISS))
    }
}
