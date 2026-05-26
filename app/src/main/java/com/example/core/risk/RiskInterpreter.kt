package com.example.core.risk

import com.example.core.ontology.IngredientCategory
import java.util.Locale

object RiskInterpreter {
    /**
     * Deterministic, conservative risk tagger. Exposes consumer warnings solely based on factual
     * properties (e.g. sugar content, emulsifiers/stabilizers).
     *
     * IMPORTANT: Contains NO references to words like "unsafe", "dangerous", "causes disease", or
     * "toxic chemical" to ensure safety and prevent legal/diagnostic liability.
     */
    fun evaluate(canonicalName: String, category: IngredientCategory, tags: List<String>): List<String> {
        val warnings = mutableListOf<String>()
        val cleanName = canonicalName.lowercase(Locale.ROOT).trim()

        // 1. Ultra-processed foods warning
        if (tags.contains("ultra_processed") || 
            category == IngredientCategory.EMULSIFIER || 
            category == IngredientCategory.STABILIZER ||
            cleanName.contains("cellulose") || 
            cleanName == "carrageenan" ||
            cleanName == "monosodium glutamate") {
            warnings.add("commonly found in ultra-processed foods")
        }

        // 2. High sugar content warning
        if (tags.contains("high_sugar") || 
            cleanName == "sugar" || 
            cleanName == "cane sugar" || 
            cleanName == "beet sugar" || 
            cleanName == "high fructose corn syrup" || 
            cleanName == "corn syrup") {
            warnings.add("high sugar content")
        }

        // 3. Artificial flavoring warning
        if (category == IngredientCategory.FLAVOUR_ENHANCER || 
            cleanName == "monosodium glutamate" || 
            tags.contains("artificial_sweetener")) {
            warnings.add("contains artificial flavoring")
        }

        // 4. High sodium warning
        if (cleanName == "salt" || 
            cleanName == "sodium chloride" || 
            cleanName == "monosodium glutamate") {
            warnings.add("high sodium content")
        }

        return warnings
    }
}
