package com.example.core.additives

import com.example.core.ontology.IngredientCategory
import java.util.Locale

object ENumberRepository {
    private val db = listOf(
        ENumberEntry(
            code = "E330",
            canonicalName = "citric acid",
            category = IngredientCategory.ACIDITY_REGULATOR,
            aliases = listOf("citnc acid", "citricacid", "acid citric"),
            description = "acidity regulator and antioxidant commonly found in citrus fruits",
            commonOcrErrors = listOf("citnc acid", "citric ac1d", "cltrlc")
        ),
        ENumberEntry(
            code = "E621",
            canonicalName = "monosodium glutamate",
            category = IngredientCategory.FLAVOUR_ENHANCER,
            aliases = listOf("msg", "mono sodium glutamate", "monosodum glutamate"),
            description = "flavour enhancer commonly used in savory processed foods",
            commonOcrErrors = listOf("e62i", "e62l", "mono sodium glutamat", "monosodum")
        ),
        ENumberEntry(
            code = "E300",
            canonicalName = "ascorbic acid",
            category = IngredientCategory.PRESERVATIVE,
            aliases = listOf("vitamin c", "ascorbicacid"),
            description = "antioxidant and preservative, also known as Vitamin C",
            commonOcrErrors = listOf("ascarbic", "e3oo", "e3o0")
        ),
        ENumberEntry(
            code = "E322",
            canonicalName = "soy lecithin",
            category = IngredientCategory.EMULSIFIER,
            aliases = listOf("lecithine", "lecithin", "sojalecithin"),
            description = "emulsifier derived from soybeans, helps mix fats and water",
            commonOcrErrors = listOf("e322i", "e322l", "e3221")
        ),
        ENumberEntry(
            code = "E415",
            canonicalName = "xanthan gum",
            category = IngredientCategory.STABILIZER,
            aliases = listOf("xanthan", "xanthangum"),
            description = "thickening agent and stabilizer produced by bacterial fermentation",
            commonOcrErrors = listOf("e415i", "e415l", "e4151")
        ),
        ENumberEntry(
            code = "E412",
            canonicalName = "guar gum",
            category = IngredientCategory.STABILIZER,
            aliases = listOf("guargum", "guar"),
            description = "natural thickening agent and stabilizer obtained from guar beans",
            commonOcrErrors = listOf("e412i", "e412l")
        ),
        ENumberEntry(
            code = "E407",
            canonicalName = "carrageenan",
            category = IngredientCategory.STABILIZER,
            aliases = listOf("carrageen"),
            description = "thickening agent and stabilizer extracted from red edible seaweeds",
            commonOcrErrors = listOf("e407i", "e407l")
        ),
        ENumberEntry(
            code = "E282",
            canonicalName = "calcium propionate",
            category = IngredientCategory.PRESERVATIVE,
            aliases = listOf("calciumpropionate"),
            description = "preservative commonly used in bread and bakery goods to prevent mold",
            commonOcrErrors = listOf("e282i", "e282l")
        ),
        ENumberEntry(
            code = "E211",
            canonicalName = "sodium benzoate",
            category = IngredientCategory.PRESERVATIVE,
            aliases = listOf("sodiumbenzoate"),
            description = "preservative commonly used in acidic foods and beverages",
            commonOcrErrors = listOf("e211i", "e211l")
        ),
        ENumberEntry(
            code = "E202",
            canonicalName = "potassium sorbate",
            category = IngredientCategory.PRESERVATIVE,
            aliases = listOf("potassiumsorbate"),
            description = "chemical preservative used to prevent mold and yeast growth",
            commonOcrErrors = listOf("e202i", "e202l")
        ),
        ENumberEntry(
            code = "E150a",
            canonicalName = "caramel color",
            category = IngredientCategory.COLORING,
            aliases = listOf("caramel colour", "caramel"),
            description = "food coloring agent manufactured by heating carbohydrates",
            commonOcrErrors = listOf("e150", "e15o")
        ),
        ENumberEntry(
            code = "E171",
            canonicalName = "titanium dioxide",
            category = IngredientCategory.COLORING,
            aliases = listOf("titaniumdioxide"),
            description = "white coloring pigment used in candies, pastries, and white foods",
            commonOcrErrors = listOf("e171i", "e171l")
        ),
        ENumberEntry(
            code = "E460(i)",
            canonicalName = "microcrystalline cellulose",
            category = IngredientCategory.STABILIZER,
            aliases = listOf("cellulose", "microcrystallinecellulose", "e460i", "e460"),
            description = "anti-caking agent, stabilizer, and bulking agent derived from wood pulp",
            commonOcrErrors = listOf("e460c", "e460i", "e460l", "e4601", "e460(l)", "e460(1)")
        ),
        ENumberEntry(
            code = "E460(ii)",
            canonicalName = "powdered cellulose",
            category = IngredientCategory.STABILIZER,
            aliases = listOf("powderedcellulose", "e460ii"),
            description = "anti-caking agent and texturizer commonly used in grated cheese and powdered foods",
            commonOcrErrors = listOf("e460ii", "e460c(ii)", "e46011", "e460ll")
        ),
        ENumberEntry(
            code = "E450",
            canonicalName = "diphosphates",
            category = IngredientCategory.EMULSIFIER,
            aliases = listOf("sodium diphosphate", "diphosphate"),
            description = "emulsifier, stabilizer, and buffering agent used in processed foods",
            commonOcrErrors = listOf("e450a", "e45o", "e45o(a)")
        ),
        ENumberEntry(
            code = "E500",
            canonicalName = "sodium carbonates",
            category = IngredientCategory.ACIDITY_REGULATOR,
            aliases = listOf("sodium carbonate", "baking soda", "sodium bicarbonate"),
            description = "acidity regulator and raising agent commonly used in baking",
            commonOcrErrors = listOf("e5oo", "e5o0", "e500i")
        )
    )

    private val aliasMap: Map<String, ENumberEntry> = buildAliasMap()

    private fun buildAliasMap(): Map<String, ENumberEntry> {
        val map = mutableMapOf<String, ENumberEntry>()
        db.forEach { entry ->
            val codeKey = entry.code.lowercase(Locale.ROOT).trim()
            map[codeKey] = entry
            val canonKey = entry.canonicalName.lowercase(Locale.ROOT).trim()
            map[canonKey] = entry
            entry.aliases.forEach { alias ->
                val aliasKey = alias.lowercase(Locale.ROOT).trim()
                map[aliasKey] = entry
            }
            entry.commonOcrErrors.forEach { err ->
                val errKey = err.lowercase(Locale.ROOT).trim()
                map[errKey] = entry
            }
        }
        return map
    }

    fun find(code: String): ENumberEntry? {
        val clean = code.lowercase(Locale.ROOT).trim()
        return aliasMap[clean]
    }

    fun getAll(): List<ENumberEntry> = db
}
