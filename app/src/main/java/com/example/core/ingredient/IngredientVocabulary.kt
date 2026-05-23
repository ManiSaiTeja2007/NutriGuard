package com.example.core.ingredient

import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

class IngredientVocabulary {

    // Curated starter ingredient list
    private val staticVocabulary = setOf(
        "salt", "sugar", "citric acid", "water", "enriched flour", "wheat flour",
        "corn syrup", "sodium", "high fructose corn syrup", "monosodium glutamate",
        "ascorbic acid", "soy lecithin", "xanthan gum", "palm oil", "canola oil",
        "soybean oil", "natural flavor", "artificial flavor", "yeast", "calcium carbonate",
        "niacin", "reduced iron", "thiamine mononitrate", "riboflavin", "folic acid",
        "milk", "cheese", "butter", "eggs", "whey", "lactose", "dextrose",
        "modified corn starch", "gelatin", "pectin", "guar gum", "carrageenan",
        "sodium benzoate", "potassium sorbate", "calcium propionate", "baking soda",
        "sodium bicarbonate", "ammonium bicarbonate", "monocalcium phosphate",
        "disodium phosphate", "trisodium phosphate", "garlic", "onion", "spices",
        "cocoa", "chocolate", "vanilla", "malic acid", "lactic acid", "tartaric acid",
        "acetic acid", "carbonated water", "sucrose", "fructose", "glucose",
        "maltose", "stevia", "erythritol", "xylitol", "sorbitol", "mannitol",
        "aspartame", "sucralose", "acesulfame potassium", "red 40", "yellow 5",
        "yellow 6", "blue 1", "caramel color", "titanium dioxide", "sodium chloride"
    )

    // Thread-safe dynamic in-memory learned vocabulary cache
    private val learnedCache = ConcurrentHashMap.newKeySet<String>()

    /**
     * Checks if the vocabulary contains the given ingredient (exact, case-insensitive match).
     */
    fun contains(ingredient: String): Boolean {
        val normalized = ingredient.lowercase(Locale.ROOT).trim()
        return staticVocabulary.contains(normalized) || learnedCache.contains(normalized)
    }

    /**
     * Dynamically learns a new ingredient if it is valid (e.g. not blank).
     */
    fun learn(ingredient: String) {
        val normalized = ingredient.lowercase(Locale.ROOT).trim()
        if (normalized.isNotEmpty()) {
            learnedCache.add(normalized)
        }
    }

    /**
     * Returns the combined set of static and learned ingredients.
     */
    fun getVocabulary(): Set<String> {
        val result = HashSet<String>(staticVocabulary.size + learnedCache.size)
        result.addAll(staticVocabulary)
        result.addAll(learnedCache)
        return result
    }

    /**
     * Clears the learned vocabulary cache.
     */
    fun clearLearned() {
        learnedCache.clear()
    }
}
