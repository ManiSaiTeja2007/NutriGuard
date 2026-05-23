package com.example.core.intelligence.fuzzy

import kotlin.math.max

data class CorrectionContext(
    val ocrConfidence: Float,
    val tokenLength: Int,
    val ambiguityCount: Int,
    val isKnownAbbreviation: Boolean,
    val isOntologyMapping: Boolean
)

object MatchConfidence {
    const val EXACT_MATCH = 1.0f
    const val OCR_CORRECTION_MAP = 0.95f
    const val FUZZY_RATIO_THRESHOLD = 0.40f

    /**
     * Computes the weighted match confidence for a candidate correction.
     * Integrates base edit distance similarity, OCR confidence, abbreviation bonuses,
     * ontology mapping bonuses, and penalties for high ambiguity and short tokens.
     */
    fun calculateFuzzyConfidence(
        token: String,
        candidate: String,
        distance: Int,
        context: CorrectionContext
    ): Float {
        val lenToken = token.length
        val lenCandidate = candidate.length
        val maxLen = max(lenToken, lenCandidate)
        if (maxLen == 0) return 0.0f

        val ratio = distance.toFloat() / maxLen
        if (ratio > FUZZY_RATIO_THRESHOLD) {
            return 0.0f
        }

        // 1. Base Similarity
        val baseSimilarity = 1.0f - ratio

        // 2. Abbreviation and Ontology Bonuses
        val abbreviationBonus = if (context.isKnownAbbreviation) 0.15f else 0.0f
        val ontologyBonus = if (context.isOntologyMapping) 0.20f else 0.0f

        // 3. Ambiguity Penalty
        val ambiguityPenalty = 0.05f * context.ambiguityCount

        // 4. Blend OCR Confidence if provided
        val blendedOcr = if (context.ocrConfidence > 0f) {
            baseSimilarity * 0.8f + context.ocrConfidence * 0.2f
        } else {
            baseSimilarity
        }

        // Assemble final score
        var score = blendedOcr + abbreviationBonus + ontologyBonus - ambiguityPenalty

        // Short tokens require stronger certainty (apply penalty)
        if (lenToken <= 3) {
            score *= 0.75f
        }

        return score.coerceIn(0.0f, 1.0f)
    }
}
