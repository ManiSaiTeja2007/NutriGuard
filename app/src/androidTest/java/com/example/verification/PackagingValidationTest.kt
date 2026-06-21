package com.example.verification

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.pipeline.PipelineConfig
import com.example.core.pipeline.PipelineMode
import com.example.core.pipeline.PipelineRunner
import com.example.core.pipeline.SemanticIngredient
import com.example.core.pipeline.graph.AggregatedSemanticOutput
import com.example.core.pipeline.graph.AggregationStage
import com.example.core.pipeline.graph.ConfidenceCalibrationStage
import com.example.core.pipeline.graph.ContextualReconstructionStage
import com.example.core.pipeline.graph.ExecutionProfiler
import com.example.core.pipeline.graph.RoutingResult
import com.example.core.pipeline.graph.SemanticRouter
import com.example.core.pipeline.graph.SemanticRoutingContext
import com.example.core.pipeline.graph.SemanticSectionClassifier
import com.example.core.pipeline.graph.SpecializedInterpretationStage
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PackagingValidationTest {

    data class ImageGroundTruth(
        val rawText: String,
        val expectedCanonical: List<String>,
        val expectedNutrition: Map<String, String>
    )

    data class FailureTestCase(
        val failureId: String,
        val observedText: String,
        val expectedDomain: String,
        val expectedIngredients: List<String>,
        val expectedAllergens: List<String>,
        val expectedNutrition: Map<String, String>,
        val expectedWarnings: List<String>,
        val expectedStorage: List<String>,
        val expectedManufacturer: String?
    )

    data class ValidationMetrics(
        val domain: String,
        var tp: Int = 0,
        var fp: Int = 0,
        var fn: Int = 0
    ) {
        val precision: Double get() = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val recall: Double get() = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        val f1: Double get() = if (precision + recall > 0.0) 2.0 * precision * recall / (precision + recall) else 0.0
        val accuracy: Double get() = if (tp + fp + fn > 0) tp.toDouble() / (tp + fp + fn) else 1.0
    }

    /**
     * Parses a plaintext ground-truth annotation file into an [ImageGroundTruth] object.
     * Extracts expected canonical ingredients, nutrition values, and raw ingredient block text.
     *
     * @param content Plaintext content of the annotation file.
     * @return [ImageGroundTruth] structured record.
     */
    private fun parseImageAnnotation(content: String): ImageGroundTruth {
        val lines = content.split("\n").map { it.trim() }
        var rawText = ""
        val expectedCanonical = mutableListOf<String>()
        val expectedNutrition = mutableMapOf<String, String>()
        
        var currentSection = ""
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("[RAW INGREDIENTS]")) {
                currentSection = "RAW"
                continue
            } else if (line.startsWith("[EXPECTED CANONICAL]")) {
                currentSection = "CANONICAL"
                continue
            } else if (line.startsWith("[NUTRITION VALUES]")) {
                currentSection = "NUTRITION"
                continue
            }
            
            when (currentSection) {
                "RAW" -> {
                    rawText = line
                }
                "CANONICAL" -> {
                    expectedCanonical.add(line.lowercase(Locale.ROOT))
                }
                "NUTRITION" -> {
                    val parts = line.split(":")
                    if (parts.size >= 2) {
                        expectedNutrition[parts[0].trim().lowercase(Locale.ROOT)] = parts[1].trim().lowercase(Locale.ROOT)
                    }
                }
            }
        }
        return ImageGroundTruth(rawText, expectedCanonical, expectedNutrition)
    }

    /**
     * Loads and parses validation test cases from a specified JSON asset file.
     *
     * @param context Android context for asset resolution.
     * @param filename Target JSON file under the `packaging_failures/` asset folder.
     * @return List of parsed [FailureTestCase] items.
     */
    private fun loadFailureCases(context: android.content.Context, filename: String): List<FailureTestCase> {
        val cases = mutableListOf<FailureTestCase>()
        val fileContent = context.assets.open("packaging_failures/$filename").bufferedReader().use { it.readText() }
        val casesArray = JSONArray(fileContent)
        
        for (i in 0 until casesArray.length()) {
            val obj = casesArray.getJSONObject(i)
            val expectedIngredients = mutableListOf<String>()
            obj.optJSONArray("expected_ingredients")?.let { arr ->
                for (j in 0 until arr.length()) expectedIngredients.add(arr.getString(j).lowercase(Locale.ROOT))
            }
            val expectedAllergens = mutableListOf<String>()
            obj.optJSONArray("expected_allergens")?.let { arr ->
                for (j in 0 until arr.length()) expectedAllergens.add(arr.getString(j).lowercase(Locale.ROOT))
            }
            val expectedNutrition = mutableMapOf<String, String>()
            obj.optJSONObject("expected_nutrition")?.let { nut ->
                val keys = nut.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    expectedNutrition[k.lowercase(Locale.ROOT)] = nut.getString(k).lowercase(Locale.ROOT)
                }
            }
            val expectedWarnings = mutableListOf<String>()
            obj.optJSONArray("expected_warnings")?.let { arr ->
                for (j in 0 until arr.length()) expectedWarnings.add(arr.getString(j).lowercase(Locale.ROOT))
            }
            val expectedStorage = mutableListOf<String>()
            obj.optJSONArray("expected_storage")?.let { arr ->
                for (j in 0 until arr.length()) expectedStorage.add(arr.getString(j).lowercase(Locale.ROOT))
            }

            cases.add(
                FailureTestCase(
                    failureId = obj.getString("failure_id"),
                    observedText = obj.getString("observed_text"),
                    expectedDomain = obj.getString("expected_domain"),
                    expectedIngredients = expectedIngredients,
                    expectedAllergens = expectedAllergens,
                    expectedNutrition = expectedNutrition,
                    expectedWarnings = expectedWarnings,
                    expectedStorage = expectedStorage,
                    expectedManufacturer = if (obj.has("expected_manufacturer") && !obj.isNull("expected_manufacturer")) {
                        obj.getString("expected_manufacturer").lowercase(Locale.ROOT)
                    } else {
                        null
                    }
                )
            )
        }
        return cases
    }

    /**
     * Simulates the semantic execution graph run directly on a raw text input.
     *
     * Steps:
     * 1. Convert text lines into mocked OCR word and line models.
     * 2. Build the semantic routing context with mocked OCR lines.
     * 3. Coordinate the execution of:
     *    a. SemanticSectionClassifier: Group lines into layout sections.
     *    b. SemanticRouter: Route sections to interpreters.
     *    c. SpecializedInterpretationStage: Interpret ingredients.
     *    d. ContextualReconstructionStage: Build ingredient models.
     *    e. AggregationStage: Assemble and package outputs.
     *    d. ConfidenceCalibrationStage: Compute confidence values.
     *
     * @param observedText Raw text input representing the OCR output.
     * @param vocabulary Ingredient vocabulary reference.
     * @return [RoutingResultAndIngredients] containing routed and parsed semantic outputs.
     */
    private suspend fun runGraphOnText(
        observedText: String,
        vocabulary: IngredientVocabulary
    ): RoutingResultAndIngredients {
        val lines = observedText.split("\n").mapIndexed { lineIndex, lineText ->
            val words = lineText.split(" ").mapIndexed { wordIndex, wordText ->
                com.example.core.ocr.OCRWord(
                    text = wordText,
                    confidence = 0.9f,
                    bounds = android.graphics.Rect(wordIndex * 10, lineIndex * 20, (wordIndex + 1) * 10, (lineIndex + 1) * 20)
                )
            }
            com.example.core.ocr.OCRLine(
                words = words,
                bounds = android.graphics.Rect(0, lineIndex * 20, 200, (lineIndex + 1) * 20),
                confidence = 0.9f
            )
        }

        val context = SemanticRoutingContext(
            executionId = UUID.randomUUID(),
            imageWidth = 500,
            imageHeight = 500,
            ocrMetadata = com.example.core.intelligence.correction.OcrMetadata(0.9f, 0f, 0f, 0f)
        )
        context.targetedOcrLines.addAll(lines)

        val classifier = SemanticSectionClassifier()
        val router = SemanticRouter()
        val specializedStage = SpecializedInterpretationStage(vocabulary)
        val reconstructionStage = ContextualReconstructionStage(PipelineConfig())
        val aggregationStage = AggregationStage()
        val calibrationStage = ConfidenceCalibrationStage(PipelineConfig())

        val profiler = ExecutionProfiler()
        classifier.execute(Unit, context, profiler)
        val routingResult = router.execute(Unit, context, profiler).output ?: RoutingResult(null, null, null, null, emptyList())
        context.metadata["routingResult"] = routingResult
        
        val interpretationResult = specializedStage.execute(routingResult, context, profiler)
        val reconstructionResult = reconstructionStage.execute(interpretationResult.output, context, profiler)
        val aggregationResult = aggregationStage.execute(reconstructionResult.output ?: emptyList(), context, profiler)
        val calibrationResult = calibrationStage.execute(
            aggregationResult.output ?: AggregatedSemanticOutput(emptyList(), null, null, null, null),
            context,
            profiler
        )

        return RoutingResultAndIngredients(
            routingResult = routingResult,
            semanticIngredients = reconstructionResult.output ?: emptyList(),
            interpretedIngredients = calibrationResult.output ?: emptyList()
        )
    }

    data class RoutingResultAndIngredients(
        val routingResult: RoutingResult,
        val semanticIngredients: List<SemanticIngredient>,
        val interpretedIngredients: List<com.example.core.intelligence.InterpretedIngredient>
    )

    /**
     * Executes the comprehensive end-to-end integration and scorecard calculation.
     *
     * Steps:
     * 1. Execute end-to-end processing on raw image assets to check bitmap loading and basic runtime execution.
     * 2. Parse a wide failure corpus (JSON records detailing known failure edge cases) to test semantic routing boundaries.
     * 3. Evaluate true positives (TP), false positives (FP), and false negatives (FN) across 6 semantic domains.
     * 4. Log a metric comparison matrix detailing precision, recall, and F1 score per domain.
     * 5. Enforce baseline recovery thresholds to assert pipeline performance.
     */
    @Test
    fun executePackagingValidationSuite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vocabulary = IngredientVocabulary()
        val ocrPipeline = com.example.core.pipeline.OCRPipeline()
        val pipelineRunner = PipelineRunner(ocrPipeline, vocabulary)

        // Metrics stores
        val domains = listOf("Ingredients", "Allergens", "Nutrition", "Warnings", "Storage", "Manufacturer")
        val graphMetrics = domains.associateWith { ValidationMetrics(it) }

        // 1. Run validation on real image assets
        val testImages = listOf(
            "datasets/raw/clean_labels/label_000006.jpg" to "datasets/raw/clean_labels/label_000006.txt",
            "datasets/raw/clean_labels/label_000007.jpg" to "datasets/raw/clean_labels/label_000007.txt",
            "datasets/raw/clean_labels/label_000008.jpg" to "datasets/raw/clean_labels/label_000008.txt"
        )

        println("=== STAGE 13.0E IMAGE VALIDATION TESTS ===")

        for ((imgRelPath, txtRelPath) in testImages) {
            val txtContent = context.assets.open(txtRelPath).bufferedReader().use { it.readText() }
            val gt = parseImageAnnotation(txtContent)

            val bitmap = context.assets.open(imgRelPath).use { BitmapFactory.decodeStream(it) }
            assertTrue("Failed to load bitmap asset $imgRelPath", bitmap != null)

            // Run graph on bitmap to verify end-to-end runtime execution
            val config = PipelineConfig(mode = PipelineMode.DEVELOPER, enableReplay = false, enableMetrics = false)
            val graphResult = pipelineRunner.run(bitmap!!, 0, ImageSource.TEST_ASSET, config, context)
            assertTrue("OCR lines should be parsed from image. Failures: ${graphResult.failures}", graphResult.ocrLines.isNotEmpty())

            // Run graph on raw annotation text to measure metrics matching the text ground-truth
            val graphResText = runGraphOnText(gt.rawText, vocabulary)

            // --- Compare Ingredients ---
            val expectedIngs = gt.expectedCanonical
            val graphIngs = graphResText.semanticIngredients.map { it.canonical.lowercase(Locale.ROOT) }

            // Graph metrics updates
            val gm = graphMetrics["Ingredients"]!!
            expectedIngs.forEach { expected ->
                if (graphIngs.contains(expected)) gm.tp++ else gm.fn++
            }
            graphIngs.forEach { act ->
                if (!expectedIngs.contains(act)) gm.fp++
            }

            // --- Compare Nutrition ---
            val gn = graphMetrics["Nutrition"]!!
            val graphNutrients = graphResText.routingResult.nutritionInterpretation?.nutrients ?: emptyMap()
            gt.expectedNutrition.forEach { (k, v) ->
                val actualVal = graphNutrients[k]
                if (actualVal != null && actualVal.contains(v)) {
                    gn.tp++
                } else {
                    gn.fn++
                }
            }
            graphNutrients.forEach { (k, _) ->
                if (!gt.expectedNutrition.containsKey(k)) gn.fp++
            }

            println("Validated image: $imgRelPath. Expected ingredients: ${expectedIngs.size}. Graph ingredients parsed: ${graphIngs.size}")
            bitmap.recycle()
        }

        // 2. Run validation on expanded Failure Corpus
        val failureFiles = listOf(
            "allergy_advice_as_ingredient.json",
            "warning_as_ingredient.json",
            "nutrition_as_ingredient.json",
            "manufacturer_as_ingredient.json",
            "storage_as_ingredient.json",
            "marketing_as_ingredient.json"
        )

        println("\n=== STAGE 13.0E FAILURE CORPUS TEXT VALIDATION TESTS ===")

        for (fileName in failureFiles) {
            val cases = loadFailureCases(context, fileName)
            for (case in cases) {
                // Run graph
                val graphRes = runGraphOnText(case.observedText, vocabulary)
                val graphIngs = graphRes.semanticIngredients.map { it.canonical.lowercase(Locale.ROOT) }

                // Update INGREDIENTS metrics
                val gmIng = graphMetrics["Ingredients"]!!
                case.expectedIngredients.forEach { expected ->
                    if (graphIngs.contains(expected)) gmIng.tp++ else gmIng.fn++
                }
                graphIngs.forEach { act ->
                    if (!case.expectedIngredients.contains(act)) gmIng.fp++
                }

                // Update ALLERGENS metrics
                val gmAll = graphMetrics["Allergens"]!!
                val graphAllergens = graphRes.routingResult.allergenInterpretation?.allergensDetected ?: emptyList()
                case.expectedAllergens.forEach { expected ->
                    if (graphAllergens.contains(expected)) gmAll.tp++ else gmAll.fn++
                }
                graphAllergens.forEach { act ->
                    if (!case.expectedAllergens.contains(act)) gmAll.fp++
                }

                // Update NUTRITION metrics
                val gmNut = graphMetrics["Nutrition"]!!
                val graphNutrients = graphRes.routingResult.nutritionInterpretation?.nutrients ?: emptyMap()
                case.expectedNutrition.forEach { (k, v) ->
                    val actualVal = graphNutrients[k]
                    if (actualVal != null && actualVal.contains(v)) gmNut.tp++ else gmNut.fn++
                }
                graphNutrients.forEach { (k, _) ->
                    if (!case.expectedNutrition.containsKey(k)) gmNut.fp++
                }

                // Update WARNINGS metrics
                val gmWarn = graphMetrics["Warnings"]!!
                val graphWarnings = mutableListOf<String>()
                graphRes.interpretedIngredients.forEach { graphWarnings.addAll(it.warnings) }
                graphRes.routingResult.nutritionInterpretation?.warnings?.let { graphWarnings.addAll(it) }
                graphRes.routingResult.allergenInterpretation?.warnings?.let { graphWarnings.addAll(it) }
                val graphWarningsLower = graphWarnings.map { it.lowercase(Locale.ROOT) }

                case.expectedWarnings.forEach { expected ->
                    if (graphWarningsLower.any { it.contains(expected) }) gmWarn.tp++ else gmWarn.fn++
                }
                graphWarningsLower.forEach { act ->
                    if (!case.expectedWarnings.any { act.contains(it) }) gmWarn.fp++
                }

                // Update STORAGE metrics
                val gmStor = graphMetrics["Storage"]!!
                val graphStorage = graphRes.routingResult.storageInterpretation?.instructions ?: emptyList()
                val graphStorageLower = graphStorage.map { it.lowercase(Locale.ROOT) }
                case.expectedStorage.forEach { expected ->
                    if (graphStorageLower.any { it.contains(expected) }) gmStor.tp++ else gmStor.fn++
                }
                graphStorageLower.forEach { act ->
                    if (!case.expectedStorage.any { act.contains(it) }) gmStor.fp++
                }

                // Update MANUFACTURER metrics
                val gmMfg = graphMetrics["Manufacturer"]!!
                val graphMfg = graphRes.routingResult.metadataInterpretation?.manufacturer?.lowercase(Locale.ROOT)
                if (case.expectedManufacturer != null) {
                    if (graphMfg != null && graphMfg.contains(case.expectedManufacturer)) gmMfg.tp++ else gmMfg.fn++
                } else {
                    if (graphMfg != null) gmMfg.fp++
                }

                println("Validated Failure case: ${case.failureId}. Domain: ${case.expectedDomain}. Observed Text length: ${case.observedText.length}")
            }
        }

        // Print final metric tables
        println("\n=== METRIC MATRIX ===")
        println(String.format("%-15s | %-12s | %-12s | %-12s | %-12s | %-6s | %-6s", "Domain", "Pipeline", "Precision", "Recall", "F1 Score", "TP", "FP"))
        println("---------------------------------------------------------------------------------------")
        for (domain in domains) {
            val gm = graphMetrics[domain]!!
            println(String.format("%-15s | %-12s | %-12.4f | %-12.4f | %-12.4f | %-6d | %-6d", domain, "Graph", gm.precision, gm.recall, gm.f1, gm.tp, gm.fp))
            println("---------------------------------------------------------------------------------------")
        }

        // Sane threshold assertion checks
        val graphIngredientsF1 = graphMetrics["Ingredients"]!!.f1
        assertTrue("Graph ingredient recovery (F1: $graphIngredientsF1) must be > 0.0", graphIngredientsF1 > 0.0)

        val graphAllergensF1 = graphMetrics["Allergens"]!!.f1
        assertTrue("Graph allergen recovery (F1: $graphAllergensF1) must be > 0.0", graphAllergensF1 > 0.0)

        val graphNutritionF1 = graphMetrics["Nutrition"]!!.f1
        assertTrue("Graph nutrition recovery (F1: $graphNutritionF1) must be > 0.0", graphNutritionF1 > 0.0)

        println("\nPackaging Validation Suite completed successfully. Life Cycle State: VALIDATED.")
    }
}
