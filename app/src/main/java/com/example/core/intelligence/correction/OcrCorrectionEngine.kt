package com.example.core.intelligence.correction

import com.example.core.intelligence.contextual.ContextualDisambiguator
import com.example.core.intelligence.contextual.DisambiguationContext
import com.example.core.intelligence.fuzzy.CorrectionContext
import com.example.core.intelligence.fuzzy.Levenshtein
import com.example.core.intelligence.fuzzy.MatchCandidate
import com.example.core.intelligence.fuzzy.MatchConfidence
import com.example.core.intelligence.ontology.IngredientOntology
import com.example.core.intelligence.parsing.PhraseNormalizer
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Result of running a single token through the full 9-stage correction pipeline.
 *
 * @param canonical           Final corrected canonical form
 * @param confidence          Weighted confidence score [0.0, 1.0]
 * @param failures            Any failure types triggered during correction
 * @param debugSteps          Full waterfall trace for replay explainability
 * @param phraseWindow        The bigram/trigram window used if phrase correction fired (else empty)
 * @param ontologyCategory    Resolved ontology category (e.g. "sweeteners"), or null
 * @param disambiguationRule  The context rule ID that fired (e.g. "acid_citric"), or null
 * @param groupPath           Group hierarchy path: "root" or "root > enriched wheat flour"
 */
data class CorrectionResult(
    val canonical: String,
    val confidence: Float,
    val failures: List<FailureType>,
    val debugSteps: List<String>,
    val phraseWindow: List<String> = emptyList(),
    val ontologyCategory: String? = null,
    val disambiguationRule: String? = null,
    val groupPath: String = "root"
) {
    /** The original raw OCR token, extracted from the first debug step. */
    val originalToken: String
        get() = debugSteps.firstOrNull { it.startsWith("OCR: ") }?.substringAfter("OCR: ") ?: ""
}

/**
 * 9-stage deterministic ingredient correction engine.
 *
 * Stage order (all side-effect free):
 *  1. Normalize       — lowercase, trim
 *  2. Phrase Normalize — PhraseNormalizer (hyphen collapse, split-compound repair)
 *  3. Ontology Mapping — E-numbers, abbreviations, subclass resolution
 *  4. Vocabulary Check — exact hit or base-form resolution
 *  5. Phrase Correction  — handled pre-call by PhraseCorrector; single token arrives here
 *  6. Fuzzy Correction — Levenshtein with safe length-delta guard
 *  7. Ambiguity Check  — flag tied candidates
 *  8. Confidence Score — weighted blending
 *  9. Canonicalization — emit CorrectionResult with category + disambiguation fields
 */
class OcrCorrectionEngine(
    private val vocabulary: IngredientVocabulary
) {

    /**
     * Corrects a flat list of tokens into [CorrectionResult] entries.
     * Applies phrase-normalization per token, then full correction cascade.
     * Contextual disambiguation uses the preceding/following canonical tokens as context.
     *
     * @param tokens        Flat list of extracted tokens (post phrase-corrector merge)
     * @param ocrConfidence Average OCR confidence for this frame [0.0, 1.0]
     * @param groupPath     Group path to annotate on each result (default "root")
     */
    fun correct(
        tokens: List<String>,
        ocrConfidence: Float = 0.8f,
        groupPath: String = "root"
    ): List<CorrectionResult> {
        // First pass: correct each token independently
        val firstPass = tokens.mapIndexed { idx, token ->
            correctSingle(token, ocrConfidence, groupPath)
        }

        // Second pass: contextual disambiguation using first-pass canonicals as neighbors
        return firstPass.mapIndexed { idx, result ->
            if (result.failures.contains(FailureType.UNKNOWN_INGREDIENT_FAILURE) ||
                result.failures.contains(FailureType.AMBIGUOUS_MATCH_FAILURE)) {

                val preceding = firstPass.subList(maxOf(0, idx - 3), idx)
                    .map { it.canonical }
                val following = firstPass.subList(idx + 1, minOf(firstPass.size, idx + 4))
                    .map { it.canonical }
                val neighborCategories = (preceding + following)
                    .mapNotNull { IngredientOntology.categoryOf(it) }
                    .toSet()

                val context = DisambiguationContext(
                    precedingTokens = preceding,
                    followingTokens = following,
                    ontologyCategories = neighborCategories
                )
                val disambig = ContextualDisambiguator.disambiguate(result.canonical, context)

                if (disambig.resolvedForm != null) {
                    val newSteps = result.debugSteps.toMutableList()
                    newSteps.addAll(disambig.debugTrace)
                    newSteps.add("disambiguation: \"${disambig.resolvedForm}\" via rule ${disambig.ruleId}")
                    newSteps.add("canonicalized: ${disambig.resolvedForm}")
                    val category = IngredientOntology.categoryOf(disambig.resolvedForm)
                    result.copy(
                        canonical = disambig.resolvedForm,
                        failures = result.failures.filter { it != FailureType.AMBIGUOUS_MATCH_FAILURE },
                        debugSteps = newSteps,
                        ontologyCategory = category ?: result.ontologyCategory,
                        disambiguationRule = disambig.ruleId
                    )
                } else if (disambig.failed) {
                    val newFailures = result.failures.toMutableList()
                    if (!newFailures.contains(FailureType.CONTEXT_DISAMBIGUATION_FAILURE)) {
                        newFailures.add(FailureType.CONTEXT_DISAMBIGUATION_FAILURE)
                    }
                    val newSteps = result.debugSteps.toMutableList()
                    newSteps.addAll(disambig.debugTrace)
                    result.copy(failures = newFailures, debugSteps = newSteps)
                } else {
                    result
                }
            } else {
                result
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Private: single-token correction (stages 1-8)
    // ─────────────────────────────────────────────────────────────

    private fun correctSingle(
        token: String,
        ocrConfidence: Float,
        groupPath: String
    ): CorrectionResult {
        val debugSteps = mutableListOf<String>()
        val failures = mutableListOf<FailureType>()

        // Stage 1: Normalize
        debugSteps.add("OCR: $token")
        val cleanToken = token.lowercase(Locale.ROOT).trim()
        debugSteps.add("normalized: $cleanToken")

        // Stage 2: Phrase Normalize
        val (phraseNorm, phraseTrace) = PhraseNormalizer.normalize(cleanToken)
        debugSteps.addAll(phraseTrace)

        // Stage 3: Ontology Mapping (E-numbers / abbreviations / subclass)
        val ontologyTarget = IngredientOntology.resolve(phraseNorm)
        if (ontologyTarget != null) {
            val category = IngredientOntology.categoryOf(ontologyTarget)
            debugSteps.add("ontology: \"$ontologyTarget\"${if (category != null) " [category: $category]" else ""}")
            val confidence = MatchConfidence.calculateFuzzyConfidence(
                token = phraseNorm,
                candidate = ontologyTarget,
                distance = 0,
                context = CorrectionContext(
                    ocrConfidence = ocrConfidence,
                    tokenLength = phraseNorm.length,
                    ambiguityCount = 0,
                    isKnownAbbreviation = true,
                    isOntologyMapping = true
                )
            )
            debugSteps.add("canonicalized: $ontologyTarget")
            return CorrectionResult(
                canonical = ontologyTarget,
                confidence = confidence,
                failures = emptyList(),
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath
            )
        }

        // Stage 4: Vocabulary exact / base-form check
        if (vocabulary.contains(phraseNorm)) {
            val baseForm = vocabulary.resolveBaseForm(phraseNorm)
            val category = IngredientOntology.categoryOf(baseForm)
            debugSteps.add("vocabulary hit: \"$baseForm\"${if (category != null) " [category: $category]" else ""}")
            debugSteps.add("canonicalized: $baseForm")
            return CorrectionResult(
                canonical = baseForm,
                confidence = 1.0f,
                failures = emptyList(),
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath
            )
        }

        // Stage 5: Phrase correction (token already phrase-corrected by PhraseCorrector upstream;
        //           this stage handles any residual vocabulary corruption map hits)
        val baseForm = vocabulary.resolveBaseForm(phraseNorm)
        if (baseForm != phraseNorm) {
            val category = IngredientOntology.categoryOf(baseForm)
            debugSteps.add("corruption map: \"$baseForm\"${if (category != null) " [category: $category]" else ""}")
            debugSteps.add("canonicalized: $baseForm")
            return CorrectionResult(
                canonical = baseForm,
                confidence = MatchConfidence.OCR_CORRECTION_MAP,
                failures = emptyList(),
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath
            )
        }

        // Stage 6: Fuzzy correction (Levenshtein with safe length-delta guard)
        val candidates = mutableListOf<MatchCandidate>()
        val vocab = vocabulary.getVocabulary()

        for (candidate in vocab) {
            val lenToken = phraseNorm.length
            val lenCandidate = candidate.length
            if (abs(lenToken - lenCandidate) > 4) continue

            val distance = Levenshtein.distance(phraseNorm, candidate)
            val maxLen = max(lenToken, lenCandidate)
            if (maxLen == 0) continue

            val ratio = distance.toFloat() / maxLen
            if (ratio <= MatchConfidence.FUZZY_RATIO_THRESHOLD) {
                candidates.add(MatchCandidate(candidate, 1.0f - ratio, distance))
            }
        }
        candidates.sortByDescending { it.confidence }

        if (candidates.isEmpty()) {
            debugSteps.add("candidate: none")
            debugSteps.add("canonicalized: $phraseNorm")
            failures.add(FailureType.UNKNOWN_INGREDIENT_FAILURE)
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = 0.5f,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath
            )
        }

        // Stage 7: Ambiguity check
        val bestCandidate = candidates[0]
        val ambiguityCount = candidates.count {
            it != bestCandidate && abs(it.distance - bestCandidate.distance) <= 1
        }
        if (ambiguityCount > 0) {
            failures.add(FailureType.AMBIGUOUS_MATCH_FAILURE)
            debugSteps.add("ambiguity warning: $ambiguityCount other close matches (top candidates: ${candidates.take(3).map { it.candidate }})")
        }

        // Stage 8: Weighted confidence scoring
        val confidenceContext = CorrectionContext(
            ocrConfidence = ocrConfidence,
            tokenLength = phraseNorm.length,
            ambiguityCount = ambiguityCount,
            isKnownAbbreviation = false,
            isOntologyMapping = false
        )
        val finalConfidence = MatchConfidence.calculateFuzzyConfidence(
            token = phraseNorm,
            candidate = bestCandidate.candidate,
            distance = bestCandidate.distance,
            context = confidenceContext
        )

        debugSteps.add("candidate: \"${bestCandidate.candidate}\"")
        debugSteps.add("distance: ${bestCandidate.distance}")
        debugSteps.add("fuzzy score: ${"%.2f".format(bestCandidate.confidence)}")

        if (finalConfidence < 0.75f) failures.add(FailureType.LOW_CONFIDENCE_CORRECTION_FAILURE)

        // Stage 9: Canonicalization
        val category = IngredientOntology.categoryOf(bestCandidate.candidate)
        if (category != null) debugSteps.add("category: $category")
        debugSteps.add("canonicalized: ${bestCandidate.candidate}")

        return CorrectionResult(
            canonical = bestCandidate.candidate,
            confidence = finalConfidence,
            failures = failures,
            debugSteps = debugSteps,
            ontologyCategory = category,
            groupPath = groupPath
        )
    }
}
