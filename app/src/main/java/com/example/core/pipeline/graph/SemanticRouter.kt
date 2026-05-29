package com.example.core.pipeline.graph

import com.example.core.intelligence.AllergenInterpretation
import com.example.core.intelligence.AllergenInterpreter
import com.example.core.intelligence.MetadataInterpretation
import com.example.core.intelligence.NutritionInterpretation
import com.example.core.intelligence.NutritionInterpreter
import com.example.core.intelligence.PackagingMetadataInterpreter
import com.example.core.intelligence.StorageInterpretation
import com.example.core.intelligence.StorageInstructionInterpreter

data class RoutingResult(
    val allergenInterpretation: AllergenInterpretation?,
    val nutritionInterpretation: NutritionInterpretation?,
    val storageInterpretation: StorageInterpretation?,
    val metadataInterpretation: MetadataInterpretation?,
    val ingredientTextBlocks: List<String>
)

class SemanticRouter : ExecutionStage<Unit, RoutingResult> {
    override val stageName: String = "semantic_routing"

    override suspend fun execute(
        input: Unit,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<RoutingResult> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        var allergenResult: AllergenInterpretation? = null
        var nutritionResult: NutritionInterpretation? = null
        var storageResult: StorageInterpretation? = null
        var metadataResult: MetadataInterpretation? = null
        val ingredientTexts = mutableListOf<String>()

        for (section in context.classifiedSections) {
            val linesToProcess = if (section.bodyLines.isEmpty() && section.headerLine != null) {
                listOf(section.headerLine)
            } else {
                section.bodyLines
            }

            if (linesToProcess.isEmpty()) continue

            when (section.type) {
                SectionType.ALLERGENS -> {
                    context.routingDecisions.add("Route ${section.type} section to AllergenInterpreter")
                    allergenResult = AllergenInterpreter.interpret(linesToProcess)
                }
                SectionType.NUTRITION -> {
                    context.routingDecisions.add("Route ${section.type} section to NutritionInterpreter")
                    nutritionResult = NutritionInterpreter.interpret(linesToProcess)
                }
                SectionType.STORAGE -> {
                    context.routingDecisions.add("Route ${section.type} section to StorageInstructionInterpreter")
                    storageResult = StorageInstructionInterpreter.interpret(linesToProcess)
                }
                SectionType.MANUFACTURER -> {
                    context.routingDecisions.add("Route ${section.type} section to PackagingMetadataInterpreter")
                    metadataResult = PackagingMetadataInterpreter.interpret(linesToProcess)
                }
                SectionType.INGREDIENTS -> {
                    context.routingDecisions.add("Route ${section.type} section to Ingredient Extraction (isolate domain)")
                    val text = linesToProcess.joinToString(separator = "\n") { line ->
                        line.words.joinToString(separator = " ") { it.text }
                    }
                    if (text.isNotBlank()) {
                        ingredientTexts.add(text)
                    }
                }
                SectionType.WARNINGS -> {
                    context.routingDecisions.add("Route ${section.type} section to AllergenInterpreter/Warnings")
                    allergenResult = AllergenInterpreter.interpret(linesToProcess)
                }
                SectionType.MARKETING -> {
                    context.routingDecisions.add("Skip ${section.type} section (budgeting / low value)")
                }
                SectionType.UNKNOWN -> {
                    val fullText = linesToProcess.flatMap { it.words }.joinToString(" ") { it.text }.lowercase()
                    if (fullText.contains("ingredient") || fullText.contains("contains")) {
                        context.routingDecisions.add("Route UNKNOWN section to Ingredient Extraction (detected ingredient text)")
                        ingredientTexts.add(fullText)
                    } else if (fullText.contains("allergy") || fullText.contains("may contain")) {
                        context.routingDecisions.add("Route UNKNOWN section to AllergenInterpreter")
                        allergenResult = AllergenInterpreter.interpret(linesToProcess)
                    } else {
                        context.routingDecisions.add("Ignore UNKNOWN section (budgeting)")
                    }
                }
            }
        }

        val hasKnownSections = context.classifiedSections.any { it.type != SectionType.UNKNOWN }
        if (ingredientTexts.isEmpty() && context.targetedOcrLines.isNotEmpty() && !hasKnownSections) {
            context.routingDecisions.add("Fallback: Route all global lines to Ingredients")
            val text = context.targetedOcrLines.joinToString(separator = "\n") { line ->
                line.words.joinToString(separator = " ") { it.text }
            }
            ingredientTexts.add(text)
        }

        val result = RoutingResult(
            allergenInterpretation = allergenResult,
            nutritionInterpretation = nutritionResult,
            storageInterpretation = storageResult,
            metadataInterpretation = metadataResult,
            ingredientTextBlocks = ingredientTexts
        )

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = result,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "hasAllergens" to (allergenResult != null),
                "hasNutrition" to (nutritionResult != null),
                "hasStorage" to (storageResult != null),
                "hasMetadata" to (metadataResult != null),
                "ingredientTextsCount" to ingredientTexts.size,
                "decisions" to ArrayList(context.routingDecisions)
            ),
            failures = failures
        )
    }
}
