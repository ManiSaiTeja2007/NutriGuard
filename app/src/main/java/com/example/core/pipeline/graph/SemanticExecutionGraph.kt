package com.example.core.pipeline.graph

import android.graphics.Bitmap
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.ocr.OcrResult
import com.example.core.ingredient.IngredientIngestionResult
import com.example.core.pipeline.SemanticIngredient
import com.example.core.intelligence.InterpretedIngredient
import com.example.core.replay.ReplayStageTrace
import java.util.UUID

/**
 * Coordinates and executes the structured processing graph of the NutriGuard system.
 * It chains stages together, keeping state in a [SemanticRoutingContext] and tracking
 * execution metrics via [ExecutionProfiler].
 */
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
    /**
     * Executes all stages of the semantic interpretation graph in order.
     *
     * Execution flow:
     * 1. Initialize [SemanticRoutingContext] and [ExecutionProfiler].
     * 2. If [preExistingOcr] is supplied, skip the initial structural analysis and targeted OCR stages,
     *    directly populating routing lines and block structures.
     * 3. Else, execute:
     *    a. [StructuralLayoutAnalyzer]: Analyze image orientation, blur, contrast, and layout zones.
     *    b. [TargetedOcrCoordinator]: Run character recognition inside identified bounds.
     * 4. Execute [SemanticSectionClassifier] to group OCR lines into logical blocks.
     * 5. Execute [SemanticRouter] to detect target semantic fields (Ingredients, Warnings, Nutrition).
     * 6. Execute [SpecializedInterpretationStage] to run normalizing, parsing, and spelling corrections.
     * 7. Execute [ContextualReconstructionStage] to map corrected tokens to semantic entities.
     * 8. Execute [AggregationStage] to aggregate multiple structured outputs.
     * 9. Execute [ConfidenceCalibrationStage] to score parsing and mapping confidence levels.
     * 10. Execute [ReplayGenerationStage] to output audit logs for regression testing.
     *
     * @param bitmap Standardized raw label bitmap.
     * @param ocrMetadata Default starting camera and light metadata.
     * @param executionId Unique UUID of this scan process session.
     * @param preExistingOcr Cached OCR results from a prior stage (skips OCR if present).
     * @return [GraphResult] containing the final output models, trace telemetry, and profiles.
     */
    suspend fun execute(
        bitmap: Bitmap,
        ocrMetadata: OcrMetadata,
        executionId: UUID = UUID.randomUUID(),
        preExistingOcr: OcrResult? = null
    ): GraphResult {
        val context = SemanticRoutingContext(
            executionId = executionId,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            ocrMetadata = ocrMetadata
        )
        val profiler = ExecutionProfiler()
        val stageResults = mutableListOf<StageResult>()

        val ocrResultOutput: OcrResult?
        if (preExistingOcr != null) {
            context.targetedOcrBlocks.addAll(preExistingOcr.ocrBlocks)
            context.targetedOcrLines.addAll(preExistingOcr.reconstructedLines)
            context.ocrMetadata = OcrMetadata(
                ocrConfidence = preExistingOcr.averageConfidence ?: 0.8f,
                blurScore = preExistingOcr.blurScore,
                contrastScore = preExistingOcr.contrastScore,
                brightnessScore = preExistingOcr.brightnessScore
            )
            
            stageResults.add(
                ExecutionStageResult(
                    executionId = executionId,
                    stageName = "structural_analysis",
                    output = StructuralLayoutAnalyzer.StructuralAnalysisResult(
                        blurScore = preExistingOcr.blurScore,
                        brightnessScore = preExistingOcr.brightnessScore,
                        contrastScore = preExistingOcr.contrastScore,
                        textDensity = 0.5f,
                        orientationDegrees = 0,
                        heatmap = emptyList(),
                        zones = emptyList(),
                        probableHeaders = emptyList()
                    ),
                    latencyMs = 0L,
                    replayArtifacts = mapOf("bypassed" to true)
                )
            )

            stageResults.add(
                ExecutionStageResult(
                    executionId = executionId,
                    stageName = "targeted_ocr",
                    output = preExistingOcr,
                    latencyMs = 0L,
                    replayArtifacts = mapOf("bypassed" to true)
                )
            )

            ocrResultOutput = preExistingOcr
        } else {
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

            ocrResultOutput = ocrResult.output
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
            ocrResult = ocrResultOutput,
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
