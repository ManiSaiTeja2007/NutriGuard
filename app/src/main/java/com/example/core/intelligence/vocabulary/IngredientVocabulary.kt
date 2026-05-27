package com.example.core.intelligence.vocabulary

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
        "yellow 6", "blue 1", "caramel color", "titanium dioxide", "sodium chloride",
        "vegetable oil", "acidity regulator", "turmeric", "potassium", "sodium carbonate",
        "sodium carbonates", "cumin", "ginger", "garlic", "saffron"
    )

    // Dynamic learned vocabulary
    private val learnedCache = ConcurrentHashMap.newKeySet<String>()

    // Multilingual translation hooks (Offline support)
    private val multilingualHooks = mapOf(
        "wasser" to "water",
        "eau" to "water",
        "sel" to "salt",
        "salz" to "salt",
        "sucre" to "sugar",
        "zucker" to "sugar",
        "farine de ble" to "wheat flour",
        "weizenmehl" to "wheat flour",
        "lecithine de soja" to "soy lecithin",
        "sojalecithin" to "soy lecithin"
    )

    // Standard OCR typo / abbreviation resolution shortcuts
    private val ocrCorruptionMap = mapOf(
        "slt" to "salt",
        "suagr" to "sugar",
        "citnc acid" to "citric acid",
        "sodlum chloride" to "sodium chloride",
        "soydum" to "sodium",
        "flourr" to "flour",
        "waterr" to "water",
        "corn syrap" to "corn syrup",
        "ascarbic" to "ascorbic",
        "monosodum" to "monosodium",
        "glutamatee" to "glutamate",
        "veg oi1" to "vegetable oil",
        "veg oil" to "vegetable oil",
        "mono sodium glutamat" to "monosodium glutamate",
        "acidity reg" to "acidity regulator",
        "s0dium carb" to "sodium carbonate",
        "potass1um" to "potassium",
        "e62lll" to "e621",
        "e62i" to "e621",
        "ins500(ii)" to "e500",
        "ins50o(ii)" to "e500"
    )

    /**
     * Checks if the vocabulary contains the given ingredient (exact, case-insensitive match).
     */
    fun contains(ingredient: String): Boolean {
        val normalized = ingredient.lowercase(Locale.ROOT).trim()
        return staticVocabulary.contains(normalized) || 
               learnedCache.contains(normalized) || 
               multilingualHooks.containsKey(normalized) || 
               ocrCorruptionMap.containsKey(normalized)
    }

    /**
     * Resolves abbreviation, multilingual translation, or OCR corruption to its clean base vocabulary string.
     * If not found, returns the clean lowercase trimmed input.
     */
    fun resolveBaseForm(ingredient: String): String {
        val clean = ingredient.lowercase(Locale.ROOT).trim()
        
        // Check corruption map first
        val fromCorruption = ocrCorruptionMap[clean]
        if (fromCorruption != null) return fromCorruption

        // Check translation hooks
        val fromMultilingual = multilingualHooks[clean]
        if (fromMultilingual != null) return fromMultilingual

        return clean
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
