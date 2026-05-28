package com.example.core.pipeline.graph

import com.example.core.pipeline.PipelineConfig
import com.example.core.intelligence.InterpretedIngredient
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.confidence.ConfidenceBand

class ConfidenceCalibrationStage(
    private val config: PipelineConfig
) : ExecutionStage<AggregatedSemanticOutput, List<InterpretedIngredient>> {
    override val stageName: String = "confidence_calibration"

    override suspend fun execute(
        input: AggregatedSemanticOutput,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<List<InterpretedIngredient>> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        val interpretedList = input.ingredients.map { ing ->
            val baseConfLine = ing.debugSteps.firstOrNull { it.startsWith("base confidence:") }
            val baseConfidence = baseConfLine?.substringAfter("base confidence:")?.trim()?.toFloatOrNull() ?: ing.confidence
            val contextualReconstructionText = if (ing.disambiguationRule != null || ing.debugSteps.any { it.contains("contextual bonus:") }) ing.canonical else null

            IngredientInterpreter.interpret(
                canonicalName = ing.canonical,
                confidence = ing.confidence,
                originalToken = ing.originalToken,
                contextualReconstructionText = contextualReconstructionText,
                baseConfidence = baseConfidence,
                provenance = config.provenance,
                calibrationEligible = config.calibrationEligible
            )
        }

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = interpretedList,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "calibratedCount" to interpretedList.size,
                "uncertainCount" to interpretedList.count { it.confidence == ConfidenceBand.UNCERTAIN },
                "moderateCount" to interpretedList.count { it.confidence == ConfidenceBand.MODERATE },
                "highCount" to interpretedList.count { it.confidence == ConfidenceBand.HIGH }
            ),
            failures = failures
        )
    }
}
