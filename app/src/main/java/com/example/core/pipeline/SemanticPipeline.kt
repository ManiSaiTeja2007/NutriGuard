package com.example.core.pipeline

import com.example.core.ingredient.NormalizationStage
import com.example.core.ingredient.ExtractionStage
import com.example.core.ingredient.GroupingStage
import com.example.core.ingredient.PhraseCorrectionStage
import com.example.core.ingredient.CorrectionStage
import com.example.core.ingredient.IngredientIngestionResult
import com.example.core.intelligence.correction.OcrCorrectionEngine
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.intelligence.parsing.PhraseCorrector
import com.example.core.intelligence.vocabulary.IngredientVocabulary

class SemanticPipeline(
    private val vocabulary: IngredientVocabulary
) : PipelineStage<Pair<String, OcrMetadata>, IngredientIngestionResult> {

    private val normalizationStage = NormalizationStage()
    private val extractionStage = ExtractionStage(vocabulary)
    private val groupingStage = GroupingStage()
    private val phraseCorrectionStage = PhraseCorrectionStage(PhraseCorrector())
    private val correctionStage = CorrectionStage(OcrCorrectionEngine(vocabulary))

    override suspend fun invoke(input: Pair<String, OcrMetadata>): IngredientIngestionResult {
        val (rawText, metadata) = input

        // Stage 1: Normalize OCR text
        val normResult = normalizationStage(rawText)

        // Stage 2: Extract raw tokens from normalized text
        val extractResult = extractionStage(normResult.output)

        // Stage 3: Parse group structure from normalized text
        val groupResult = groupingStage(normResult.output)

        // Stage 4: Phrase correction (bigram/trigram merging)
        val phraseCorrResult = phraseCorrectionStage(extractResult.output)

        // Stage 5: Semantic token correction (with contextual disambiguation)
        val correctionResult = correctionStage(Pair(phraseCorrResult.output, metadata))

        return IngredientIngestionResult(
            normalization = normResult,
            extraction = extractResult,
            grouping = groupResult,
            phraseCorrection = phraseCorrResult,
            correction = correctionResult
        )
    }
}
