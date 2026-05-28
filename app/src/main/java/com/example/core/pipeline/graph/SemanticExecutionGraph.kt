package com.example.core.pipeline.graph

import android.graphics.Bitmap
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.ocr.OcrResult
import com.example.core.ingredient.IngredientIngestionResult
import com.example.core.pipeline.SemanticIngredient
import com.example.core.intelligence.InterpretedIngredient
import com.example.core.replay.ReplayStageTrace
import java.util.UUID

class SemanticExecutionGraph(
    val structuralLayoutAnalyzer: StructuralLayoutAnalyzer,
    val targetedOcrCoordinator: TargetedOcrCoordinator,
    val semanticSectionClassifier: SemanticSectionClassifier,
    val semanticRouter: SemanticRouter,
    val specializedInterpretationStage: SpecializedInterpretationStage,
    val contextualReconstructionStage: ContextualReconstructionStage,
    val aggregationStage: AggregationStage,
    val confidenceCalibrationStage: ConfidenceCalibrationStage,
    val replayGenerationStage: ReplayGenerationStage
) {
    suspend fun execute(
        bitmap: Bitmap,
        ocrMetadata: OcrMetadata,
        executionId: UUID = UUID.randomUUID()
    ): GraphResult {
        val context = SemanticRoutingContext(
            executionId = executionId,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            ocrMetadata = ocrMetadata
        )
        val profiler = ExecutionProfiler()
        val stageResults = mutableListOf<StageResult>()

        // 1. Structural Analysis
        profiler.startStage("structural_analysis")
        val structuralResult = structuralLayoutAnalyzer.execute(bitmap, context, profiler)
        profiler.endStage("structural_analysis")
        stageResults.add(structuralResult)

        // Update ocrMetadata with structural analysis metrics
        structuralResult.output?.let { metrics ->
            context.ocrMetadata = OcrMetadata(
                ocrConfidence = 0.8f,
                blurScore = metrics.blurScore,
                contrastScore = metrics.contrastScore,
                brightnessScore = metrics.brightnessScore
            )
        }

        // 2. Targeted OCR
        profiler.startStage("targeted_ocr")
        val ocrResult = targetedOcrCoordinator.execute(bitmap, context, profiler)
        profiler.endStage("targeted_ocr")
        stageResults.add(ocrResult)

        // Update ocrMetadata with actual OCR confidence
        ocrResult.output?.let { ocr ->
            val prevMetadata = context.ocrMetadata
            context.ocrMetadata = com.example.core.intelligence.correction.OcrMetadata(
                ocrConfidence = ocr.averageConfidence ?: 0.8f,
                blurScore = prevMetadata?.blurScore ?: 0f,
                contrastScore = prevMetadata?.contrastScore ?: 0f,
                brightnessScore = prevMetadata?.brightnessScore ?: 0f
            )
        }

        // 3. Section Classification
        profiler.startStage("section_classification")
        val classificationResult = semanticSectionClassifier.execute(Unit, context, profiler)
        profiler.endStage("section_classification")
        stageResults.add(classificationResult)

        // 4. Semantic Routing
        profiler.startStage("semantic_routing")
        val routingResult = semanticRouter.execute(Unit, context, profiler)
        profiler.endStage("semantic_routing")
        stageResults.add(routingResult)
        
        routingResult.output?.let {
            context.metadata["routingResult"] = it
        }

        // 5. Specialized Interpretation
        profiler.startStage("specialized_interpretation")
        val interpretationResult = specializedInterpretationStage.execute(
            routingResult.output ?: RoutingResult(null, null, null, null, emptyList()),
            context,
            profiler
        )
        profiler.endStage("specialized_interpretation")
        stageResults.add(interpretationResult)

        // 6. Contextual Reconstruction
        profiler.startStage("contextual_reconstruction")
        val reconstructionResult = contextualReconstructionStage.execute(interpretationResult.output, context, profiler)
        profiler.endStage("contextual_reconstruction")
        stageResults.add(reconstructionResult)

        // 7. Aggregation
        profiler.startStage("aggregation")
        val aggregationResult = aggregationStage.execute(reconstructionResult.output ?: emptyList(), context, profiler)
        profiler.endStage("aggregation")
        stageResults.add(aggregationResult)

        // 8. Confidence Calibration
        profiler.startStage("confidence_calibration")
        val calibrationResult = confidenceCalibrationStage.execute(
            aggregationResult.output ?: AggregatedSemanticOutput(emptyList(), null, null, null, null),
            context,
            profiler
        )
        profiler.endStage("confidence_calibration")
        stageResults.add(calibrationResult)

        // 9. Replay Generation
        context.metadata["stageResults"] = stageResults
        profiler.startStage("replay_generation")
        val replayResult = replayGenerationStage.execute(Unit, context, profiler)
        profiler.endStage("replay_generation")
        stageResults.add(replayResult)

        return GraphResult(
            executionId = executionId,
            context = context,
            stageResults = stageResults,
            profiler = profiler,
            ocrResult = ocrResult.output,
            semanticIngredients = reconstructionResult.output ?: emptyList(),
            interpretedIngredients = calibrationResult.output ?: emptyList(),
            replayTrace = replayResult.output ?: emptyList(),
            ingestionResult = interpretationResult.output
        )
    }
}

data class GraphResult(
    val executionId: UUID,
    val context: SemanticRoutingContext,
    val stageResults: List<StageResult>,
    val profiler: ExecutionProfiler,
    val ocrResult: OcrResult?,
    val semanticIngredients: List<SemanticIngredient>,
    val interpretedIngredients: List<InterpretedIngredient>,
    val replayTrace: List<ReplayStageTrace>,
    val ingestionResult: IngredientIngestionResult?
)
