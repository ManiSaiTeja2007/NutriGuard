package com.example.core.intelligence

import com.example.core.ontology.OntologyRepository
import com.example.core.additives.ENumberRepository
import com.example.core.explanation.IngredientExplanationEngine
import com.example.core.confidence.ConfidenceEvaluator
import com.example.core.confidence.ConfidenceBand
import com.example.core.risk.RiskInterpreter
import com.example.core.ontology.IngredientCategory
import com.example.core.normalization.IngredientNormalizer
import java.util.Locale

object IngredientInterpreter {
    
    val metadata = KnowledgeMetadata(
        ontologyVersion = "1.0.0",
        additiveVersion = "1.0.0",
        explanationVersion = "1.0.0",
        calibrationVersion = "1.0.0"
    )

    /**
     * Pure ingredient interpreter. Coordinates ontology/additives lookup, normalizes inputs,
     * assigns discrete confidence bands, pulls explanations, and assigns conservative risk warnings.
     * Contains NO side effects (no file writing, no UI mutations, no navigation).
     */
    fun interpret(
        canonicalName: String,
        confidence: Float,
        originalToken: String = ""
    ): InterpretedIngredient {
        val normalizedQuery = IngredientNormalizer.normalize(canonicalName)

        val ontologyEntry = OntologyRepository.find(normalizedQuery)
        val additiveEntry = ENumberRepository.find(normalizedQuery)

        val failuresList = mutableListOf<InterpretationFailure>()

        // 1. Fallback State if ontology match fails (safeguard against aggressive guessing)
        if (ontologyEntry == null && additiveEntry == null) {
            failuresList.add(InterpretationFailure.ONTOLOGY_MISS)
            
            val assessment = ConfidenceEvaluator.assess(confidence, canonicalName)
            val fallbackExplanation = IngredientExplanationEngine.explain(
                canonicalName = assessment.displayMessage,
                category = IngredientCategory.UNKNOWN,
                additiveCode = null
            )

            val warnings = mutableListOf<String>()
            if (assessment.isAmbiguous) {
                warnings.add("Possible match with moderate or low confidence. Original scan was: \"$originalToken\"")
            }

            return InterpretedIngredient(
                canonicalName = canonicalName,
                category = IngredientCategory.UNKNOWN,
                confidence = confidence,
                confidenceBand = assessment.band,
                additiveCode = null,
                explanation = fallbackExplanation,
                warnings = warnings,
                failures = failuresList,
                trace = InterpretationTrace(
                    matchedAlias = null,
                    confidence = confidence,
                    ontologySource = null,
                    resolutionPath = "fallback_unknown"
                )
            )
        }

        // 2. Resolve entries
        val resolvedCategory = ontologyEntry?.category ?: additiveEntry!!.category
        val resolvedAdditiveCode = ontologyEntry?.additiveCode ?: additiveEntry!!.code
        val resolvedCanonical = ontologyEntry?.canonicalName ?: additiveEntry!!.canonicalName
        val tags = ontologyEntry?.tags ?: emptyList()

        // 3. Assess confidence bands
        val assessment = ConfidenceEvaluator.assess(confidence, resolvedCanonical)
        
        // 4. Retrieve explanation
        val explanation = IngredientExplanationEngine.explain(
            canonicalName = assessment.displayMessage,
            category = resolvedCategory,
            additiveCode = resolvedAdditiveCode
        )

        // 5. Evaluate warnings
        val warnings = mutableListOf<String>()
        if (assessment.isAmbiguous) {
            warnings.add("Possible match with moderate or low confidence. Original scan was: \"$originalToken\"")
            failuresList.add(InterpretationFailure.LOW_CONFIDENCE_MATCH)
        }

        val riskWarnings = RiskInterpreter.evaluate(resolvedCanonical, resolvedCategory, tags)
        warnings.addAll(riskWarnings)

        if (additiveEntry != null && resolvedCanonical != normalizedQuery) {
            failuresList.add(InterpretationFailure.AMBIGUOUS_E_NUMBER)
        }

        return InterpretedIngredient(
            canonicalName = resolvedCanonical,
            category = resolvedCategory,
            confidence = confidence,
            confidenceBand = assessment.band,
            additiveCode = resolvedAdditiveCode,
            explanation = explanation,
            warnings = warnings,
            failures = failuresList,
            trace = InterpretationTrace(
                matchedAlias = if (normalizedQuery != resolvedCanonical) normalizedQuery else null,
                confidence = confidence,
                ontologySource = resolvedAdditiveCode ?: "ontology",
                resolutionPath = if (ontologyEntry != null) "ontology_lookup" else "additives_lookup"
            )
        )
    }
}
