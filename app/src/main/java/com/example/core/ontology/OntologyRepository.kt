package com.example.core.ontology

import java.util.Locale

object OntologyRepository {
    private val db = listOf(
        IngredientEntry(
            canonicalName = "monosodium glutamate",
            aliases = listOf("msg", "mono sodium glutamate", "monosodum glutamate", "sodium glutamate"),
            category = IngredientCategory.FLAVOUR_ENHANCER,
            additiveCode = "E621",
            tags = listOf("additive", "flavor_enhancer", "ultra_processed")
        ),
        IngredientEntry(
            canonicalName = "citric acid",
            aliases = listOf("citnc acid", "citricacid", "acid citric"),
            category = IngredientCategory.ACIDITY_REGULATOR,
            additiveCode = "E330",
            tags = listOf("additive", "acidity_regulator", "preservative")
        ),
        IngredientEntry(
            canonicalName = "microcrystalline cellulose",
            aliases = listOf("cellulose", "microcrystallinecellulose", "e460i", "e460"),
            category = IngredientCategory.STABILIZER,
            additiveCode = "E460(i)",
            tags = listOf("additive", "stabilizer", "emulsifier", "ultra_processed")
        ),
        IngredientEntry(
            canonicalName = "soy lecithin",
            aliases = listOf("lecithine", "lecithin", "sojalecithin"),
            category = IngredientCategory.EMULSIFIER,
            additiveCode = "E322",
            tags = listOf("additive", "emulsifier", "allergen")
        ),
        IngredientEntry(
            canonicalName = "ascorbic acid",
            aliases = listOf("vitamin c", "ascorbicacid"),
            category = IngredientCategory.PRESERVATIVE,
            additiveCode = "E300",
            tags = listOf("additive", "preservative", "antioxidant")
        ),
        IngredientEntry(
            canonicalName = "xanthan gum",
            aliases = listOf("xanthan", "xanthangum"),
            category = IngredientCategory.STABILIZER,
            additiveCode = "E415",
            tags = listOf("additive", "stabilizer", "thickener")
        ),
        IngredientEntry(
            canonicalName = "guar gum",
            aliases = listOf("guargum", "guar"),
            category = IngredientCategory.STABILIZER,
            additiveCode = "E412",
            tags = listOf("additive", "stabilizer", "thickener")
        ),
        IngredientEntry(
            canonicalName = "carrageenan",
            aliases = listOf("carrageen"),
            category = IngredientCategory.STABILIZER,
            additiveCode = "E407",
            tags = listOf("additive", "stabilizer", "thickener", "ultra_processed")
        ),
        IngredientEntry(
            canonicalName = "calcium propionate",
            aliases = listOf("calciumpropionate"),
            category = IngredientCategory.PRESERVATIVE,
            additiveCode = "E282",
            tags = listOf("additive", "preservative")
        ),
        IngredientEntry(
            canonicalName = "sodium benzoate",
            aliases = listOf("sodiumbenzoate"),
            category = IngredientCategory.PRESERVATIVE,
            additiveCode = "E211",
            tags = listOf("additive", "preservative")
        ),
        IngredientEntry(
            canonicalName = "potassium sorbate",
            aliases = listOf("potassiumsorbate"),
            category = IngredientCategory.PRESERVATIVE,
            additiveCode = "E202",
            tags = listOf("additive", "preservative")
        ),
        IngredientEntry(
            canonicalName = "caramel color",
            aliases = listOf("caramel colour", "caramel"),
            category = IngredientCategory.COLORING,
            additiveCode = "E150a",
            tags = listOf("additive", "coloring")
        ),
        IngredientEntry(
            canonicalName = "titanium dioxide",
            aliases = listOf("titaniumdioxide"),
            category = IngredientCategory.COLORING,
            additiveCode = "E171",
            tags = listOf("additive", "coloring")
        ),
        IngredientEntry(
            canonicalName = "powdered cellulose",
            aliases = listOf("powderedcellulose", "e460ii"),
            category = IngredientCategory.STABILIZER,
            additiveCode = "E460(ii)",
            tags = listOf("additive", "stabilizer", "emulsifier")
        ),
        IngredientEntry(
            canonicalName = "diphosphates",
            aliases = listOf("sodium diphosphate", "diphosphate"),
            category = IngredientCategory.EMULSIFIER,
            additiveCode = "E450",
            tags = listOf("additive", "emulsifier", "stabilizer")
        ),
        IngredientEntry(
            canonicalName = "sodium carbonates",
            aliases = listOf("sodium carbonate", "baking soda", "sodium bicarbonate"),
            category = IngredientCategory.ACIDITY_REGULATOR,
            additiveCode = "E500",
            tags = listOf("additive", "acidity_regulator")
        ),
        IngredientEntry(
            canonicalName = "sucrose",
            aliases = listOf("sugar", "cane sugar", "beet sugar", "raw sugar", "brown sugar", "invert sugar"),
            category = IngredientCategory.SWEETENER,
            additiveCode = null,
            tags = listOf("sweetener", "high_sugar")
        ),
        IngredientEntry(
            canonicalName = "high fructose corn syrup",
            aliases = listOf("hfcs", "corn syrup", "corn syrup solids"),
            category = IngredientCategory.SWEETENER,
            additiveCode = null,
            tags = listOf("sweetener", "high_sugar", "ultra_processed")
        ),
        IngredientEntry(
            canonicalName = "stevia",
            aliases = listOf("stevia extract"),
            category = IngredientCategory.SWEETENER,
            additiveCode = null,
            tags = listOf("sweetener", "artificial_sweetener")
        ),
        IngredientEntry(
            canonicalName = "aspartame",
            aliases = emptyList(),
            category = IngredientCategory.SWEETENER,
            additiveCode = null,
            tags = listOf("sweetener", "artificial_sweetener")
        ),
        IngredientEntry(
            canonicalName = "sucralose",
            aliases = emptyList(),
            category = IngredientCategory.SWEETENER,
            additiveCode = null,
            tags = listOf("sweetener", "artificial_sweetener")
        ),
        IngredientEntry(
            canonicalName = "acesulfame potassium",
            aliases = listOf("acesulfame k"),
            category = IngredientCategory.SWEETENER,
            additiveCode = null,
            tags = listOf("sweetener", "artificial_sweetener")
        ),
        IngredientEntry(
            canonicalName = "palm oil",
            aliases = emptyList(),
            category = IngredientCategory.OIL,
            additiveCode = null,
            tags = listOf("oil", "saturated_fat", "ultra_processed")
        ),
        IngredientEntry(
            canonicalName = "canola oil",
            aliases = emptyList(),
            category = IngredientCategory.OIL,
            additiveCode = null,
            tags = listOf("oil", "vegetable_oil")
        ),
        IngredientEntry(
            canonicalName = "soybean oil",
            aliases = emptyList(),
            category = IngredientCategory.OIL,
            additiveCode = null,
            tags = listOf("oil", "vegetable_oil")
        ),
        IngredientEntry(
            canonicalName = "coconut oil",
            aliases = emptyList(),
            category = IngredientCategory.OIL,
            additiveCode = null,
            tags = listOf("oil", "saturated_fat")
        )
    )

    private val aliasMap: Map<String, IngredientEntry> = buildAliasMap()

    private fun buildAliasMap(): Map<String, IngredientEntry> {
        val map = mutableMapOf<String, IngredientEntry>()
        db.forEach { entry ->
            val canonKey = entry.canonicalName.lowercase(Locale.ROOT).trim()
            map[canonKey] = entry
            entry.aliases.forEach { alias ->
                val aliasKey = alias.lowercase(Locale.ROOT).trim()
                map[aliasKey] = entry
            }
            if (entry.additiveCode != null) {
                val codeKey = entry.additiveCode.lowercase(Locale.ROOT).trim()
                map[codeKey] = entry
            }
        }
        return map
    }

    fun find(name: String): IngredientEntry? {
        val clean = name.lowercase(Locale.ROOT).trim()
        return aliasMap[clean]
    }

    fun getAll(): List<IngredientEntry> = db
}
