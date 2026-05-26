package com.example.core.intelligence.correction

import com.example.core.intelligence.ambiguity.OCRConfusionResolver
import com.example.core.intelligence.calibration.ConfidenceCalibrationEngine
import com.example.core.intelligence.contextual.ContextualDisambiguator
import com.example.core.intelligence.contextual.DisambiguationContext
import com.example.core.additives.ENumberEntry
import com.example.core.intelligence.enumbers.ENumberRepairEngine
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
 */
data class CorrectionResult(
    val canonical: String,
    val confidence: Float,
    val failures: List<FailureType>,
    val debugSteps: List<String>,
    val phraseWindow: List<String> = emptyList(),
    val ontologyCategory: String? = null,
    val disambiguationRule: String? = null,
    val groupPath: String = "root",
    val interpretedCategory: String? = null,
    val additiveCode: String? = null,
    val explanation: String? = null,
    val warnings: List<String> = emptyList()
) {
    /** The original raw OCR token, extracted from the first debug step. */
    val originalToken: String
        get() = debugSteps.firstOrNull { it.startsWith("OCR: ") }?.substringAfter("OCR: ") ?: ""
}

/**
 * 9-stage deterministic ingredient correction engine with adaptive calibration.
 */
class OcrCorrectionEngine(
    private val vocabulary: IngredientVocabulary
) {

    // Overloaded backward compatible correct method
    fun correct(
        tokens: List<String>,
        ocrConfidence: Float,
        groupPath: String = "root"
    ): List<CorrectionResult> {
        return correct(tokens, OcrMetadata(ocrConfidence = ocrConfidence), groupPath)
    }

    /**
     * Corrects a flat list of tokens with calibration profiles and staged candidate generation.
     */
    fun correct(
        tokens: List<String>,
        metadata: OcrMetadata = OcrMetadata(),
        groupPath: String = "root"
    ): List<CorrectionResult> {
        // Calculate E-number ratio to see if this is an additive-heavy label
        val totalTokens = tokens.size.coerceAtLeast(1)
        val additiveCount = tokens.count { 
            it.startsWith("e", ignoreCase = true) || ENumberRepairEngine.repair(it) != null 
        }
        val additiveRatio = additiveCount.toFloat() / totalTokens

        val profile = ConfidenceCalibrationEngine.calibrate(
            ocrConfidence = metadata.ocrConfidence,
            blurScore = metadata.blurScore,
            contrastScore = metadata.contrastScore,
            brightnessScore = metadata.brightnessScore,
            additiveRatio = additiveRatio
        )

        // First pass: correct each token independently with the visual profile
        val firstPass = tokens.map { token ->
            correctSingle(token, metadata, groupPath, profile)
        }

        // Second pass: contextual disambiguation
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

    private fun correctSingle(
        token: String,
        metadata: OcrMetadata,
        groupPath: String,
        profile: ConfidenceCalibrationEngine.CalibrationProfile
    ): CorrectionResult {
        val debugSteps = mutableListOf<String>()
        val failures = mutableListOf<FailureType>()

        // 1. Normalize
        debugSteps.add("OCR: $token")
        debugSteps.add("calibration profile: ${profile.name}")
        val cleanToken = token.lowercase(Locale.ROOT).trim()
        debugSteps.add("normalized: $cleanToken")

        // 2. Phrase Normalize
        val (phraseNorm, phraseTrace) = PhraseNormalizer.normalize(cleanToken)
        debugSteps.addAll(phraseTrace)

        // --- STAGED CANDIDATE GENERATION PIPELINE ---

        // Stage 1: Ontology Exact Match (E-numbers / Abbreviations)
        val ontologyTarget = IngredientOntology.resolve(phraseNorm)
        if (ontologyTarget != null) {
            val category = IngredientOntology.categoryOf(ontologyTarget)
            debugSteps.add("ontology hit: \"$ontologyTarget\"${if (category != null) " [category: $category]" else ""}")
            debugSteps.add("canonicalized: $ontologyTarget")
            return CorrectionResult(
                canonical = ontologyTarget,
                confidence = 1.0f,
                failures = emptyList(),
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath
            )
        }

        // Stage 2: Specialized E-Number Repair
        val eNumberRepair = ENumberRepairEngine.repair(phraseNorm)
        if (eNumberRepair != null) {
            val category = eNumberRepair.category
            debugSteps.add("additive repair: \"${eNumberRepair.repairedCode}\" -> \"${eNumberRepair.canonicalName}\"")
            if (eNumberRepair.isRepaired) {
                failures.add(FailureType.ADDITIVE_NOTATION_FAILURE)
            }
            debugSteps.add("canonicalized: ${eNumberRepair.canonicalName}")
            return CorrectionResult(
                canonical = eNumberRepair.canonicalName,
                confidence = if (eNumberRepair.isRepaired) 0.90f else 1.0f,
                failures = failures,
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath
            )
        }

        // Stage 3: OCR Confusion Repair (Position-Aware)
        val siteCount = phraseNorm.count { it in "c0oO1lis5()" }
        if (siteCount > 8) {
            if (!failures.contains(FailureType.CANDIDATE_EXPLOSION_FAILURE)) {
                failures.add(FailureType.CANDIDATE_EXPLOSION_FAILURE)
            }
            debugSteps.add("candidate explosion warning: token contains $siteCount ambiguity sites")
        }
        val confusionCandidates = OCRConfusionResolver.resolveAmbiguity(phraseNorm)
        val validConfusionHits = mutableListOf<MatchCandidate>()

        for (cand in confusionCandidates) {
            if (vocabulary.contains(cand.text)) {
                validConfusionHits.add(
                    MatchCandidate(vocabulary.resolveBaseForm(cand.text), 1.0f - cand.penalty, 0)
                )
            }
            val ontResolve = IngredientOntology.resolve(cand.text)
            if (ontResolve != null) {
                validConfusionHits.add(
                    MatchCandidate(ontResolve, 1.0f - cand.penalty, 0)
                )
            }
        }

        if (validConfusionHits.isNotEmpty()) {
            validConfusionHits.sortByDescending { it.confidence }
            val topHit = validConfusionHits[0]
            failures.add(FailureType.OCR_AMBIGUITY_FAILURE)
            debugSteps.add("ocr ambiguity resolved: \"${topHit.candidate}\" (confidence: ${"%.2f".format(topHit.confidence)})")
            debugSteps.add("canonicalized: ${topHit.candidate}")
            return CorrectionResult(
                canonical = topHit.candidate,
                confidence = topHit.confidence,
                failures = failures,
                debugSteps = debugSteps,
                ontologyCategory = IngredientOntology.categoryOf(topHit.candidate),
                groupPath = groupPath
            )
        }

        // Stage 4: Fuzzy Expansion (Bounded Levenshtein)
        val fuzzyCandidates = mutableListOf<MatchCandidate>()
        val vocab = vocabulary.getVocabulary()
        
        // Dynamic max edit distance bounds by token length and profile
        val lenToken = phraseNorm.length
        val maxAllowedDistance = minOf(profile.maxEditDistance, lenToken / 2).coerceAtLeast(1)

        for (candidate in vocab) {
            val lenCandidate = candidate.length
            if (abs(lenToken - lenCandidate) > maxAllowedDistance) continue

            val distance = Levenshtein.distance(phraseNorm, candidate)
            if (distance <= maxAllowedDistance) {
                val ratio = distance.toFloat() / max(lenToken, lenCandidate)
                val fuzzyScore = 1.0f - ratio
                
                val context = CorrectionContext(
                    ocrConfidence = metadata.ocrConfidence,
                    tokenLength = lenToken,
                    ambiguityCount = 0,
                    isKnownAbbreviation = false,
                    isOntologyMapping = false
                )
                val finalConf = MatchConfidence.calculateFuzzyConfidence(phraseNorm, candidate, distance, context)
                fuzzyCandidates.add(MatchCandidate(candidate, finalConf, distance))
            }
        }
        fuzzyCandidates.sortByDescending { it.confidence }

        if (fuzzyCandidates.isEmpty()) {
            debugSteps.add("candidate: none")
            debugSteps.add("rejection reason: no fuzzy candidates within max edit distance $maxAllowedDistance")
            failures.add(FailureType.UNKNOWN_INGREDIENT_FAILURE)
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = 0.5f,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath
            )
        }

        // Stage 5: False-Correction Safeguards
        val bestCandidate = fuzzyCandidates[0]
        val ambiguityCount = fuzzyCandidates.count {
            it != bestCandidate && abs(it.distance - bestCandidate.distance) <= 1
        }

        // Handle ambiguity warnings
        if (ambiguityCount > 0) {
            failures.add(FailureType.OCR_AMBIGUITY_FAILURE)
            debugSteps.add("ambiguity warning: $ambiguityCount other close matches. Top: ${fuzzyCandidates.take(3).map { it.candidate }}")
        }

        // 1. Safeguard: High ambiguity and allowAmbiguousCorrection is disabled
        if (ambiguityCount > 0 && !profile.allowAmbiguousCorrection) {
            failures.add(FailureType.FALSE_CORRECTION_RISK_FAILURE)
            debugSteps.add("safeguard triggered: preserved raw token \"$phraseNorm\" due to high ambiguity (allowAmbiguousCorrection is false)")
            debugSteps.add("rejected candidates: ${fuzzyCandidates.take(3).map { it.candidate }}")
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = 0.6f,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath
            )
        }

        // 2. Safeguard: Low confidence threshold check
        val finalConfidence = bestCandidate.confidence
        if (finalConfidence < profile.minimumConfidenceThreshold) {
            failures.add(FailureType.FALSE_CORRECTION_RISK_FAILURE)
            debugSteps.add("safeguard triggered: preserved raw token \"$phraseNorm\" due to low match confidence (${"%.2f".format(finalConfidence)} < ${profile.minimumConfidenceThreshold})")
            debugSteps.add("rejected candidate: \"${bestCandidate.candidate}\"")
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = finalConfidence,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath
            )
        }

        debugSteps.add("accepted candidate: \"${bestCandidate.candidate}\"")
        debugSteps.add("distance: ${bestCandidate.distance}")
        debugSteps.add("final confidence: ${"%.2f".format(finalConfidence)}")

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
