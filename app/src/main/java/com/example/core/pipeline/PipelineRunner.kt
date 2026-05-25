package com.example.core.pipeline

import android.graphics.Bitmap
import android.os.SystemClock
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageFrame
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.metrics.MetricsCollector
import com.example.core.replay.ReplayCollector
import java.util.UUID

class PipelineRunner(
    private val ocrPipeline: OCRPipeline,
    private val semanticPipeline: SemanticPipeline
) {

    suspend fun run(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        source: ImageSource = ImageSource.CAMERA_X,
        config: PipelineConfig = PipelineConfig()
    ): PipelineResult {
        val executionId = PipelineExecutionId.generate()
        val metricsCollector = MetricsCollector()
        val replayCollector = ReplayCollector()

        val totalStart = SystemClock.elapsedRealtime()

        // 1. Create Frame & Run OCR
        val frame = ImageFrame.BitmapFrame(
            bitmap = bitmap,
            rotationDegrees = rotationDegrees,
            timestampNanos = System.nanoTime(),
            source = source
        )
        val frameResult = FrameAnalysisResult(
            width = bitmap.width,
            height = bitmap.height,
            rotationDegrees = rotationDegrees,
            timestampNanos = frame.timestampNanos,
            source = source,
            hasBitmap = true,
            processingLatencyMs = 0L
        )

        val ocrStart = SystemClock.elapsedRealtime()
        val ocrResult = ocrPipeline(Pair(frame, frameResult))
        val ocrLatency = SystemClock.elapsedRealtime() - ocrStart
        metricsCollector.recordLatency("ocr", ocrLatency)

        if (config.enableReplay) {
            replayCollector.addStage(
                stageName = "ocr",
                input = "image_${bitmap.width}x${bitmap.height}",
                output = ocrResult.text,
                latencyMs = ocrLatency
            )
        }

        // 2. Run Semantic Ingestion
        val ocrMetadata = OcrMetadata(
            ocrConfidence = ocrResult.averageConfidence ?: 0.8f,
            blurScore = ocrResult.blurScore,
            contrastScore = ocrResult.contrastScore,
            brightnessScore = ocrResult.brightnessScore
        )

        val semStart = SystemClock.elapsedRealtime()
        val semanticResult = semanticPipeline(Pair(ocrResult.text, ocrMetadata))
        val semLatency = SystemClock.elapsedRealtime() - semStart
        metricsCollector.recordLatency("semantic", semLatency)

        metricsCollector.recordLatency("normalization", semanticResult.normalization.latencyMs)
        metricsCollector.recordLatency("extraction", semanticResult.extraction.latencyMs)
        metricsCollector.recordLatency("grouping", semanticResult.grouping.latencyMs)
        metricsCollector.recordLatency("phrase_correction", semanticResult.phraseCorrection.latencyMs)
        metricsCollector.recordLatency("correction", semanticResult.correction.latencyMs)

        if (config.enableReplay) {
            replayCollector.addStage(
                stageName = "normalization",
                input = ocrResult.text,
                output = semanticResult.normalization.output,
                latencyMs = semanticResult.normalization.latencyMs
            )
            replayCollector.addStage(
                stageName = "extraction",
                input = semanticResult.normalization.output,
                output = semanticResult.extraction.output.joinToString(", "),
                latencyMs = semanticResult.extraction.latencyMs
            )
            replayCollector.addStage(
                stageName = "correction",
                input = semanticResult.phraseCorrection.output.joinToString(", "),
                output = semanticResult.correction.output.map { it.canonical }.joinToString(", "),
                latencyMs = semanticResult.correction.latencyMs
            )
        }

        val totalLatency = SystemClock.elapsedRealtime() - totalStart
        metricsCollector.recordLatency("total", totalLatency)

        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        metricsCollector.setMemoryUsage(usedMemory)

        // 3. Map to Immutable result structures
        val semanticIngredients = semanticResult.correction.output.map { result ->
            SemanticIngredient(
                canonical = result.canonical,
                originalToken = result.originalToken,
                confidence = result.confidence,
                failures = result.failures,
                debugSteps = result.debugSteps,
                phraseWindow = result.phraseWindow,
                ontologyCategory = result.ontologyCategory,
                disambiguationRule = result.disambiguationRule,
                groupPath = result.groupPath
            )
        }

        val pipelineFailures = mutableListOf<PipelineFailure>()
        ocrResult.failures.forEach {
            pipelineFailures.add(PipelineFailure(it, "ocr", "OCR failure: ${ocrResult.skippedReason ?: "low confidence/blur"}"))
        }
        semanticResult.normalization.failures.forEach {
            pipelineFailures.add(PipelineFailure(it, "normalization", "Normalization stage warning"))
        }
        semanticResult.extraction.failures.forEach {
            pipelineFailures.add(PipelineFailure(it, "extraction", "Zero tokens extracted"))
        }
        semanticIngredients.forEach { ing ->
            ing.failures.forEach { fail ->
                pipelineFailures.add(
                    PipelineFailure(
                        fail,
                        "correction",
                        "Token correction warning on '${ing.originalToken}': ${fail.name}"
                    )
                )
            }
        }

        return PipelineResult(
            executionId = executionId,
            ocrBlocks = ocrResult.ocrBlocks,
            ocrLines = ocrResult.reconstructedLines,
            semanticIngredients = semanticIngredients,
            replayTrace = replayCollector.getStages(),
            metrics = PipelineMetrics(
                ocrLatencyMs = ocrLatency,
                normalizationLatencyMs = semanticResult.normalization.latencyMs,
                extractionLatencyMs = semanticResult.extraction.latencyMs,
                groupingLatencyMs = semanticResult.grouping.latencyMs,
                phraseCorrectionLatencyMs = semanticResult.phraseCorrection.latencyMs,
                correctionLatencyMs = semanticResult.correction.latencyMs,
                totalLatencyMs = totalLatency,
                memoryUsageKb = usedMemory,
                averageConfidence = ocrResult.averageConfidence ?: 0.8f
            ),
            preprocessingProfile = PreprocessingProfile(
                blurScore = ocrResult.blurScore,
                contrastScore = ocrResult.contrastScore,
                brightnessScore = ocrResult.brightnessScore,
                complexityRating = ocrResult.complexityRating,
                routedStrategy = ocrResult.routedStrategy
            ),
            failures = pipelineFailures
        )
    }
}
