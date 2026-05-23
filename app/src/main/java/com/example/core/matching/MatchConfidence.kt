package com.example.core.matching

import kotlin.math.max

object MatchConfidence {
    const val EXACT_MATCH = 1.0f
    const val OCR_CORRECTION_MAP = 0.95f
    const val FUZZY_RATIO_THRESHOLD = 0.34f

    /**
     * Calculates deterministic confidence for a fuzzy match candidate.
     * Applies ratio checks and penalizes short tokens (length <= 3) to enforce stronger certainty constraints.
     */
    fun calculateFuzzyConfidence(token: String, candidate: String, distance: Int): Float {
        val lenToken = token.length
        val lenCandidate = candidate.length
        val maxLen = max(lenToken, lenCandidate)
        if (maxLen == 0) return 0.0f

        val ratio = distance.toFloat() / maxLen
        if (ratio > FUZZY_RATIO_THRESHOLD) {
            return 0.0f
        }

        val baseConfidence = 1.0f - ratio

        // Short tokens require stronger certainty. Penalize short token fuzzy matches.
        return if (lenToken <= 3) {
            (baseConfidence * 0.75f).coerceIn(0.0f, 1.0f)
        } else {
            baseConfidence.coerceIn(0.0f, 1.0f)
        }
    }
}
