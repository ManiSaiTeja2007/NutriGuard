package com.example.core.intelligence.alias

import com.example.core.intelligence.fuzzy.Levenshtein
import com.example.core.intelligence.fuzzy.MatchCandidate
import com.example.core.intelligence.fuzzy.MatchConfidence
import com.example.core.intelligence.fuzzy.CorrectionContext
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class AliasResolver(private val vocabulary: IngredientVocabulary) {

    /**
     * Resolves a raw token using:
     *  1. Vocabulary corruption-map / multilingual hook (base form resolution)
     *  2. Vocabulary exact match check
     *  3. Bounded Levenshtein fuzzy candidates (safe length-delta enforced)
     *
     * Returns candidates sorted by confidence descending.
     */
    fun resolve(token: String, ocrConfidence: Float = 0.8f): List<MatchCandidate> {
        val cleanToken = token.lowercase(Locale.ROOT).trim()
        if (cleanToken.isEmpty()) return emptyList()

        // 1. Direct base-form resolution (corruption map / multilingual)
        val baseForm = vocabulary.resolveBaseForm(cleanToken)
        if (baseForm != cleanToken) {
            return listOf(
                MatchCandidate(
                    candidate = baseForm,
                    confidence = MatchConfidence.OCR_CORRECTION_MAP,
                    distance = 0
                )
            )
        }

        // 2. Exact vocabulary hit
        if (vocabulary.contains(cleanToken)) {
            return listOf(MatchCandidate(cleanToken, MatchConfidence.EXACT_MATCH, distance = 0))
        }

        // 3. Bounded Levenshtein fuzzy matching
        val candidates = mutableListOf<MatchCandidate>()
        val vocab = vocabulary.getVocabulary()

        for (candidate in vocab) {
            val lenToken = cleanToken.length
            val lenCandidate = candidate.length

            // Safe length-delta guard: prevents "salt" → "sulfate" class errors
            if (abs(lenToken - lenCandidate) > 4) continue

            val distance = Levenshtein.distance(cleanToken, candidate)
            val maxLen = max(lenToken, lenCandidate)
            if (maxLen == 0) continue

            val context = CorrectionContext(
                ocrConfidence = ocrConfidence,
                tokenLength = lenToken,
                ambiguityCount = 0,    // initial pass, ambiguity computed by engine
                isKnownAbbreviation = false,
                isOntologyMapping = false
            )
            val confidence = MatchConfidence.calculateFuzzyConfidence(cleanToken, candidate, distance, context)
            if (confidence > 0.0f) {
                candidates.add(MatchCandidate(candidate, confidence, distance))
            }
        }

        candidates.sortByDescending { it.confidence }
        return candidates.ifEmpty {
            listOf(MatchCandidate(cleanToken, 0.5f, distance = Int.MAX_VALUE))
        }
    }
}
