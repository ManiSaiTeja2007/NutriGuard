package com.example.core.pipeline

import android.graphics.Bitmap
import android.os.SystemClock
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.IngredientInterpreter
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.metrics.MetricsCollector
import com.example.core.replay.ReplayCollector
import com.example.core.intelligence.explanation.ExplanationType
import com.example.core.pipeline.PipelineExecutionId
import com.example.core.pipeline.PipelineResult
import com.example.core.pipeline.SemanticIngredient
class PipelineRunner(
    private val ocrPipeline: OCRPipeline,
    private val semanticPipeline: SemanticPipeline
) {

    suspend fun run(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        source: ImageSource = ImageSource.CAMERA_X,
        config: PipelineConfig = PipelineConfig(),
        context: android.content.Context? = null
    ): PipelineResult {
        val executionId = PipelineExecutionId.generate()

        if (context != null) {
            com.example.core.export.PipelineSnapshotRepository.saveTempBitmap(context, bitmap, "raw")
            ocrPipeline.context = context
        }

        val totalStart = SystemClock.elapsedRealtime()

        // 1. Initialize SemanticExecutionGraph
        val graph = com.example.core.pipeline.graph.SemanticExecutionGraph(
            structuralLayoutAnalyzer = com.example.core.pipeline.graph.StructuralLayoutAnalyzer(),
            targetedOcrCoordinator = com.example.core.pipeline.graph.TargetedOcrCoordinator(ocrPipeline),
            semanticSectionClassifier = com.example.core.pipeline.graph.SemanticSectionClassifier(),
            semanticRouter = com.example.core.pipeline.graph.SemanticRouter(),
            specializedInterpretationStage = com.example.core.pipeline.graph.SpecializedInterpretationStage(semanticPipeline),
            contextualReconstructionStage = com.example.core.pipeline.graph.ContextualReconstructionStage(config),
            aggregationStage = com.example.core.pipeline.graph.AggregationStage(),
            confidenceCalibrationStage = com.example.core.pipeline.graph.ConfidenceCalibrationStage(config),
            replayGenerationStage = com.example.core.pipeline.graph.ReplayGenerationStage()
        )

        // 2. Execute graph
        val defaultOcrMetadata = OcrMetadata(0.8f, 0.0f, 0.0f, 0.0f)
        val graphResult = graph.execute(bitmap, defaultOcrMetadata, executionId)

        // Save preprocessed bitmap if targeted ocr coordinates are available
        if (context != null) {
            com.example.core.export.PipelineSnapshotRepository.saveTempBitmap(context, bitmap, "prep")
        }

        val totalLatency = SystemClock.elapsedRealtime() - totalStart

        // Collect metrics and latencies from profiler
        val profiler = graphResult.profiler
        val ocrLatency = profiler.getMetrics("targeted_ocr")?.latencyMs ?: 0L
        val normLatency = profiler.getMetrics("specialized_interpretation")?.latencyMs ?: 0L
        val extLatency = 0L
        val groupLatency = 0L
        val phraseLatency = 0L
        val corrLatency = profiler.getMetrics("contextual_reconstruction")?.latencyMs ?: 0L

        val usedMemory = profiler.getAllMetrics().values.map { it.memoryAllocatedKb }.sum()

        // Map failures
        val pipelineFailures = mutableListOf<PipelineFailure>()
        graphResult.stageResults.forEach { stageRes ->
            stageRes.failures.forEach { failStr ->
                pipelineFailures.add(
                    PipelineFailure(
                        com.example.core.intelligence.correction.FailureType.OCR_PIPELINE_ROUTING_FAILURE,
                        stageRes.stageName,
                        failStr
                    )
                )
            }
        }

        val routingStageResult = graphResult.context.metadata["routingResult"] as? com.example.core.pipeline.graph.RoutingResult

        val finalResult = PipelineResult(
            executionId = executionId,
            ocrBlocks = graphResult.ocrResult?.ocrBlocks ?: emptyList(),
            ocrLines = graphResult.ocrResult?.reconstructedLines ?: emptyList(),
            semanticIngredients = graphResult.semanticIngredients,
            interpretedIngredients = graphResult.interpretedIngredients,
            replayTrace = graphResult.replayTrace,
            metrics = PipelineMetrics(
                ocrLatencyMs = ocrLatency,
                normalizationLatencyMs = normLatency,
                extractionLatencyMs = extLatency,
                groupingLatencyMs = groupLatency,
                phraseCorrectionLatencyMs = phraseLatency,
                correctionLatencyMs = corrLatency,
                totalLatencyMs = totalLatency,
                memoryUsageKb = usedMemory,
                averageConfidence = graphResult.ocrResult?.averageConfidence ?: 0.8f
            ),
            preprocessingProfile = PreprocessingProfile(
                blurScore = graphResult.ocrResult?.blurScore ?: 0f,
                contrastScore = graphResult.ocrResult?.contrastScore ?: 0f,
                brightnessScore = graphResult.ocrResult?.brightnessScore ?: 0f,
                complexityRating = graphResult.ocrResult?.complexityRating ?: "LOW",
                routedStrategy = graphResult.ocrResult?.routedStrategy ?: "STANDARD"
            ),
            failures = pipelineFailures,
            allergenInterpretation = routingStageResult?.allergenInterpretation,
            nutritionInterpretation = routingStageResult?.nutritionInterpretation,
            storageInterpretation = routingStageResult?.storageInterpretation,
            metadataInterpretation = routingStageResult?.metadataInterpretation
        )

        if (context != null) {
            val renamedPaths = com.example.core.export.PipelineSnapshotRepository.renameTempFiles(context, executionId.toString())
            val snapshot = com.example.core.export.PipelineSnapshot(
                executionId = executionId.toString(),
                rawImagePath = renamedPaths.first,
                preprocessedImagePath = renamedPaths.second,
                result = finalResult,
                timestamp = System.currentTimeMillis(),
                scanSource = source.name
            )
            com.example.core.export.PipelineSnapshotRepository.update(snapshot)
        }

        return finalResult
    }
}
