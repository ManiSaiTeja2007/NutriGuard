package com.example.verification

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.config.FeatureFlags
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.AllergenInterpreter
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.MetadataInterpretation
import com.example.core.intelligence.NutritionInterpretation
import com.example.core.intelligence.StorageInterpretation
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.confidence.DatasetProvenance
import com.example.core.pipeline.PipelineConfig
import com.example.core.pipeline.PipelineMode
import com.example.core.pipeline.PipelineRunner
import com.example.core.pipeline.SemanticIngredient
import com.example.core.pipeline.SemanticPipeline
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
            } else if (line.startsWith("[FAILURE_TAGS]")) {
                currentSection = "TAGS"
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

    private fun loadFailureCases(context: android.content.Context, fileName: String): List<FailureTestCase> {
        val jsonString = context.assets.open("packaging_failures/$fileName").bufferedReader().use { it.readText() }
        val array = JSONArray(jsonString)
        val cases = mutableListOf<FailureTestCase>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val expectedIngs = mutableListOf<String>()
            val expectedIngsArr = obj.optJSONArray("expected_ingredients")
            if (expectedIngsArr != null) {
                for (j in 0 until expectedIngsArr.length()) {
                    expectedIngs.add(expectedIngsArr.getString(j).lowercase(Locale.ROOT))
                }
            }

            val expectedAllergens = mutableListOf<String>()
            val expectedAllergensArr = obj.optJSONArray("expected_allergens")
            if (expectedAllergensArr != null) {
                for (j in 0 until expectedAllergensArr.length()) {
                    expectedAllergens.add(expectedAllergensArr.getString(j).lowercase(Locale.ROOT))
                }
            }

            val expectedNutrition = mutableMapOf<String, String>()
            val expectedNutJson = obj.optJSONObject("expected_nutrition")
            if (expectedNutJson != null) {
                val keys = expectedNutJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    expectedNutrition[key.lowercase(Locale.ROOT)] = expectedNutJson.getString(key).lowercase(Locale.ROOT)
                }
            }

            val expectedWarnings = mutableListOf<String>()
            val expectedWarningsArr = obj.optJSONArray("expected_warnings")
            if (expectedWarningsArr != null) {
                for (j in 0 until expectedWarningsArr.length()) {
                    expectedWarnings.add(expectedWarningsArr.getString(j).lowercase(Locale.ROOT))
                }
            }

            val expectedStorage = mutableListOf<String>()
            val expectedStorageArr = obj.optJSONArray("expected_storage")
            if (expectedStorageArr != null) {
                for (j in 0 until expectedStorageArr.length()) {
                    expectedStorage.add(expectedStorageArr.getString(j).lowercase(Locale.ROOT))
                }
            }

            cases.add(
                FailureTestCase(
                    failureId = obj.getString("failure_id"),
                    observedText = obj.getString("observed_text"),
                    expectedDomain = obj.getString("expected_domain"),
                    expectedIngredients = expectedIngs,
                    expectedAllergens = expectedAllergens,
                    expectedNutrition = expectedNutrition,
                    expectedWarnings = expectedWarnings,
                    expectedStorage = expectedStorage,
                    expectedManufacturer = obj.optString("expected_manufacturer", null)?.lowercase(Locale.ROOT)
                )
            )
        }
        return cases
    }

    private suspend fun runGraphOnText(
        observedText: String,
        semanticPipeline: SemanticPipeline
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
            ocrMetadata = OcrMetadata(0.9f, 0f, 0f, 0f)
        )
        context.targetedOcrLines.addAll(lines)

        val classifier = SemanticSectionClassifier()
        val router = SemanticRouter()
        val specializedStage = SpecializedInterpretationStage(semanticPipeline)
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

    @Test
    fun executePackagingValidationSuite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vocabulary = IngredientVocabulary()
        val semanticPipeline = SemanticPipeline(vocabulary)
        val ocrPipeline = com.example.core.pipeline.OCRPipeline()
        val pipelineRunner = PipelineRunner(ocrPipeline, semanticPipeline)

        // Metrics stores
        val domains = listOf("Ingredients", "Allergens", "Nutrition", "Warnings", "Storage", "Manufacturer")
        val legacyMetrics = domains.associateWith { ValidationMetrics(it) }
        val graphMetrics = domains.associateWith { ValidationMetrics(it) }

        // 1. Run validation on real image assets
        val testImages = listOf(
            "datasets/raw/clean_labels/label_000006.jpg" to "datasets/raw/clean_labels/label_000006.txt",
            "datasets/raw/clean_labels/label_000007.jpg" to "datasets/raw/clean_labels/label_000007.txt",
            "datasets/raw/clean_labels/label_000008.jpg" to "datasets/raw/clean_labels/label_000008.txt"
        )

        println("=== STAGE 13.1 IMAGE VALIDATION TESTS ===")

        for ((imgRelPath, txtRelPath) in testImages) {
            val txtContent = context.assets.open(txtRelPath).bufferedReader().use { it.readText() }
            val gt = parseImageAnnotation(txtContent)

            val bitmap = context.assets.open(imgRelPath).use { BitmapFactory.decodeStream(it) }
            assertTrue("Failed to load bitmap asset $imgRelPath", bitmap != null)

            // Run graph on bitmap to verify end-to-end runtime execution
            val config = PipelineConfig(mode = PipelineMode.DEVELOPER, enableReplay = false, enableMetrics = false)
            val graphResult = pipelineRunner.run(bitmap!!, 0, ImageSource.TEST_ASSET, config, context)
            if (graphResult.ocrLines.isEmpty()) {
                println("DEBUG_VAL: ocrLines is empty! Failures: ${graphResult.failures.map { "${it.stage}: ${it.details}" }}")
                println("DEBUG_VAL: Preprocessing Profile: complexity=${graphResult.preprocessingProfile.complexityRating}, strategy=${graphResult.preprocessingProfile.routedStrategy}")
            }
            assertTrue("OCR lines should be parsed from image. Failures: ${graphResult.failures}", graphResult.ocrLines.isNotEmpty())

            // Run graph on raw annotation text to measure metrics matching the text ground-truth
            val graphResText = runGraphOnText(gt.rawText, semanticPipeline)

            // Run legacy
            val legacyInput = gt.rawText
            val legacyResult = semanticPipeline(Pair(legacyInput, OcrMetadata(0.8f, 0f, 0f, 0f)))

            // --- Compare Ingredients ---
            val expectedIngs = gt.expectedCanonical
            val legacyIngs = legacyResult.correction.output.map { it.canonical.lowercase(Locale.ROOT) }
            val graphIngs = graphResText.semanticIngredients.map { it.canonical.lowercase(Locale.ROOT) }

            // Legacy metrics updates
            val lm = legacyMetrics["Ingredients"]!!
            expectedIngs.forEach { expected ->
                if (legacyIngs.contains(expected)) lm.tp++ else lm.fn++
            }
            legacyIngs.forEach { act ->
                if (!expectedIngs.contains(act)) lm.fp++
            }

            // Graph metrics updates
            val gm = graphMetrics["Ingredients"]!!
            expectedIngs.forEach { expected ->
                if (graphIngs.contains(expected)) gm.tp++ else gm.fn++
            }
            graphIngs.forEach { act ->
                if (!expectedIngs.contains(act)) gm.fp++
            }

            // --- Compare Nutrition ---
            // Legacy has no nutrition parsing, so all expected keys represent False Negatives
            val ln = legacyMetrics["Nutrition"]!!
            gt.expectedNutrition.forEach { _ -> ln.fn++ }

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

            println("Validated image: $imgRelPath. Expected ingredients: ${expectedIngs.size}. Legacy ingredients parsed: ${legacyIngs.size}. Graph ingredients parsed: ${graphIngs.size}")
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

        println("\n=== STAGE 13.1 FAILURE CORPUS TEXT VALIDATION TESTS ===")

        for (fileName in failureFiles) {
            val cases = loadFailureCases(context, fileName)
            for (case in cases) {
                // Run legacy
                val legacyResult = semanticPipeline(Pair(case.observedText, OcrMetadata(0.8f, 0f, 0f, 0f)))
                val legacyIngs = legacyResult.correction.output.map { it.canonical.lowercase(Locale.ROOT) }

                // Run graph
                val graphRes = runGraphOnText(case.observedText, semanticPipeline)
                val graphIngs = graphRes.semanticIngredients.map { it.canonical.lowercase(Locale.ROOT) }

                // Update INGREDIENTS metrics
                // For non-ingredient domains, expectedIngredients is empty.
                // Any ingredient produced by the legacy pipeline represents a False Positive!
                val lmIng = legacyMetrics["Ingredients"]!!
                case.expectedIngredients.forEach { expected ->
                    if (legacyIngs.contains(expected)) lmIng.tp++ else lmIng.fn++
                }
                legacyIngs.forEach { act ->
                    if (!case.expectedIngredients.contains(act)) lmIng.fp++
                }

                val gmIng = graphMetrics["Ingredients"]!!
                case.expectedIngredients.forEach { expected ->
                    if (graphIngs.contains(expected)) gmIng.tp++ else gmIng.fn++
                }
                graphIngs.forEach { act ->
                    if (!case.expectedIngredients.contains(act)) gmIng.fp++
                }

                // Update ALLERGENS metrics
                val lmAll = legacyMetrics["Allergens"]!!
                // Legacy parsed allergens via warnings
                val legacyAllergens = legacyResult.correction.output.flatMap { res ->
                    IngredientInterpreter.interpret(
                        canonicalName = res.canonical,
                        confidence = res.confidence,
                        originalToken = res.originalToken,
                        contextualReconstructionText = null,
                        baseConfidence = res.confidence,
                        provenance = DatasetProvenance.REAL_WORLD,
                        calibrationEligible = true
                    ).warnings.filter { it.startsWith("Contains allergen:") }
                }.map { it.substringAfter("Contains allergen:").trim().lowercase(Locale.ROOT) }

                case.expectedAllergens.forEach { expected ->
                    if (legacyAllergens.contains(expected)) lmAll.tp++ else lmAll.fn++
                }
                legacyAllergens.forEach { act ->
                    if (!case.expectedAllergens.contains(act)) lmAll.fp++
                }

                val gmAll = graphMetrics["Allergens"]!!
                val graphAllergens = graphRes.routingResult.allergenInterpretation?.allergensDetected ?: emptyList()
                case.expectedAllergens.forEach { expected ->
                    if (graphAllergens.contains(expected)) gmAll.tp++ else gmAll.fn++
                }
                graphAllergens.forEach { act ->
                    if (!case.expectedAllergens.contains(act)) gmAll.fp++
                }

                // Update NUTRITION metrics
                val lmNut = legacyMetrics["Nutrition"]!!
                case.expectedNutrition.forEach { _ -> lmNut.fn++ }

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
                val lmWarn = legacyMetrics["Warnings"]!!
                // Legacy warnings
                val legacyWarnings = legacyResult.correction.output.flatMap { res ->
                    IngredientInterpreter.interpret(
                        canonicalName = res.canonical,
                        confidence = res.confidence,
                        originalToken = res.originalToken,
                        contextualReconstructionText = null,
                        baseConfidence = res.confidence,
                        provenance = DatasetProvenance.REAL_WORLD,
                        calibrationEligible = true
                    ).warnings
                }.map { it.lowercase(Locale.ROOT) }

                case.expectedWarnings.forEach { expected ->
                    if (legacyWarnings.any { it.contains(expected) }) lmWarn.tp++ else lmWarn.fn++
                }
                legacyWarnings.forEach { act ->
                    if (!case.expectedWarnings.any { act.contains(it) }) lmWarn.fp++
                }

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
                val lmStor = legacyMetrics["Storage"]!!
                case.expectedStorage.forEach { _ -> lmStor.fn++ }

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
                val lmMfg = legacyMetrics["Manufacturer"]!!
                if (case.expectedManufacturer != null) lmMfg.fn++

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
        println("\n=== METRIC COMPARISON MATRIX ===")
        println(String.format("%-15s | %-12s | %-12s | %-12s | %-12s | %-6s | %-6s", "Domain", "Pipeline", "Precision", "Recall", "F1 Score", "TP", "FP"))
        println("---------------------------------------------------------------------------------------")
        for (domain in domains) {
            val lm = legacyMetrics[domain]!!
            val gm = graphMetrics[domain]!!
            println(String.format("%-15s | %-12s | %-12.4f | %-12.4f | %-12.4f | %-6d | %-6d", domain, "Legacy", lm.precision, lm.recall, lm.f1, lm.tp, lm.fp))
            println(String.format("%-15s | %-12s | %-12.4f | %-12.4f | %-12.4f | %-6d | %-6d", domain, "Graph", gm.precision, gm.recall, gm.f1, gm.tp, gm.fp))
            println("---------------------------------------------------------------------------------------")
        }

        // Broad assertion checks: Graph must outperform legacy path or equal it on ingredients, and exceed it on routing.
        val graphIngredientsF1 = graphMetrics["Ingredients"]!!.f1
        val legacyIngredientsF1 = legacyMetrics["Ingredients"]!!.f1
        assertTrue("Graph ingredient recovery (F1: $graphIngredientsF1) must be >= Legacy (F1: $legacyIngredientsF1)", graphIngredientsF1 >= legacyIngredientsF1)

        val graphAllergensF1 = graphMetrics["Allergens"]!!.f1
        val legacyAllergensF1 = legacyMetrics["Allergens"]!!.f1
        assertTrue("Graph allergen recovery (F1: $graphAllergensF1) must be >= Legacy (F1: $legacyAllergensF1)", graphAllergensF1 >= legacyAllergensF1)

        val graphNutritionF1 = graphMetrics["Nutrition"]!!.f1
        assertTrue("Graph nutrition recovery must be > 0.0", graphNutritionF1 > 0.0)

        println("\nPackaging Validation Suite completed successfully. Life Cycle State: VALIDATED.")
    }
}
