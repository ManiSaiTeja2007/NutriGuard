package com.example.core.ingredient

import com.example.core.normalization.TextNormalizer
import com.example.core.pipeline.PipelineStage
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.correction.FailureType
import com.example.core.intelligence.correction.PipelineStageResult
import com.example.core.intelligence.correction.CorrectionResult
import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.grouping.IngredientGroup
import com.example.core.intelligence.grouping.IngredientGroupParser
import com.example.core.intelligence.parsing.PhraseCorrector

/**
 * Full ingestion result returned by [IngredientNormalizationPipeline].
 *
 * @param normalization   Stage 1 output: normalized OCR text
 * @param extraction      Stage 2 output: raw extracted tokens
 * @param grouping        Stage 3 output: structured [IngredientGroup] tree
 * @param phraseCorrection Stage 4 output: flat token list after bigram/trigram phrase merging
 * @param correction      Stage 5 output: fully corrected [CorrectionResult] list
 */
data class IngredientIngestionResult(
    val normalization: PipelineStageResult<String>,
    val extraction: PipelineStageResult<List<String>>,
    val grouping: PipelineStageResult<List<IngredientGroup>>,
    val phraseCorrection: PipelineStageResult<List<String>>,
    val correction: PipelineStageResult<List<CorrectionResult>>
)

// ─── Stage 1: Text Normalization ─────────────────────────────────────────────

class NormalizationStage : PipelineStage<String, PipelineStageResult<String>> {
    override suspend fun invoke(input: String): PipelineStageResult<String> {
        val startTime = System.currentTimeMillis()
        val normalized = TextNormalizer.normalize(input)
        val latency = System.currentTimeMillis() - startTime
        val trace = listOf(
            "input text: \"${input.take(80)}\"",
            "normalized form: \"${normalized.take(80)}\""
        )
        val failures = mutableListOf<FailureType>()
        if (normalized.isBlank() && input.isNotBlank()) {
            failures.add(FailureType.NORMALIZATION_FAILURE)
        }
        return PipelineStageResult(normalized, latency, trace, failures)
    }
}

// ─── Stage 2: Raw Token Extraction ───────────────────────────────────────────

class ExtractionStage(
    private val vocabulary: IngredientVocabulary
) : PipelineStage<String, PipelineStageResult<List<String>>> {
    override suspend fun invoke(input: String): PipelineStageResult<List<String>> {
        val startTime = System.currentTimeMillis()
        val sectionText = IngredientExtractor.extractRawSection(input)
        val vocabSet = vocabulary.getVocabulary()
        val tokens = IngredientExtractor.tokenize(sectionText, vocabSet)
        val latency = System.currentTimeMillis() - startTime

        val trace = mutableListOf<String>()
        trace.add("extracted section: \"${sectionText.take(80)}\"")
        trace.add("tokens: ${tokens.size} -> $tokens")

        val failures = mutableListOf<FailureType>()
        if (tokens.isEmpty() && input.isNotBlank()) {
            failures.add(FailureType.EXTRACTION_FAILURE)
        }
        return PipelineStageResult(tokens, latency, trace, failures)
    }
}

// ─── Stage 3: Structured Group Parsing ───────────────────────────────────────

class GroupingStage : PipelineStage<String, PipelineStageResult<List<IngredientGroup>>> {
    override suspend fun invoke(input: String): PipelineStageResult<List<IngredientGroup>> {
        // IngredientGroupParser already returns a PipelineStageResult
        return IngredientGroupParser.parse(input)
    }
}

// ─── Stage 4: Phrase Correction ──────────────────────────────────────────────

class PhraseCorrectionStage(
    private val phraseCorrector: PhraseCorrector
) : PipelineStage<List<String>, PipelineStageResult<List<String>>> {
    override suspend fun invoke(input: List<String>): PipelineStageResult<List<String>> {
        return phraseCorrector.correct(input)
    }
}

// ─── Stage 5: Semantic Correction ────────────────────────────────────────────

class CorrectionStage(
    private val correctionEngine: OcrCorrectionEngine
) : PipelineStage<Pair<List<String>, Any>, PipelineStageResult<List<CorrectionResult>>> {
    override suspend fun invoke(input: Pair<List<String>, Any>): PipelineStageResult<List<CorrectionResult>> {
        val (tokens, metadataRaw) = input
        val metadata = when (metadataRaw) {
            is OcrMetadata -> metadataRaw
            is Float -> OcrMetadata(ocrConfidence = metadataRaw)
            else -> OcrMetadata()
        }
        val startTime = System.currentTimeMillis()
        val results = correctionEngine.correct(tokens, metadata)
        val latency = System.currentTimeMillis() - startTime

        val trace = mutableListOf<String>()
        val failures = mutableListOf<FailureType>()
        results.forEach { res ->
            trace.addAll(res.debugSteps)
            failures.addAll(res.failures)
        }
        return PipelineStageResult(results, latency, trace, failures)
    }
}

// ─── Full Pipeline Orchestrator ───────────────────────────────────────────────

/**
 * Orchestrates all 5 pipeline stages in sequence:
 *
 *   OCR text (+ ocrConfidence/OcrMetadata)
 *     ↓ NormalizationStage
 *     ↓ ExtractionStage       (flat token list)
 *     ↓ GroupingStage         (structured IngredientGroup tree — runs in parallel on raw text)
 *     ↓ PhraseCorrectionStage (bigram/trigram merge)
 *     ↓ CorrectionStage       (9-stage token correction)
 *
 * Each stage emits: output, latencyMs, debugTrace, failures.
 */
// Replaced by com.example.core.pipeline.SemanticPipeline

