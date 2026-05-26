package com.example.core.explanation

import com.example.core.ontology.IngredientCategory
import java.util.Locale

object IngredientExplanationEngine {
    /**
     * Constructs a deterministic, factual explanation for a given ingredient name.
     * Uses static templates from either ENumber entries, ontology definitions, or category fallback.
     */
    fun explain(canonicalName: String, category: IngredientCategory): String {
        val cleanName = canonicalName.lowercase(Locale.ROOT).trim()
        val desc = ExplanationTemplates.getExplanation(cleanName, category)
        val displayName = canonicalName.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        return "$displayName\n→ $desc"
    }
}
