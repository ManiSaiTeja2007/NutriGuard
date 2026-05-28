package com.example.core.pipeline.graph

import com.example.core.pipeline.SemanticPipeline
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.ingredient.IngredientIngestionResult

class SpecializedInterpretationStage(
    private val semanticPipeline: SemanticPipeline
) : ExecutionStage<RoutingResult, IngredientIngestionResult?> {
    override val stageName: String = "specialized_interpretation"

    override suspend fun execute(
        input: RoutingResult,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<IngredientIngestionResult?> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        val ingredientText = input.ingredientTextBlocks.joinToString(separator = "\n")

        if (ingredientText.isBlank()) {
            val latency = android.os.SystemClock.elapsedRealtime() - started
            return ExecutionStageResult(context.executionId, stageName, null, latency, emptyMap(), failures)
        }

        val ocrMetadata = context.ocrMetadata ?: OcrMetadata(0.8f, 0f, 0f, 0f)
        val ingestionResult = try {
            semanticPipeline(Pair(ingredientText, ocrMetadata))
        } catch (e: Exception) {
            failures.add("Semantic pipeline execution failed: ${e.message}")
            null
        }

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = ingestionResult,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "ingredientText" to ingredientText,
                "correctionsCount" to (ingestionResult?.correction?.output?.size ?: 0)
            ),
            failures = failures
        )
    }
}
