package com.example.core.explanation

import com.example.core.ontology.IngredientCategory
import com.example.core.additives.ENumberRepository
import com.example.core.ontology.OntologyRepository
import java.util.Locale

object IngredientExplanationEngine {
    /**
     * Constructs a deterministic, factual explanation for a given ingredient name.
     * Uses static templates from either ENumber entries, ontology definitions, or category fallback.
     */
    fun explain(canonicalName: String, category: IngredientCategory, additiveCode: String?): String {
        val cleanName = canonicalName.lowercase(Locale.ROOT).trim()

        // 1. Resolve E-number descriptions if applicable
        val eNumber = additiveCode?.let { ENumberRepository.find(it) } ?: ENumberRepository.find(cleanName)
        if (eNumber != null && eNumber.description.isNotEmpty()) {
            val formattedCode = eNumber.code.uppercase(Locale.ROOT)
            return "${eNumber.canonicalName} ($formattedCode) is ${eNumber.description}."
        }

        // 2. Resolve ontology entries if present
        val ontologyEntry = OntologyRepository.find(cleanName)
        if (ontologyEntry != null) {
            val formattedCategory = category.name.lowercase(Locale.ROOT).replace('_', ' ')
            return "${ontologyEntry.canonicalName} is a $formattedCategory commonly used in food products."
        }

        // 3. Fallback to standard category template
        val catDesc = ExplanationTemplates.getCategoryExplanation(category)
        return "$canonicalName: $catDesc"
    }
}
