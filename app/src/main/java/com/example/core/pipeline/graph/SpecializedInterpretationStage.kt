package com.example.core.pipeline.graph

import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.ingredient.NormalizationStage
import com.example.core.ingredient.ExtractionStage
import com.example.core.ingredient.GroupingStage
import com.example.core.ingredient.PhraseCorrectionStage
import com.example.core.ingredient.CorrectionStage
import com.example.core.ingredient.IngredientIngestionResult
import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.parsing.PhraseCorrector

/**
 * SpecializedInterpretationStage is the 5th stage of the execution graph.
 * It coordinates and runs the sub-stages of ingredient semantic interpretation directly
 * using the configured [IngredientVocabulary].
 *
 * This stage replaces the legacy linear SemanticPipeline wrapper and invokes the sub-stages
 * sequentially in memory:
 * 1. Normalization: Text cleanup and standardizing formatting.
 * 2. Extraction: Extracting raw ingredient boundaries and names.
 * 3. Grouping: Recognizing groupings and structural hierarchies of ingredients.
 * 4. Phrase Correction: Correcting structural phrases and multi-word terms.
 * 5. Correction: Applying lexical checks and vocabulary alignment based on OCR quality.
 *
 * @property vocabulary The ingredient vocabulary used for matching, extraction, and correction.
 */
class SpecializedInterpretationStage(
    private val vocabulary: IngredientVocabulary
) : ExecutionStage<RoutingResult, IngredientIngestionResult?> {
    override val stageName: String = "specialized_interpretation"

    private val normalizationStage = NormalizationStage()
    private val extractionStage = ExtractionStage(vocabulary)
    private val groupingStage = GroupingStage()
    private val phraseCorrectionStage = PhraseCorrectionStage(PhraseCorrector())
    private val correctionStage = CorrectionStage(OcrCorrectionEngine(vocabulary))

    /**
     * Executes the specialized interpretation of ingredients from the OCR routing results.
     *
     * Steps:
     * 1. Collect and join all OCR text blocks routed to the INGREDIENTS domain.
     * 2. Normalize raw text using [NormalizationStage].
     * 3. Extract candidate ingredients using [ExtractionStage].
     * 4. Group list elements using [GroupingStage].
     * 5. Resolve structural corrections via [PhraseCorrectionStage].
     * 6. Perform dictionary-backed corrections and spelling alignment via [CorrectionStage].
     *
     * @param input The routing result containing segmented text blocks.
     * @param context Contextual runtime properties like execution metadata.
     * @param profiler Profiler tracker for latency and performance metrics.
     * @return ExecutionStageResult wrapping the final [IngredientIngestionResult] or null on error.
     */
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
            val normResult = normalizationStage(ingredientText)
            val extractResult = extractionStage(normResult.output)
            val groupResult = groupingStage(normResult.output)
            val phraseCorrResult = phraseCorrectionStage(extractResult.output)
            val correctionResult = correctionStage(Pair(phraseCorrResult.output, ocrMetadata))

            IngredientIngestionResult(
                normalization = normResult,
                extraction = extractResult,
                grouping = groupResult,
                phraseCorrection = phraseCorrResult,
                correction = correctionResult
            )
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
