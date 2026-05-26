package com.example.core.intelligence.ontology

import com.example.core.additives.ENumberEntry
import com.example.core.additives.ENumberRepository
import com.example.core.ontology.OntologyRepository
import com.example.core.ontology.IngredientCategory
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
        val cats = mutableListOf<OntologyCategory>()
        
        // Loop over each IngredientCategory enum and populate
        IngredientCategory.values().forEach { category ->
            val membersSet = mutableSetOf<String>()
            
            // Add from OntologyRepository matching category
            OntologyRepository.getAll()
                .filter { it.category == category }
                .forEach { entry ->
                    membersSet.add(entry.canonicalName.lowercase(Locale.ROOT))
                    entry.aliases.forEach { membersSet.add(it.lowercase(Locale.ROOT)) }
                }

            // Add from ENumberRepository matching category
            ENumberRepository.getAll()
                .filter { it.category == category }
                .forEach { entry ->
                    membersSet.add(entry.canonicalName.lowercase(Locale.ROOT))
                    membersSet.add(entry.code.lowercase(Locale.ROOT))
                    entry.aliases.forEach { membersSet.add(it.lowercase(Locale.ROOT)) }
                }

            val catName = category.name.lowercase(Locale.ROOT)
            cats.add(
                OntologyCategory(
                    name = catName,
                    members = membersSet,
                    parentCategory = if (catName == "sugar_alcohols") "sweeteners" else null
                )
            )
        }
        
        // Add manual parent/child override for sugar alcohols
        val sugarAlcoholsMembers = setOf("erythritol", "xylitol", "sorbitol", "mannitol", "maltitol", "lactitol")
        cats.add(
            OntologyCategory(
                name = "sugar_alcohols",
                members = sugarAlcoholsMembers,
                parentCategory = "sweeteners"
            )
        )

        return cats.associateBy { it.name }
    }

    /**
     * Resolves E-number and abbreviation aliases to their canonical ingredient names.
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
