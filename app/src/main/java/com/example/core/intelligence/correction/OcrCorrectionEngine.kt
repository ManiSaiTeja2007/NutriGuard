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
import com.example.core.intelligence.context.ContextualSemanticScorer
import com.example.core.intelligence.context.NeighborContext
import com.example.core.intelligence.confidence.ConfidenceStep
import com.example.core.intelligence.explanation.ExplanationHint
import com.example.core.intelligence.explanation.ExplanationType
import com.example.core.aliases.AliasRepairEngine
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
    val warnings: List<String> = emptyList(),
    val explanationHint: ExplanationHint? = null,
    val confidenceStep: ConfidenceStep? = null,
    val influencingTokens: List<String> = emptyList()
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

        // First pass: correct each token independently with visual profile and neighboring contexts
        val firstPass = tokens.mapIndexed { index, token ->
            // Construct NeighborContext list for neighbors within window of 3 (excluding current index)
            val neighbors = mutableListOf<NeighborContext>()
            val start = maxOf(0, index - 3)
            val end = minOf(tokens.size - 1, index + 3)
            for (i in start..end) {
                if (i == index) continue
                val neighborToken = tokens[i].lowercase(Locale.ROOT).trim()
                val distance = abs(i - index)
                
                // Try resolving category
                var neighborCat = IngredientOntology.categoryOf(neighborToken)
                if (neighborCat == null) {
                    val ontMatch = IngredientOntology.resolve(neighborToken)
                    if (ontMatch != null) {
                        neighborCat = IngredientOntology.categoryOf(ontMatch)
                    }
                }
                if (neighborCat == null) {
                    val eNumber = ENumberEntry.find(neighborToken)
                    if (eNumber != null) {
                        neighborCat = eNumber.category.name.lowercase(Locale.ROOT)
                    }
                }
                neighbors.add(NeighborContext(token = tokens[i], category = neighborCat, distance = distance))
            }
            correctSingle(token, metadata, groupPath, profile, neighbors)
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
                    val hint = ExplanationHint(
                        type = ExplanationType.CONTEXTUAL_RECONSTRUCTION,
                        originalText = result.originalToken,
                        reconstructedText = disambig.resolvedForm,
                        reason = "Resolved ambiguity using surrounding ingredients"
                    )
                    
                    val influencing = preceding + following
                    val disambigStep = ConfidenceStep(
                        baseConfidence = result.confidence,
                        contextBonus = 0.80f - result.confidence,
                        finalConfidence = 0.80f,
                        reason = "Contextual disambiguation: ${disambig.ruleId}",
                        influencingTokens = influencing
                    )

                    result.copy(
                        canonical = disambig.resolvedForm,
                        confidence = 0.80f,
                        failures = result.failures.filter { it != FailureType.AMBIGUOUS_MATCH_FAILURE },
                        debugSteps = newSteps,
                        ontologyCategory = category ?: result.ontologyCategory,
                        disambiguationRule = disambig.ruleId,
                        explanationHint = hint,
                        confidenceStep = disambigStep,
                        influencingTokens = influencing
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
        profile: ConfidenceCalibrationEngine.CalibrationProfile,
        neighbors: List<NeighborContext>
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
            val hint = if (phraseNorm != ontologyTarget) {
                val repairResult = AliasRepairEngine.repair(phraseNorm)
                val type = if (repairResult.isTransliteration) ExplanationType.TRANSLITERATION else ExplanationType.ALIAS_RESOLUTION
                ExplanationHint(
                    type = type,
                    originalText = token,
                    reconstructedText = ontologyTarget
                )
            } else {
                ExplanationHint(
                    type = ExplanationType.NO_CHANGES,
                    originalText = token,
                    reconstructedText = ontologyTarget
                )
            }
            val confidenceStep = ConfidenceStep(
                baseConfidence = 1.0f,
                contextBonus = 0.0f,
                finalConfidence = 1.0f,
                reason = null,
                influencingTokens = emptyList()
            )
            return CorrectionResult(
                canonical = ontologyTarget,
                confidence = 1.0f,
                failures = emptyList(),
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath,
                explanationHint = hint,
                confidenceStep = confidenceStep,
                influencingTokens = emptyList()
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
            val hint = ExplanationHint(
                type = ExplanationType.ADDITIVE_STANDARDIZATION,
                originalText = token,
                reconstructedText = eNumberRepair.canonicalName
            )
            val expectedConf = if (eNumberRepair.isRepaired) 0.90f else 1.0f
            val confidenceStep = ConfidenceStep(
                baseConfidence = expectedConf,
                contextBonus = 0.0f,
                finalConfidence = expectedConf,
                reason = null,
                influencingTokens = emptyList()
            )
            return CorrectionResult(
                canonical = eNumberRepair.canonicalName,
                confidence = expectedConf,
                failures = failures,
                debugSteps = debugSteps,
                ontologyCategory = category,
                groupPath = groupPath,
                explanationHint = hint,
                confidenceStep = confidenceStep,
                influencingTokens = emptyList()
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
            val hint = ExplanationHint(
                type = ExplanationType.SPELLING_CORRECTION,
                originalText = token,
                reconstructedText = topHit.candidate
            )
            val confidenceStep = ConfidenceStep(
                baseConfidence = topHit.confidence,
                contextBonus = 0.0f,
                finalConfidence = topHit.confidence,
                reason = null,
                influencingTokens = emptyList()
            )
            return CorrectionResult(
                canonical = topHit.candidate,
                confidence = topHit.confidence,
                failures = failures,
                debugSteps = debugSteps,
                ontologyCategory = IngredientOntology.categoryOf(topHit.candidate),
                groupPath = groupPath,
                explanationHint = hint,
                confidenceStep = confidenceStep,
                influencingTokens = emptyList()
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
                
                // --- CONTEXTUAL RECONSTRUCTION SCORING ---
                val scorerResult = ContextualSemanticScorer.scoreCandidate(
                    candidate = candidate,
                    baseConfidence = finalConf,
                    neighbors = neighbors
                )
                
                fuzzyCandidates.add(
                    MatchCandidate(
                        candidate = candidate,
                        confidence = scorerResult.finalConfidence,
                        distance = distance,
                        contextBonus = scorerResult.bonusApplied,
                        contextReason = scorerResult.reason,
                        baseConfidence = finalConf,
                        influencingTokens = scorerResult.influencingTokens
                    )
                )
            }
        }
        fuzzyCandidates.sortByDescending { it.confidence }

        if (fuzzyCandidates.isEmpty()) {
            debugSteps.add("candidate: none")
            debugSteps.add("rejection reason: no fuzzy candidates within max edit distance $maxAllowedDistance")
            failures.add(FailureType.UNKNOWN_INGREDIENT_FAILURE)
            val hint = ExplanationHint(
                type = ExplanationType.NO_CHANGES,
                originalText = token,
                reconstructedText = phraseNorm
            )
            val confidenceStep = ConfidenceStep(
                baseConfidence = 0.5f,
                contextBonus = 0.0f,
                finalConfidence = 0.5f,
                reason = null,
                influencingTokens = emptyList()
            )
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = 0.5f,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath,
                explanationHint = hint,
                confidenceStep = confidenceStep,
                influencingTokens = emptyList()
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
            val hint = ExplanationHint(
                type = ExplanationType.NO_CHANGES,
                originalText = token,
                reconstructedText = phraseNorm
            )
            val confidenceStep = ConfidenceStep(
                baseConfidence = 0.6f,
                contextBonus = 0.0f,
                finalConfidence = 0.6f,
                reason = null,
                influencingTokens = emptyList()
            )
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = 0.6f,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath,
                explanationHint = hint,
                confidenceStep = confidenceStep,
                influencingTokens = emptyList()
            )
        }

        // 2. Safeguard: Low confidence threshold check
        val finalConfidence = bestCandidate.confidence
        val baseConf = bestCandidate.baseConfidence

        // Strictly prevent contextual bonus from bypassing the LOW confidence safeguard limit (0.70f)
        if (finalConfidence < profile.minimumConfidenceThreshold || baseConf < 0.70f) {
            failures.add(FailureType.FALSE_CORRECTION_RISK_FAILURE)
            debugSteps.add("safeguard triggered: preserved raw token \"$phraseNorm\" due to low match confidence (${"%.2f".format(baseConf)} < threshold)")
            debugSteps.add("rejected candidate: \"${bestCandidate.candidate}\"")
            val hint = ExplanationHint(
                type = ExplanationType.NO_CHANGES,
                originalText = token,
                reconstructedText = phraseNorm
            )
            val confidenceStep = ConfidenceStep(
                baseConfidence = baseConf,
                contextBonus = bestCandidate.contextBonus,
                finalConfidence = finalConfidence,
                reason = "Safeguard triggered (base confidence too low)",
                influencingTokens = bestCandidate.influencingTokens
            )
            return CorrectionResult(
                canonical = phraseNorm,
                confidence = baseConf,
                failures = failures,
                debugSteps = debugSteps,
                groupPath = groupPath,
                explanationHint = hint,
                confidenceStep = confidenceStep,
                influencingTokens = bestCandidate.influencingTokens
            )
        }

        // Explainable Trace Formatting: candidate, baseConfidence, contextBonus, reason, finalConfidence
        debugSteps.add("base confidence: ${"%.2f".format(baseConf)}")
        if (bestCandidate.contextBonus > 0.0f) {
            debugSteps.add("contextual bonus: ${"%.2f".format(bestCandidate.contextBonus)}")
            debugSteps.add("contextual reason: ${bestCandidate.contextReason ?: "none"}")
        }
        debugSteps.add("accepted candidate: \"${bestCandidate.candidate}\"")
        debugSteps.add("distance: ${bestCandidate.distance}")
        debugSteps.add("final confidence: ${"%.2f".format(finalConfidence)}")

        val category = IngredientOntology.categoryOf(bestCandidate.candidate)
        if (category != null) debugSteps.add("category: $category")
        debugSteps.add("canonicalized: ${bestCandidate.candidate}")

        val hint = if (bestCandidate.contextBonus > 0.0f) {
            ExplanationHint(
                type = ExplanationType.CONTEXTUAL_RECONSTRUCTION,
                originalText = token,
                reconstructedText = bestCandidate.candidate
            )
        } else {
            ExplanationHint(
                type = ExplanationType.SPELLING_CORRECTION,
                originalText = token,
                reconstructedText = bestCandidate.candidate
            )
        }

        val confidenceStep = ConfidenceStep(
            baseConfidence = baseConf,
            contextBonus = bestCandidate.contextBonus,
            finalConfidence = finalConfidence,
            reason = bestCandidate.contextReason,
            influencingTokens = bestCandidate.influencingTokens
        )

        return CorrectionResult(
            canonical = bestCandidate.candidate,
            confidence = finalConfidence,
            failures = failures,
            debugSteps = debugSteps,
            ontologyCategory = category,
            groupPath = groupPath,
            explanationHint = hint,
            confidenceStep = confidenceStep,
            influencingTokens = bestCandidate.influencingTokens
        )
    }
}
