package com.example.core.ingredient

import java.util.Locale

object IngredientCanonicalizer {

    private val canonicalMap = mapOf(
        "sodium chloride" to "salt",
        "msg" to "monosodium glutamate",
        "vitamin c" to "ascorbic acid",
        "l-ascorbic acid" to "ascorbic acid",
        "sodium hydrogen carbonate" to "sodium bicarbonate",
        "baking soda" to "sodium bicarbonate",
        "high-fructose corn syrup" to "high fructose corn syrup",
        "hfcs" to "high fructose corn syrup",
        "lecithin" to "soy lecithin",
        "lecithine" to "soy lecithin",
        "sucrose" to "sugar",
        "cane sugar" to "sugar",
        "beet sugar" to "sugar",
        "sodium mono-glutamate" to "monosodium glutamate",
        "e621" to "monosodium glutamate",
        "e300" to "ascorbic acid"
    )

    /**
     * Maps a corrected ingredient name to its canonical version.
     * If no alias mapping exists, returns the original string.
     */
    fun canonicalize(ingredient: String): String {
        val normalized = ingredient.lowercase(Locale.ROOT).trim()
        return canonicalMap[normalized] ?: normalized
    }

    /**
     * Returns true if the corrected name is a known alias in the canonical map.
     */
    fun isAlias(ingredient: String): Boolean {
        val normalized = ingredient.lowercase(Locale.ROOT).trim()
        return canonicalMap.containsKey(normalized)
    }
}
