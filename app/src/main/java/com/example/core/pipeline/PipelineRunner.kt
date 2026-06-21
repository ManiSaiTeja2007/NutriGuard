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
import java.io.Closeable
import com.example.core.intelligence.vocabulary.IngredientVocabulary

/**
 * Executes the full scan pipeline: structural analysis → targeted OCR → semantic interpretation.
 *
 * Replaces the legacy sequential pipeline wrapper and coordinates the stages of the
 * execution graph:
 * 1. StructuralLayoutAnalyzer: Detects regions of interest (OCR bounding zones).
 * 2. TargetedOcrCoordinator: Runs OCR on the localized text blocks.
 * 3. SemanticSectionClassifier: Classifies sections like ingredients, allergens, nutrition.
 * 4. SemanticRouter: Routes sections to their dedicated interpreters.
 * 5. SpecializedInterpretationStage: Analyzes and cleans ingredients.
 * 6. ContextualReconstructionStage: Builds final models and formats outputs.
 * 7. AggregationStage: Collects and bundles all structural results.
 * 8. ConfidenceCalibrationStage: Computes and refines pipeline execution confidence.
 * 9. ReplayGenerationStage: Generates audit trails for offline replay.
 *
 * ISSUE-001 FIX: The SemanticExecutionGraph and all its stages (including StructuralLayoutAnalyzer
 * which holds an ML Kit TextRecognizer) are now created ONCE as instance fields, not per-scan.
 * Previously a new graph was allocated on every call to run(), causing:
 *   - A new ML Kit TextRecognizer per scan (native resource leak, ISSUE-004)
 *   - TextRecognizer initialization overhead per scan (~100-200ms)
 *   - Unbounded native memory growth (progressive slowdown)
 *
 * ISSUE-004 FIX: PipelineRunner now implements Closeable. Call close() from ScanViewModel.onCleared()
 * to release the StructuralLayoutAnalyzer's ML Kit TextRecognizer.
 *
 * @property ocrPipeline The OCR coordinator running text detection engines.
 * @property vocabulary The ingredient vocabulary used for semantic parsing.
 */
class PipelineRunner(
    private val ocrPipeline: OCRPipeline,
    private val vocabulary: IngredientVocabulary
) : Closeable {

    // ISSUE-001 FIX: Stage instances held as fields — created once, reused per scan.
    private val structuralLayoutAnalyzer = com.example.core.pipeline.graph.StructuralLayoutAnalyzer()
    private val targetedOcrCoordinator = com.example.core.pipeline.graph.TargetedOcrCoordinator(ocrPipeline)
    private val semanticSectionClassifier = com.example.core.pipeline.graph.SemanticSectionClassifier()
    private val semanticRouter = com.example.core.pipeline.graph.SemanticRouter()
    private val specializedInterpretationStage = com.example.core.pipeline.graph.SpecializedInterpretationStage(vocabulary)
    private val aggregationStage = com.example.core.pipeline.graph.AggregationStage()
    private val replayGenerationStage = com.example.core.pipeline.graph.ReplayGenerationStage()

    /**
     * Executes the pipeline on a raw scan bitmap.
     *
     * Steps:
     * 1. Initialize execution metadata and save intermediate telemetry image states if context is provided.
     * 2. Assemble the execution graph using the reusable stage instances.
     * 3. Run the semantic execution graph to produce structured data.
     * 4. Log profiling metrics (latency, memory usage) and package execution errors into [PipelineFailure] objects.
     * 5. Capture final execution telemetry snapshots.
     *
     * @param bitmap The raw image input containing label text.
     * @param rotationDegrees Rotation angle applied to align the image frame.
     * @param source The source type of the scan (e.g. CAMERA_X, GALLERY).
     * @param config The runtime pipeline configuration.
     * @param context Android context for file access and storage.
     * @param preExistingOcr An optional pre-calculated OCR result to inject directly, skipping initial OCR passes.
     * @return [PipelineResult] containing all extracted labels, metrics, and metadata.
     */
    suspend fun run(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        source: ImageSource = ImageSource.CAMERA_X,
        config: PipelineConfig = PipelineConfig(),
        context: android.content.Context? = null,
        preExistingOcr: com.example.core.ocr.OcrResult? = null
    ): PipelineResult {
        val executionId = PipelineExecutionId.generate()
 
        if (context != null) {
            com.example.core.export.PipelineSnapshotRepository.saveTempBitmap(context, bitmap, "raw")
            ocrPipeline.context = context
        }
 
        val totalStart = SystemClock.elapsedRealtime()
 
        // Build graph using reusable stage instances (config-dependent stages rebuilt per run)
        val graph = com.example.core.pipeline.graph.SemanticExecutionGraph(
            structuralLayoutAnalyzer = structuralLayoutAnalyzer,
            targetedOcrCoordinator = targetedOcrCoordinator,
            semanticSectionClassifier = semanticSectionClassifier,
            semanticRouter = semanticRouter,
            specializedInterpretationStage = specializedInterpretationStage,
            contextualReconstructionStage = com.example.core.pipeline.graph.ContextualReconstructionStage(config),
            aggregationStage = aggregationStage,
            confidenceCalibrationStage = com.example.core.pipeline.graph.ConfidenceCalibrationStage(config),
            replayGenerationStage = replayGenerationStage
        )
 
        // Execute graph
        val defaultOcrMetadata = OcrMetadata(0.8f, 0.0f, 0.0f, 0.0f)
        val graphResult = graph.execute(bitmap, defaultOcrMetadata, executionId, preExistingOcr)

        // Save preprocessed bitmap if targeted ocr coordinates are available
        if (context != null) {
            com.example.core.export.PipelineSnapshotRepository.saveTempBitmap(context, bitmap, "prep")
        }

        val totalLatency = SystemClock.elapsedRealtime() - totalStart

        // Collect metrics and latencies from profiler
        val profiler = graphResult.profiler
        val ocrLatency = profiler.getMetrics("targeted_ocr")?.latencyMs ?: 0L
        val ingestionRes = graphResult.ingestionResult
        val normLatency = ingestionRes?.normalization?.latencyMs ?: 0L
        val extLatency = ingestionRes?.extraction?.latencyMs ?: 0L
        val groupLatency = ingestionRes?.grouping?.latencyMs ?: 0L
        val phraseLatency = ingestionRes?.phraseCorrection?.latencyMs ?: 0L
        val corrLatency = ingestionRes?.correction?.latencyMs ?: 0L

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

    /**
     * Releases system and native assets by cleaning up the [StructuralLayoutAnalyzer]'s
     * internal ML Kit TextRecognizer engine. This prevents native memory leaks.
     * Must be invoked when the lifetime of this runner ends (e.g., in ScanViewModel.onCleared()).
     */
    override fun close() {
        structuralLayoutAnalyzer.close()
    }
}
