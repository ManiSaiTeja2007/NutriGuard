package com.example.core.intelligence.parsing

import java.util.Locale

/**
 * Phrase-level similarity scorer using weighted token overlap and character trigram overlap.
 *
 * Used by [PhraseCorrector] to score multi-word phrase candidates. Unlike Levenshtein
 * (which is character-level and single-token), this scorer handles multi-word ingredients
 * where token ORDER matters but small OCR variations in individual words are expected.
 *
 * Score = α × token_overlap_ratio + β × char_trigram_overlap_ratio
 *
 * Constants:
 *   α = 0.60 (token presence is primary signal)
 *   β = 0.40 (character-level trigram overlap for fuzzy sub-word matching)
 *
 * Returns Float in [0.0, 1.0].
 * Acceptance threshold: ≥ 0.72 (configured in PhraseCorrector)
 *
 * Safety: max phrase length 48 characters; longer phrases return 0.0f.
 */
object PhraseSimilarity {

    private const val ALPHA = 0.60f
    private const val BETA = 0.40f
    private const val MAX_PHRASE_LENGTH = 48

    /**
     * Scores similarity between [query] and [candidate] as a phrase.
     * Both inputs should already be lowercased and trimmed.
     */
    fun score(query: String, candidate: String): Float {
        if (query.length > MAX_PHRASE_LENGTH || candidate.length > MAX_PHRASE_LENGTH) return 0.0f
        if (query.isEmpty() || candidate.isEmpty()) return 0.0f
        if (query == candidate) return 1.0f

        val tokenOverlap = tokenOverlapRatio(query, candidate)
        val trigramOverlap = charTrigramOverlapRatio(query, candidate)

        return (ALPHA * tokenOverlap + BETA * trigramOverlap).coerceIn(0.0f, 1.0f)
    }

    /**
     * Token Jaccard overlap: how many words are shared / union of words.
     */
    private fun tokenOverlapRatio(a: String, b: String): Float {
        val tokensA = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0f
        val intersection = tokensA.intersect(tokensB).size.toFloat()
        val union = (tokensA + tokensB).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }

    /**
     * Character trigram Jaccard overlap: handles OCR typos in individual tokens.
     * e.g. "potasium" vs "potassium" share most trigrams despite one missing 's'.
     */
    private fun charTrigramOverlapRatio(a: String, b: String): Float {
        val triA = charTrigrams(a.replace(" ", ""))
        val triB = charTrigrams(b.replace(" ", ""))
        if (triA.isEmpty() && triB.isEmpty()) return 1.0f
        val intersection = triA.intersect(triB).size.toFloat()
        val union = (triA + triB).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }

    private fun charTrigrams(s: String): Set<String> {
        if (s.length < 3) return if (s.isEmpty()) emptySet() else setOf(s)
        val result = mutableSetOf<String>()
        for (i in 0..s.length - 3) {
            result.add(s.substring(i, i + 3))
        }
        return result
    }
}
