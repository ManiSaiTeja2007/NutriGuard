package com.example.core.ingredient

import com.example.core.matching.AliasResolver
import com.example.core.matching.MatchCandidate
import com.example.core.ocr.NormalizedIngredient
import com.example.core.ocr.MatchType
import com.example.core.pipeline.PipelineStage
import com.example.core.normalization.TextNormalizer

class NormalizationStage : PipelineStage<String, String> {
    override suspend fun invoke(input: String): String {
        return TextNormalizer.normalize(input)
    }
}

class ExtractionStage(
    private val vocabulary: IngredientVocabulary? = null
) : PipelineStage<String, List<String>> {
    override suspend fun invoke(input: String): List<String> {
        val sectionText = IngredientExtractor.extractRawSection(input)
        val vocabSet = vocabulary?.getVocabulary() ?: emptySet()
        return IngredientExtractor.tokenize(sectionText, vocabSet)
    }
}

class AliasResolutionStage(
    private val aliasResolver: AliasResolver
) : PipelineStage<List<String>, List<Pair<String, List<MatchCandidate>>>> {
    override suspend fun invoke(input: List<String>): List<Pair<String, List<MatchCandidate>>> {
        return input.map { token ->
            token to aliasResolver.resolve(token)
        }
    }
}

class CanonicalizationStage : PipelineStage<List<Pair<String, List<MatchCandidate>>>, List<NormalizedIngredient>> {
    override suspend fun invoke(input: List<Pair<String, List<MatchCandidate>>>): List<NormalizedIngredient> {
        return input.map { (token, candidates) ->
            val bestCandidate = candidates.firstOrNull()
            val corrected = bestCandidate?.candidate ?: token
            val confidence = bestCandidate?.confidence ?: 0.5f

            val canonical = IngredientCanonicalizer.canonicalize(corrected)

            val matchType = when {
                bestCandidate == null -> MatchType.UNKNOWN
                bestCandidate.confidence == 1.0f -> MatchType.EXACT
                bestCandidate.confidence == 0.95f -> MatchType.ALIAS_MAP
                else -> MatchType.FUZZY
            }

            NormalizedIngredient(
                originalToken = token,
                correctedToken = corrected,
                canonicalToken = canonical,
                confidence = confidence,
                matchType = matchType
            )
        }
    }
}

class IngredientNormalizationPipeline(
    private val vocabulary: IngredientVocabulary
) : PipelineStage<String, List<NormalizedIngredient>> {

    private val normalizationStage = NormalizationStage()
    private val extractionStage = ExtractionStage(vocabulary)
    private val aliasResolutionStage = AliasResolutionStage(AliasResolver(vocabulary))
    private val canonicalizationStage = CanonicalizationStage()

    override suspend fun invoke(input: String): List<NormalizedIngredient> {
        val normalizedText = normalizationStage(input)
        val tokens = extractionStage(normalizedText)
        val resolved = aliasResolutionStage(tokens)
        return canonicalizationStage(resolved)
    }
}
