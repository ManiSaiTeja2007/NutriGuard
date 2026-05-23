package com.example.core.intelligence.parsing

import com.example.core.intelligence.correction.FailureType
import com.example.core.intelligence.correction.PipelineStageResult
import java.util.Locale

/**
 * Phrase-aware multi-token correction engine.
 *
 * Operates on a flat list of tokens and attempts correction in sliding windows:
 *   1. Trigram window (tokens[i..i+2]) — 3-token phrase candidate
 *   2. Bigram window  (tokens[i..i+1]) — 2-token phrase candidate
 *   3. Single token   (tokens[i])      — falls through to single-token correction
 *
 * For each window, the concatenated phrase (space-joined) is scored against a curated
 * phrase vocabulary using [PhraseSimilarity]. If the score ≥ ACCEPTANCE_THRESHOLD,
 * the matched tokens are merged into a single corrected token and the window is consumed.
 *
 * Safety limits:
 *   - max window size: 3 tokens
 *   - max phrase length per window: 48 characters
 *   - emits PHRASE_CORRECTION_FAILURE when no phrase candidate exceeds threshold
 *     but input clearly looks like a multi-word fragment (contains a space)
 *
 * Returns PipelineStageResult<List<String>> — a corrected flat token list
 * (some adjacent tokens may be merged into single canonical phrases).
 */
class PhraseCorrector {

    companion object {
        const val ACCEPTANCE_THRESHOLD = 0.72f
    }

    /**
     * Multi-word ingredient phrases that PhraseCorrector can recognize and merge.
     * Mirrors the vocabulary's multi-word entries plus additional phrase aliases.
     */
    private val phraseDictionary: Set<String> = setOf(
        "high fructose corn syrup",
        "monosodium glutamate",
        "sodium chloride",
        "potassium sorbate",
        "sodium benzoate",
        "calcium propionate",
        "sodium bicarbonate",
        "ammonium bicarbonate",
        "monocalcium phosphate",
        "disodium phosphate",
        "trisodium phosphate",
        "modified corn starch",
        "enriched wheat flour",
        "enriched flour",
        "wheat flour",
        "corn syrup",
        "corn syrup solids",
        "soy lecithin",
        "sunflower lecithin",
        "sunflower oil",
        "palm oil",
        "palm kernel oil",
        "canola oil",
        "soybean oil",
        "vegetable oil",
        "natural flavor",
        "artificial flavor",
        "natural and artificial flavor",
        "caramel color",
        "citric acid",
        "malic acid",
        "lactic acid",
        "tartaric acid",
        "acetic acid",
        "ascorbic acid",
        "xanthan gum",
        "guar gum",
        "carob bean gum",
        "locust bean gum",
        "reduced iron",
        "thiamine mononitrate",
        "acidity regulator",
        "acesulfame potassium",
        "potassium chloride",
        "sodium diacetate",
        "mono and diglycerides",
        "polysorbate 80",
        "titanium dioxide",
        "red 40",
        "yellow 5",
        "yellow 6",
        "blue 1",
        "folic acid",
        "calcium carbonate",
        "zinc oxide",
        "beet sugar",
        "cane sugar",
        "cane juice",
        "tapioca starch",
        "potato starch",
        "rice flour",
        "oat flour",
        "barley malt",
        "barley malt extract",
        "malt extract",
        "yeast extract",
        "whey protein",
        "milk protein",
        "soy protein",
        "pea protein",
        "sodium stearoyl lactylate",
        "calcium stearoyl lactylate",
        "partially hydrogenated soybean oil",
        "partially hydrogenated oil"
    )

    /**
     * Corrects the flat token list using phrase-aware sliding windows.
     * Returns a [PipelineStageResult] wrapping the corrected token list.
     */
    fun correct(tokens: List<String>): PipelineStageResult<List<String>> {
        val startMs = System.currentTimeMillis()
        val trace = mutableListOf<String>()
        val failures = mutableListOf<FailureType>()
        trace.add("phrase correction input: $tokens")

        val result = mutableListOf<String>()
        var i = 0

        while (i < tokens.size) {
            val remaining = tokens.size - i

            // Attempt trigram match (3 tokens)
            if (remaining >= 3) {
                val phrase = listOf(tokens[i], tokens[i + 1], tokens[i + 2])
                val match = findBestPhraseMatch(phrase, trace)
                if (match != null) {
                    trace.add("trigram merged: ${phrase.joinToString(" ")} -> \"$match\"")
                    result.add(match)
                    i += 3
                    continue
                }
            }

            // Attempt bigram match (2 tokens)
            if (remaining >= 2) {
                val phrase = listOf(tokens[i], tokens[i + 1])
                val match = findBestPhraseMatch(phrase, trace)
                if (match != null) {
                    trace.add("bigram merged: ${phrase.joinToString(" ")} -> \"$match\"")
                    result.add(match)
                    i += 2
                    continue
                }
            }

            // No phrase match — emit token as-is, flag if it looks like a split fragment
            val token = tokens[i]
            if (token.contains(' ') && !phraseDictionary.contains(token.lowercase(Locale.ROOT))) {
                failures.add(FailureType.PHRASE_CORRECTION_FAILURE)
                trace.add("phrase failure: no match for multi-word fragment \"$token\"")
            }
            result.add(token)
            i++
        }

        trace.add("phrase correction output: $result")
        val latency = System.currentTimeMillis() - startMs
        return PipelineStageResult(result, latency, trace, failures)
    }

    /**
     * Scores the concatenated phrase against all dictionary entries.
     * Returns the best-matching dictionary entry if score ≥ ACCEPTANCE_THRESHOLD, else null.
     */
    private fun findBestPhraseMatch(tokens: List<String>, trace: MutableList<String>): String? {
        val phrase = tokens.joinToString(" ").lowercase(Locale.ROOT).trim()
        if (phrase.length > 48) return null

        // Fast-path: exact dictionary hit
        if (phraseDictionary.contains(phrase)) {
            trace.add("phrase exact hit: \"$phrase\"")
            return phrase
        }

        var bestScore = 0.0f
        var bestMatch: String? = null

        for (dictPhrase in phraseDictionary) {
            val score = PhraseSimilarity.score(phrase, dictPhrase)
            if (score > bestScore) {
                bestScore = score
                bestMatch = dictPhrase
            }
        }

        return if (bestScore >= ACCEPTANCE_THRESHOLD) {
            trace.add("phrase fuzzy match: \"$phrase\" -> \"$bestMatch\" (score=${"%.2f".format(bestScore)})")
            bestMatch
        } else {
            null
        }
    }
}
