package com.example.core.ontology

import java.util.Locale

object CorpusClassifier {

    enum class Section(val displayName: String) {
        CHEMICAL_ADDITIVES("Chemical Substances & Additives"),
        SEASONINGS_SPICES("Seasonings & Spices"),
        OILS_FATS("Oils & Fats"),
        VEGGIES_GRAINS("Veggies, Fruits & Grains"),
        BASE_INGREDIENTS("Base Ingredients")
    }

    /**
     * Classifies an ingredient (by canonical name) into one of the 5 semantic sections.
     */
    fun classify(canonicalName: String): Section {
        val entry = OntologyRepository.find(canonicalName)
        if (entry != null) {
            // Check by category first
            when (entry.category) {
                IngredientCategory.SWEETENER -> {
                    if (entry.canonicalName.lowercase(Locale.ROOT) == "sucrose" || 
                        entry.canonicalName.lowercase(Locale.ROOT) == "sugar") {
                        return Section.BASE_INGREDIENTS
                    }
                    return Section.CHEMICAL_ADDITIVES
                }
                IngredientCategory.PRESERVATIVE,
                IngredientCategory.EMULSIFIER,
                IngredientCategory.STABILIZER,
                IngredientCategory.ACIDITY_REGULATOR,
                IngredientCategory.FLAVOUR_ENHANCER,
                IngredientCategory.COLORING,
                IngredientCategory.ADDITIVE -> {
                    return Section.CHEMICAL_ADDITIVES
                }
                IngredientCategory.SEASONING -> {
                    return Section.SEASONINGS_SPICES
                }
                IngredientCategory.OIL -> {
                    return Section.OILS_FATS
                }
                IngredientCategory.VEGGIE_GRAIN -> {
                    return Section.VEGGIES_GRAINS
                }
                IngredientCategory.BASE_INGREDIENT -> {
                    return Section.BASE_INGREDIENTS
                }
                IngredientCategory.UNKNOWN -> {} // Handled below
            }

            // Fallback check on tags or additiveCode
            if (entry.additiveCode != null) {
                return Section.CHEMICAL_ADDITIVES
            }
            if (entry.tags.contains("additive") || entry.tags.contains("chemical") || entry.tags.contains("artificial_sweetener")) {
                return Section.CHEMICAL_ADDITIVES
            }
            if (entry.tags.contains("seasoning") || entry.tags.contains("spice")) {
                return Section.SEASONINGS_SPICES
            }
            if (entry.tags.contains("oil") || entry.tags.contains("fat")) {
                return Section.OILS_FATS
            }
            if (entry.tags.contains("vegetable") || entry.tags.contains("fruit") || entry.tags.contains("grain") || entry.tags.contains("seed")) {
                return Section.VEGGIES_GRAINS
            }
            if (entry.tags.contains("base_ingredient")) {
                return Section.BASE_INGREDIENTS
            }
        }

        // Catch-all fallbacks based on string pattern matching for unknown or weakly-mapped ingredients
        val lower = canonicalName.lowercase(Locale.ROOT).trim()
        if (lower.contains("oil") || lower.contains("fat") || lower.contains("butter") || lower.contains("tallow") || lower.contains("lard")) {
            return Section.OILS_FATS
        }
        if (lower.contains("acid") || lower.contains("gum") || lower.contains("benzoate") || lower.contains("sorbate") || 
            lower.contains("phosphate") || lower.contains("carbonate") || lower.contains("lecithin") || 
            lower.matches(Regex("e\\d{3,4}.*")) || lower.matches(Regex("ins\\s?\\d{3,4}.*"))) {
            return Section.CHEMICAL_ADDITIVES
        }
        if (lower.contains("salt") || lower.contains("pepper") || lower.contains("chili") || lower.contains("spices") || 
            lower.contains("cumin") || lower.contains("garlic") || lower.contains("onion") || lower.contains("ginger") || 
            lower.contains("turmeric") || lower.contains("cinnamon") || lower.contains("vanilla") || lower.contains("extract")) {
            return Section.SEASONINGS_SPICES
        }
        if (lower.contains("flour") || lower.contains("wheat") || lower.contains("rice") || lower.contains("corn") || 
            lower.contains("soy") || lower.contains("bean") || lower.contains("pea") || lower.contains("starch") ||
            lower.contains("water") || lower.contains("milk") || lower.contains("whey") || lower.contains("egg") || 
            lower.contains("cocoa") || lower.contains("chocolate") || lower.contains("yeast")) {
            return Section.BASE_INGREDIENTS
        }

        return Section.BASE_INGREDIENTS
    }
}
