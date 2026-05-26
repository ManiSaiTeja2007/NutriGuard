package com.example.core.explanation

import com.example.core.ontology.IngredientCategory
import com.example.core.utils.AssetLoader
import org.json.JSONObject
import java.util.Locale

object ExplanationTemplates {
    private val ingredientExplanations = mutableMapOf<String, String>()
    private val categoryExplanations = mutableMapOf<IngredientCategory, String>()

    init {
        try {
            val jsonStr = AssetLoader.loadAsset("explanations/explanations.json")
            val json = JSONObject(jsonStr)
            
            val ingsJson = json.getJSONObject("ingredients")
            ingsJson.keys().forEach { key ->
                ingredientExplanations[key.lowercase(Locale.ROOT).trim()] = ingsJson.getString(key)
            }

            val catsJson = json.getJSONObject("categories")
            catsJson.keys().forEach { key ->
                try {
                    val category = IngredientCategory.valueOf(key)
                    categoryExplanations[category] = catsJson.getString(key)
                } catch (e: Exception) {
                    // Ignore malformed categories
                }
            }
        } catch (e: Exception) {
            // Static fallbacks
            categoryExplanations[IngredientCategory.UNKNOWN] = "ingredient with an unspecified category or primary function"
            ingredientExplanations["monosodium glutamate"] = "flavour enhancer commonly used in processed foods"
            ingredientExplanations["citric acid"] = "acidity regulator and preservative commonly found in fruits"
        }
    }

    /**
     * Returns the static, factual explanation template for the given ingredient name or fallback category.
     */
    fun getExplanation(name: String, category: IngredientCategory): String {
        val clean = name.lowercase(Locale.ROOT).trim()
        val specific = ingredientExplanations[clean]
        if (specific != null) {
            return specific
        }
        return categoryExplanations[category] ?: categoryExplanations[IngredientCategory.UNKNOWN] ?: "unspecified food ingredient"
    }
}
