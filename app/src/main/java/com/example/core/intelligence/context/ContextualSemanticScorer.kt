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
        val reason: String?,
        val influencingTokens: List<String> = emptyList()
    )

    /**
     * Scores a candidate based on NeighborContext with distance decay weighting.
     */
    fun scoreCandidate(
        candidate: String,
        baseConfidence: Float,
        neighbors: List<NeighborContext>
    ): ScorerResult {
        val cleanCandidate = candidate.lowercase(Locale.ROOT).trim()
        val category = IngredientOntology.categoryOf(cleanCandidate)

        var bonus = 0.0f
        val reasons = mutableListOf<String>()
        val influencing = mutableListOf<String>()

        if (category != null) {
            for (neighbor in neighbors) {
                // Decay context bonus as neighbor distance increases
                val weight = 1.0f / neighbor.distance.coerceAtLeast(1)
                
                // 1. Same ontology category bonus
                if (neighbor.category == category) {
                    val partBonus = sameCategoryBonus * weight
                    bonus += partBonus
                    reasons.add("neighbor category: $category")
                    influencing.add(neighbor.token)
                } 
                // 2. Keyword context bonus
                else {
                    val matchingKeywords = listOf("acidity_regulator", "preservative", "color", "colour", "sweetener")
                    val matchingKeyword = matchingKeywords.firstOrNull { kw ->
                        val cleanCat = neighbor.category ?: ""
                        cleanCat.startsWith(kw) || kw.startsWith(cleanCat) ||
                        cleanCat.replace("_", " ").contains(kw) || kw.contains(cleanCat.replace("_", " ")) ||
                        neighbor.token.lowercase(Locale.ROOT).contains(kw.replace("_", " "))
                    }
                    if (matchingKeyword != null) {
                        val partBonus = sameCategoryBonus * weight
                        bonus += partBonus
                        reasons.add("neighbor keyword match: $matchingKeyword")
                        influencing.add(neighbor.token)
                    }
                }
            }
        }

        // 3. Additive proximity bonus (if the candidate is an E-number or resolved additive, and is near other additives)
        val isAdditive = cleanCandidate.startsWith("e") && cleanCandidate.substring(1).all { it.isDigit() || it == '(' || it == ')' || it == 'i' || it == 'v' }
        if (isAdditive) {
            val neighborAdditives = neighbors.filter { n ->
                val cleanN = n.token.lowercase(Locale.ROOT).trim()
                cleanN.startsWith("e") && cleanN.substring(1).all { it.isDigit() || it == '(' || it == ')' || it == 'i' || it == 'v' }
            }
            for (neighbor in neighborAdditives) {
                val weight = 1.0f / neighbor.distance.coerceAtLeast(1)
                val partBonus = additiveNeighborBonus * weight
                bonus += partBonus
                reasons.add("additive proximity bonus")
                influencing.add(neighbor.token)
            }
        }

        val finalConf = (baseConfidence + bonus).coerceIn(0.0f, 1.0f)
        return ScorerResult(
            finalConfidence = finalConf,
            bonusApplied = bonus,
            reason = if (reasons.isEmpty()) null else reasons.joinToString(" + "),
            influencingTokens = influencing
        )
    }

    /**
     * Backward-compatible helper that maps sets to flat lists of NeighborContexts with distance = 1.
     */
    fun scoreCandidate(
        candidate: String,
        baseConfidence: Float,
        contextCategories: Set<String>,
        contextKeywords: Set<String>
    ): ScorerResult {
        val neighbors = mutableListOf<NeighborContext>()
        contextCategories.forEach { cat ->
            neighbors.add(NeighborContext(token = cat, category = cat, distance = 1))
        }
        contextKeywords.forEach { kw ->
            neighbors.add(NeighborContext(token = kw, category = kw, distance = 1))
        }
        return scoreCandidate(candidate, baseConfidence, neighbors)
    }
}
