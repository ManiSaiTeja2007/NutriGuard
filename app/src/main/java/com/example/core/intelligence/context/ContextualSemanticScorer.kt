package com.example.core.intelligence.context

import com.example.core.intelligence.ontology.IngredientOntology
import com.example.core.utils.AssetLoader
import org.json.JSONObject
import java.util.Locale

object ContextualSemanticScorer {

    private val sameCategoryBonus: Float by lazy {
        loadRule("same_category_bonus", 0.12f)
    }

    private val additiveNeighborBonus: Float by lazy {
        loadRule("additive_neighbor_bonus", 0.08f)
    }

    private val ingredientSequenceBonus: Float by lazy {
        loadRule("ingredient_sequence_bonus", 0.05f)
    }

    private fun loadRule(key: String, default: Float): Float {
        return try {
            val jsonStr = AssetLoader.loadAsset("context_scoring_rules.json")
            val json = JSONObject(jsonStr)
            json.optDouble(key, default.toDouble()).toFloat()
        } catch (e: Exception) {
            default
        }
    }

    data class ScorerResult(
        val finalConfidence: Float,
        val bonusApplied: Float,
        val reason: String?
    )

    /**
     * Scores a candidate based on surrounding category and keyword contexts.
     *
     * @param candidate      The canonical candidate term to evaluate.
     * @param baseConfidence The base fuzzy matching confidence score.
     * @param contextCategories Set of active categories resolved in the neighborhood window.
     * @param contextKeywords Set of category/additive naming keywords in the neighborhood.
     */
    fun scoreCandidate(
        candidate: String,
        baseConfidence: Float,
        contextCategories: Set<String>,
        contextKeywords: Set<String>
    ): ScorerResult {
        val cleanCandidate = candidate.lowercase(Locale.ROOT).trim()
        val category = IngredientOntology.categoryOf(cleanCandidate)

        var bonus = 0.0f
        var reason: String? = null

        if (category != null) {
            // 1. Same ontology category bonus (e.g. if candidate is citric acid, and neighbor category is acidity_regulator)
            if (contextCategories.contains(category)) {
                bonus += sameCategoryBonus
                reason = "neighbor category: $category"
            }
            // 2. Keyword context bonus (e.g. if neighbor token is "preservative", and candidate is a preservative)
            else {
                val matchingKeyword = contextKeywords.firstOrNull { kw ->
                    category.startsWith(kw) || kw.startsWith(category) ||
                    category.replace("_", " ").contains(kw) || kw.contains(category.replace("_", " "))
                }
                if (matchingKeyword != null) {
                    bonus += sameCategoryBonus
                    reason = "neighbor keyword match: $matchingKeyword"
                }
            }
        }

        // 3. Additive proximity bonus (if the candidate is an E-number or resolved additive, and is near other additives)
        val isAdditive = cleanCandidate.startsWith("e") && cleanCandidate.substring(1).all { it.isDigit() || it == '(' || it == ')' || it == 'i' || it == 'v' }
        if (isAdditive && contextCategories.contains("acidity_regulator")) {
            bonus += additiveNeighborBonus
            reason = (reason?.let { "$it + " } ?: "") + "additive proximity bonus"
        }

        // Contextual scoring must never override semantic safeguards by forcing HIGH confidence
        // Cap the final confidence appropriately or let it assist ranking/resolution safely.
        val finalConf = (baseConfidence + bonus).coerceIn(0.0f, 1.0f)
        return ScorerResult(
            finalConfidence = finalConf,
            bonusApplied = bonus,
            reason = reason
        )
    }
}
