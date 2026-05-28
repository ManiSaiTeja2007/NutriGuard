package com.example.core.pipeline.graph

import com.example.core.ingredient.IngredientIngestionResult
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.pipeline.PipelineConfig
import com.example.core.pipeline.SemanticIngredient
import com.example.core.intelligence.explanation.ExplanationType

class ContextualReconstructionStage(
    private val config: PipelineConfig
) : ExecutionStage<IngredientIngestionResult?, List<SemanticIngredient>> {
    override val stageName: String = "contextual_reconstruction"

    override suspend fun execute(
        input: IngredientIngestionResult?,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<List<SemanticIngredient>> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        if (input == null) {
            val latency = android.os.SystemClock.elapsedRealtime() - started
            return ExecutionStageResult(context.executionId, stageName, emptyList(), latency, emptyMap(), failures)
        }

        val correctionOutput = input.correction.output
        val semanticIngredients = correctionOutput.map { result ->
            val contextualReconstructionText = if (result.confidenceStep != null) {
                if (result.confidenceStep.contextBonus > 0.0f) result.canonical else null
            } else {
                if (result.explanationHint?.type == ExplanationType.CONTEXTUAL_RECONSTRUCTION) result.canonical else null
            }
            val baseConfidence = result.confidenceStep?.baseConfidence ?: result.confidence

            val interpretation = IngredientInterpreter.interpret(
                canonicalName = result.canonical,
                confidence = result.confidence,
                originalToken = result.originalToken,
                contextualReconstructionText = contextualReconstructionText,
                baseConfidence = baseConfidence,
                provenance = config.provenance,
                calibrationEligible = config.calibrationEligible
            )
            
            val parentCat = com.example.core.intelligence.ontology.IngredientOntology.getParentCategory(result.canonical)

            SemanticIngredient(
                canonical = result.canonical,
                originalToken = result.originalToken,
                confidence = result.confidence,
                failures = result.failures,
                debugSteps = result.debugSteps,
                phraseWindow = result.phraseWindow,
                ontologyCategory = result.ontologyCategory,
                parentCategory = parentCat,
                disambiguationRule = result.disambiguationRule,
                groupPath = result.groupPath,
                interpretedCategory = interpretation.category.name,
                additiveCode = interpretation.additiveCode,
                explanation = interpretation.explanation,
                warnings = interpretation.warnings
            )
        }

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = semanticIngredients,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "ingredientsCount" to semanticIngredients.size,
                "contextualReconstructions" to semanticIngredients.filter { it.disambiguationRule != null }.map { it.canonical }
            ),
            failures = failures
        )
    }
}
