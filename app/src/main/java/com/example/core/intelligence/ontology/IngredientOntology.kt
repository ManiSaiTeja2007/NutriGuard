package com.example.core.intelligence.ontology

import com.example.core.intelligence.enumbers.ENumberEntry
import java.util.Locale

/**
 * Named group of semantically related ingredients (additive category, food family, etc.).
 *
 * @param name           Category identifier, e.g. "sweeteners"
 * @param members        Set of canonical ingredient names belonging to this category
 * @param parentCategory Optional parent category for hierarchical grouping (e.g. "sugar_alcohols" → "sweeteners")
 */
data class OntologyCategory(
    val name: String,
    val members: Set<String>,
    val parentCategory: String? = null
)

object IngredientOntology {

    // ------- Abbreviation / Acronym resolutions -------
    private val abbreviations = mapOf(
        "msg" to "monosodium glutamate",
        "hfcs" to "high fructose corn syrup",
        "slt" to "salt",
        "tbhq" to "tertiary butylhydroquinone",
        "bha" to "butylated hydroxyanisole",
        "bht" to "butylated hydroxytoluene",
        "edta" to "ethylenediaminetetraacetic acid",
        "pgpr" to "polyglycerol polyricinoleate",
        "sles" to "sodium laureth sulfate",
        "sls" to "sodium lauryl sulfate"
    )

    // ------- Subclass → parent relationships -------
    private val subClassRelations = mapOf(
        "soy lecithin" to "lecithin",
        "sunflower lecithin" to "lecithin",
        "palm oil" to "vegetable oil",
        "canola oil" to "vegetable oil",
        "soybean oil" to "vegetable oil",
        "sunflower oil" to "vegetable oil",
        "coconut oil" to "vegetable oil",
        "cane sugar" to "sugar",
        "beet sugar" to "sugar",
        "sodium chloride" to "salt",
        "potassium chloride" to "salt",
        "citric acid" to "acidity regulator",
        "malic acid" to "acidity regulator",
        "lactic acid" to "acidity regulator",
        "tartaric acid" to "acidity regulator",
        "acetic acid" to "acidity regulator",
        "phosphoric acid" to "acidity regulator"
    )

    // ------- Ingredient category groups -------
    val categories: Map<String, OntologyCategory> = buildCategories()

    private fun buildCategories(): Map<String, OntologyCategory> {
        val cats = listOf(
            OntologyCategory(
                name = "sweeteners",
                members = setOf(
                    "sucrose", "fructose", "glucose", "dextrose", "maltose",
                    "stevia", "erythritol", "xylitol", "sorbitol", "mannitol",
                    "aspartame", "sucralose", "acesulfame potassium",
                    "sugar", "cane sugar", "beet sugar", "high fructose corn syrup",
                    "corn syrup", "corn syrup solids", "malt syrup", "maple syrup",
                    "agave syrup", "rice syrup", "brown sugar", "raw sugar",
                    "invert sugar", "molasses", "honey"
                )
            ),
            OntologyCategory(
                name = "sugar_alcohols",
                parentCategory = "sweeteners",
                members = setOf("erythritol", "xylitol", "sorbitol", "mannitol", "maltitol", "lactitol")
            ),
            OntologyCategory(
                name = "preservatives",
                members = setOf(
                    "sodium benzoate", "potassium sorbate", "calcium propionate",
                    "sodium nitrate", "sodium nitrite", "potassium nitrate",
                    "sodium diacetate", "propionic acid", "sorbic acid",
                    "benzoic acid", "tertiary butylhydroquinone",
                    "butylated hydroxyanisole", "butylated hydroxytoluene"
                )
            ),
            OntologyCategory(
                name = "emulsifiers",
                members = setOf(
                    "soy lecithin", "sunflower lecithin", "lecithin",
                    "mono and diglycerides", "diglycerides", "monoglycerides",
                    "polysorbate 80", "polysorbate 60", "polysorbate 20",
                    "sodium stearoyl lactylate", "calcium stearoyl lactylate",
                    "polyglycerol polyricinoleate", "diacetyl tartaric acid esters"
                )
            ),
            OntologyCategory(
                name = "acidity_regulators",
                members = setOf(
                    "citric acid", "malic acid", "lactic acid",
                    "tartaric acid", "acetic acid", "phosphoric acid",
                    "fumaric acid", "adipic acid", "glucono delta lactone",
                    "sodium citrate", "potassium citrate", "calcium citrate",
                    "sodium acetate", "potassium acetate", "acidity regulator"
                )
            ),
            OntologyCategory(
                name = "thickeners",
                members = setOf(
                    "xanthan gum", "guar gum", "carrageenan", "pectin", "gelatin",
                    "carob bean gum", "locust bean gum", "agar", "agar agar",
                    "cellulose gum", "methyl cellulose", "hydroxypropyl cellulose",
                    "modified starch", "modified corn starch", "tapioca starch",
                    "potato starch", "corn starch"
                )
            ),
            OntologyCategory(
                name = "colors",
                members = setOf(
                    "caramel color", "titanium dioxide", "red 40", "yellow 5",
                    "yellow 6", "blue 1", "blue 2", "red 3",
                    "annatto", "carmine", "beet juice", "turmeric",
                    "paprika extract", "beta carotene", "riboflavin"
                )
            ),
            OntologyCategory(
                name = "vitamins",
                members = setOf(
                    "niacin", "riboflavin", "thiamine mononitrate", "folic acid",
                    "ascorbic acid", "tocopherols", "vitamin d", "vitamin b12",
                    "pantothenic acid", "biotin", "pyridoxine"
                )
            ),
            OntologyCategory(
                name = "minerals",
                members = setOf(
                    "calcium carbonate", "reduced iron", "zinc oxide",
                    "potassium iodide", "ferrous sulfate", "ferric orthophosphate",
                    "zinc sulfate", "magnesium oxide", "calcium phosphate",
                    "dicalcium phosphate", "tricalcium phosphate"
                )
            ),
            OntologyCategory(
                name = "flavor_enhancers",
                members = setOf(
                    "monosodium glutamate", "disodium inosinate", "disodium guanylate",
                    "yeast extract", "autolyzed yeast extract", "hydrolyzed vegetable protein",
                    "hydrolyzed soy protein", "hydrolyzed corn protein"
                )
            ),
            OntologyCategory(
                name = "leavening_agents",
                members = setOf(
                    "baking soda", "sodium bicarbonate", "ammonium bicarbonate",
                    "monocalcium phosphate", "sodium aluminum phosphate",
                    "sodium acid pyrophosphate", "cream of tartar",
                    "potassium hydrogen tartrate"
                )
            ),
            OntologyCategory(
                name = "dairy",
                members = setOf(
                    "milk", "cheese", "butter", "whey", "lactose", "cream",
                    "skim milk", "whole milk", "milk fat", "buttermilk",
                    "casein", "caseinate", "sodium caseinate", "whey protein",
                    "milk protein", "nonfat dry milk", "nonfat milk"
                )
            ),
            OntologyCategory(
                name = "allergens",
                members = setOf(
                    "milk", "eggs", "soy", "wheat", "peanuts", "tree nuts",
                    "fish", "shellfish", "sesame", "soy lecithin", "whey",
                    "lactose", "casein", "gluten"
                )
            )
        )
        return cats.associateBy { it.name }
    }

    /**
     * Resolves E-number and abbreviation aliases to their canonical ingredient names.
     * Subclass relationships remain available through [isSubclassOf] but are not
     * applied as automatic corrections, so exact vocabulary terms remain stable.
     */
    fun resolve(token: String): String? {
        val clean = token.lowercase(Locale.ROOT).trim()

        val eNumber = ENumberEntry.find(clean)
        if (eNumber != null) return eNumber.canonicalName

        val abbrevMatch = abbreviations[clean]
        if (abbrevMatch != null) return abbrevMatch

        return null
    }

    /**
     * Returns the category name for the given canonical ingredient, or null if uncategorized.
     */
    fun categoryOf(canonical: String): String? {
        val clean = canonical.lowercase(Locale.ROOT).trim()
        return categories.values.firstOrNull { clean in it.members }?.name
    }

    /**
     * Returns all members of the given category, or empty set if unknown.
     */
    fun membersOf(category: String): Set<String> {
        return categories[category]?.members ?: emptySet()
    }

    /**
     * Checks if a child ingredient belongs to a parent category.
     */
    fun isSubclassOf(child: String, parent: String): Boolean {
        val cleanChild = child.lowercase(Locale.ROOT).trim()
        val cleanParent = parent.lowercase(Locale.ROOT).trim()
        return subClassRelations[cleanChild] == cleanParent
    }
}
