package com.example.core.pipeline

import com.example.core.ocr.OCRBlock
import com.example.core.ocr.OCRLine
import com.example.core.intelligence.correction.FailureType
import com.example.core.replay.ReplayStageTrace
import java.util.UUID

data class SemanticIngredient(
    val canonical: String,
    val originalToken: String,
    val confidence: Float,
    val failures: List<FailureType>,
    val debugSteps: List<String>,
    val phraseWindow: List<String> = emptyList(),
    val ontologyCategory: String? = null,
    val disambiguationRule: String? = null,
    val groupPath: String = "root"
)

data class PipelineMetrics(
    val ocrLatencyMs: Long,
    val normalizationLatencyMs: Long,
    val extractionLatencyMs: Long,
    val groupingLatencyMs: Long,
    val phraseCorrectionLatencyMs: Long,
    val correctionLatencyMs: Long,
    val totalLatencyMs: Long,
    val memoryUsageKb: Long = 0L,
    val averageConfidence: Float = 0.8f
)

data class PreprocessingProfile(
    val blurScore: Float,
    val contrastScore: Float,
    val brightnessScore: Float,
    val complexityRating: String,
    val routedStrategy: String
)

data class PipelineFailure(
    val failureType: FailureType,
    val stage: String,
    val details: String
)

data class PipelineResult(
    val executionId: UUID,
    val ocrBlocks: List<OCRBlock>,
    val ocrLines: List<OCRLine>,
    val semanticIngredients: List<SemanticIngredient>,
    val replayTrace: List<ReplayStageTrace> = emptyList(),
    val metrics: PipelineMetrics,
    val preprocessingProfile: PreprocessingProfile,
    val failures: List<PipelineFailure>
)
