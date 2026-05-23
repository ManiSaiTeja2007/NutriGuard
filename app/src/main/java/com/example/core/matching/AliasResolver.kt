package com.example.core.matching

import com.example.core.ingredient.IngredientVocabulary
import com.example.core.utils.text.Levenshtein
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class AliasResolver(private val vocabulary: IngredientVocabulary) {

    private val commonOcrCorrections = mapOf(
        "slt" to "salt",
        "suagr" to "sugar",
        "citnc acid" to "citric acid",
        "sodlum chloride" to "sodium chloride",
        "soydum" to "sodium",
        "flourr" to "flour",
        "waterr" to "water",
        "corn syrap" to "corn syrup",
        "ascarbic" to "ascorbic",
        "monosodum" to "monosodium",
        "glutamatee" to "glutamate"
    )

    /**
     * Resolves a raw token into a list of potential correction candidates, sorted by confidence descending.
     * Enforces exact match priority, hardcoded correction map, and bounded Levenshtein fuzzy matching.
     */
    fun resolve(token: String): List<MatchCandidate> {
        val cleanToken = token.lowercase(Locale.ROOT).trim()
        if (cleanToken.isEmpty()) {
            return emptyList()
        }

        // 1. Exact match in vocabulary (highest priority)
        if (vocabulary.contains(cleanToken)) {
            return listOf(MatchCandidate(cleanToken, MatchConfidence.EXACT_MATCH))
        }

        // 2. Exact match in common OCR corrections map
        val hardcodedCorrection = commonOcrCorrections[cleanToken]
        if (hardcodedCorrection != null) {
            return listOf(MatchCandidate(hardcodedCorrection, MatchConfidence.OCR_CORRECTION_MAP))
        }

        // 3. Bounded Levenshtein matching against vocabulary
        val candidates = mutableListOf<MatchCandidate>()
        val vocab = vocabulary.getVocabulary()

        for (candidate in vocab) {
            val lenToken = cleanToken.length
            val lenCandidate = candidate.length
            val maxLen = max(lenToken, lenCandidate)

            // Length difference boundary check: if difference itself exceeds the threshold, skip Levenshtein computation
            val lengthDiff = abs(lenToken - lenCandidate)
            if (lengthDiff.toFloat() / maxLen > MatchConfidence.FUZZY_RATIO_THRESHOLD) {
                continue
            }

            val distance = Levenshtein.distance(cleanToken, candidate)
            val confidence = MatchConfidence.calculateFuzzyConfidence(cleanToken, candidate, distance)
            if (confidence > 0.0f) {
                candidates.add(MatchCandidate(candidate, confidence))
            }
        }

        // Sort candidates by confidence descending
        candidates.sortByDescending { it.confidence }

        return if (candidates.isNotEmpty()) {
            candidates
        } else {
            // If completely unknown, return original clean token with low confidence
            listOf(MatchCandidate(cleanToken, 0.5f))
        }
    }
}
