package com.example.core.explanation

import com.example.core.ontology.IngredientCategory

object ExplanationTemplates {
    private val categoryExplanations = mapOf(
        IngredientCategory.SWEETENER to "A sweetening agent used to add flavor and sweetness to food products.",
        IngredientCategory.PRESERVATIVE to "A substance added to food products to prevent decay or spoilage and extend shelf life.",
        IngredientCategory.EMULSIFIER to "An additive that helps mix and stabilize ingredients that would otherwise separate, such as oil and water.",
        IngredientCategory.STABILIZER to "An additive that maintains the physical texture, structure, and consistency of food products.",
        IngredientCategory.ACIDITY_REGULATOR to "An ingredient used to control or alter the acidity or alkalinity of food products.",
        IngredientCategory.FLAVOUR_ENHANCER to "A compound added to foods to enhance their existing savory or sweet flavors.",
        IngredientCategory.OIL to "A fat source commonly used for texture, moisture, or cooking in processed foods.",
        IngredientCategory.COLORING to "A food additive used to impart or restore color to food products.",
        IngredientCategory.ADDITIVE to "A general food additive used for technological purposes during processing.",
        IngredientCategory.UNKNOWN to "An ingredient with an unspecified category or primary function."
    )

    /**
     * Returns the static, factual explanation template for the given ingredient category.
     */
    fun getCategoryExplanation(category: IngredientCategory): String {
        return categoryExplanations[category] ?: categoryExplanations[IngredientCategory.UNKNOWN]!!
    }
}
