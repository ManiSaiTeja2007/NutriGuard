package com.example.core.intelligence

import com.example.core.normalization.DefaultIngredientNormalizer
import com.example.core.aliases.AliasRepairEngine
import com.example.core.ambiguity.AmbiguityResolver
import com.example.core.additives.AdditiveResolver
import com.example.core.ontology.OntologyRepository
import com.example.core.ontology.IngredientCategory
import com.example.core.confidence.ConfidenceEvaluator
import com.example.core.confidence.ConfidenceBand
import com.example.core.explanation.IngredientExplanationEngine
import com.example.core.risk.RiskInterpreter
import java.util.Locale

object IngredientInterpreter {
    
    val metadata = KnowledgeMetadata(
        ontologyVersion = "1.0.0",
        additiveVersion = "1.0.0",
        normalizationVersion = "1.0.0",
        aliasVersion = "1.0.0"
    )

    /**
     * Coordinated semantic intelligence interpreter.
     * Integrates normalization, alias repair, additive resolution, confidence calibration,
     * static explanations, and conservative risk evaluations.
     */
    fun interpret(
        canonicalName: String,
        confidence: Float,
        originalToken: String = ""
    ): InterpretedIngredient {
        val rawText = originalToken.ifEmpty { canonicalName }
        val failuresList = mutableListOf<InterpretationFailure>()

        // 1. Normalization Stage
        val normResult = DefaultIngredientNormalizer.normalize(rawText)
        val normalizedText = normResult.normalizedText

        // 2. Alias Repair Stage
        val aliasResult = AliasRepairEngine.repair(normalizedText)
        val aliasRepairedText = aliasResult.repairedText
        if (aliasResult.isRepaired) {
            failuresList.add(InterpretationFailure.AMBIGUOUS_ALIAS)
        }

        // 3. Ambiguity Check Safeguard
        if (AmbiguityResolver.isAmbiguous(aliasRepairedText)) {
            failuresList.add(InterpretationFailure.FALSE_INTERPRETATION_RISK)
            val finalExplanation = IngredientExplanationEngine.explain(rawText, IngredientCategory.UNKNOWN)
            val trace = InterpretationTrace(
                ocrText = rawText,
                normalizedText = normalizedText,
                aliasRepairedText = aliasRepairedText,
                ontologyMatchedName = null,
                confidenceBand = ConfidenceBand.UNCERTAIN.name,
                finalInterpretation = finalExplanation
            )
            return InterpretedIngredient(
                originalText = rawText,
                normalizedText = normalizedText,
                canonicalName = null,
                category = IngredientCategory.UNKNOWN,
                confidence = ConfidenceBand.UNCERTAIN,
                additiveCode = null,
                explanation = finalExplanation,
                warnings = emptyList(),
                failures = failuresList,
                trace = trace,
                resolutionSource = ResolutionSource.UNKNOWN
            )
        }

        // 4. Additive / E-number check
        val additiveEntry = AdditiveResolver.resolve(aliasRepairedText)
        
        // 5. Ontology lookup
        val ontologyEntry = OntologyRepository.find(aliasRepairedText)

        // 6. Safeguard: Ontology/Additive miss or weak confidence
        if (ontologyEntry == null && additiveEntry == null) {
            failuresList.add(InterpretationFailure.ONTOLOGY_MISS)
            failuresList.add(InterpretationFailure.UNKNOWN_INGREDIENT)
            
            val finalExplanation = IngredientExplanationEngine.explain(rawText, IngredientCategory.UNKNOWN)
            val trace = InterpretationTrace(
                ocrText = rawText,
                normalizedText = normalizedText,
                aliasRepairedText = aliasRepairedText,
                ontologyMatchedName = null,
                confidenceBand = ConfidenceBand.UNCERTAIN.name,
                finalInterpretation = finalExplanation
            )
            return InterpretedIngredient(
                originalText = rawText,
                normalizedText = normalizedText,
                canonicalName = null,
                category = IngredientCategory.UNKNOWN,
                confidence = ConfidenceBand.UNCERTAIN,
                additiveCode = null,
                explanation = finalExplanation,
                warnings = emptyList(),
                failures = failuresList,
                trace = trace,
                resolutionSource = ResolutionSource.UNKNOWN
            )
        }

        // 7. Resolve canonical values and category
        val resolvedCategory = ontologyEntry?.category ?: additiveEntry?.category ?: IngredientCategory.UNKNOWN
        val resolvedAdditiveCode = ontologyEntry?.additiveCode ?: additiveEntry?.code
        val resolvedCanonical = ontologyEntry?.canonicalName ?: additiveEntry?.canonicalName ?: "unknown"
        val tags = ontologyEntry?.tags ?: emptyList()

        // 8. Confidence Band assessment
        val assessment = ConfidenceEvaluator.assess(confidence, resolvedCanonical)
        
        // Resolve the resolution source
        val rawSource = when {
            additiveEntry != null -> ResolutionSource.ADDITIVE_PARSE
            aliasResult.isTransliteration -> ResolutionSource.TRANSLITERATION
            aliasResult.isRepaired -> ResolutionSource.ALIAS_MATCH
            normalizedText == resolvedCanonical -> ResolutionSource.EXACT_MATCH
            else -> ResolutionSource.FUZZY_MATCH
        }

        // Apply Transliteration Confidence cap: remains MODERATE unless extra context is present
        val finalConfidenceBand = if (rawSource == ResolutionSource.TRANSLITERATION) {
            // Cap confidence at MODERATE
            if (assessment.band == ConfidenceBand.HIGH) ConfidenceBand.MODERATE else assessment.band
        } else {
            assessment.band
        }
        
        // Safeguard if final confidence is weak (LOW or UNCERTAIN) -> return UNKNOWN / UNCERTAIN
        if (finalConfidenceBand == ConfidenceBand.LOW || finalConfidenceBand == ConfidenceBand.UNCERTAIN) {
            failuresList.add(InterpretationFailure.LOW_CONFIDENCE_MATCH)
            val finalExplanation = IngredientExplanationEngine.explain(rawText, IngredientCategory.UNKNOWN)
            val trace = InterpretationTrace(
                ocrText = rawText,
                normalizedText = normalizedText,
                aliasRepairedText = aliasRepairedText,
                ontologyMatchedName = null,
                confidenceBand = ConfidenceBand.UNCERTAIN.name,
                finalInterpretation = finalExplanation
            )
            return InterpretedIngredient(
                originalText = rawText,
                normalizedText = normalizedText,
                canonicalName = null,
                category = IngredientCategory.UNKNOWN,
                confidence = ConfidenceBand.UNCERTAIN,
                additiveCode = null,
                explanation = finalExplanation,
                warnings = emptyList(),
                failures = failuresList,
                trace = trace,
                resolutionSource = ResolutionSource.UNKNOWN
            )
        }

        // 9. Static Explanation
        val explanation = IngredientExplanationEngine.explain(resolvedCanonical, resolvedCategory)

        // 10. Conservative Warning rules
        val warnings = RiskInterpreter.evaluate(resolvedCanonical, resolvedCategory, tags).toMutableList()
        if (additiveEntry != null && resolvedCanonical != aliasRepairedText) {
            failuresList.add(InterpretationFailure.AMBIGUOUS_E_NUMBER)
        }

        val trace = InterpretationTrace(
            ocrText = rawText,
            normalizedText = normalizedText,
            aliasRepairedText = aliasRepairedText,
            ontologyMatchedName = resolvedCanonical,
            confidenceBand = finalConfidenceBand.name,
            finalInterpretation = explanation
        )

        return InterpretedIngredient(
            originalText = rawText,
            normalizedText = normalizedText,
            canonicalName = resolvedCanonical,
            category = resolvedCategory,
            confidence = finalConfidenceBand,
            additiveCode = resolvedAdditiveCode,
            explanation = explanation,
            warnings = warnings,
            failures = failuresList,
            trace = trace,
            resolutionSource = rawSource
        )
    }
}
