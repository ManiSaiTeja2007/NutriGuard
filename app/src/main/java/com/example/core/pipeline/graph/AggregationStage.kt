package com.example.core.pipeline.graph

import com.example.core.pipeline.SemanticIngredient
import com.example.core.intelligence.AllergenInterpretation
import com.example.core.intelligence.NutritionInterpretation
import com.example.core.intelligence.StorageInterpretation
import com.example.core.intelligence.MetadataInterpretation

data class AggregatedSemanticOutput(
    val ingredients: List<SemanticIngredient>,
    val allergenInterpretation: AllergenInterpretation?,
    val nutritionInterpretation: NutritionInterpretation?,
    val storageInterpretation: StorageInterpretation?,
    val metadataInterpretation: MetadataInterpretation?
)

class AggregationStage : ExecutionStage<List<SemanticIngredient>, AggregatedSemanticOutput> {
    override val stageName: String = "aggregation"

    override suspend fun execute(
        input: List<SemanticIngredient>,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<AggregatedSemanticOutput> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        val routingStageResult = context.metadata["routingResult"] as? RoutingResult
        val allergen = routingStageResult?.allergenInterpretation
        val nutrition = routingStageResult?.nutritionInterpretation
        val storage = routingStageResult?.storageInterpretation
        val metadata = routingStageResult?.metadataInterpretation

        val output = AggregatedSemanticOutput(
            ingredients = input,
            allergenInterpretation = allergen,
            nutritionInterpretation = nutrition,
            storageInterpretation = storage,
            metadataInterpretation = metadata
        )

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = output,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "ingredientsCount" to input.size,
                "hasAllergen" to (allergen != null),
                "hasNutrition" to (nutrition != null)
            ),
            failures = failures
        )
    }
}
