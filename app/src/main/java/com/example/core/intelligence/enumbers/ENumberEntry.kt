package com.example.core.intelligence.enumbers

import java.util.Locale

data class ENumberEntry(
    val code: String,
    val canonicalName: String,
    val category: String,
    val aliases: List<String> = emptyList(),
    val commonOcrErrors: List<String> = emptyList()
) {
    companion object {
        // Curated static database of additives to prevent scattered hardcoded maps
        private val db = listOf(
            ENumberEntry(
                code = "e330",
                canonicalName = "citric acid",
                category = "acid / antioxidant",
                aliases = listOf("citnc acid", "citricacid", "acid citric"),
                commonOcrErrors = listOf("citnc acid", "citric ac1d", "cltrlc")
            ),
            ENumberEntry(
                code = "e621",
                canonicalName = "monosodium glutamate",
                category = "flavor enhancer",
                aliases = listOf("msg", "mono sodium glutamate", "monosodum glutamate"),
                commonOcrErrors = listOf("e62i", "e62l", "mono sodium glutamat", "monosodum")
            ),
            ENumberEntry(
                code = "e300",
                canonicalName = "ascorbic acid",
                category = "antioxidant",
                aliases = listOf("vitamin c", "ascorbicacid"),
                commonOcrErrors = listOf("ascarbic", "e3oo", "e3o0")
            ),
            ENumberEntry(
                code = "e322",
                canonicalName = "soy lecithin",
                category = "emulsifier",
                aliases = listOf("lecithine", "lecithin", "sojalecithin"),
                commonOcrErrors = listOf("e322i", "e322l", "e3221")
            ),
            ENumberEntry(
                code = "e415",
                canonicalName = "xanthan gum",
                category = "thickener / stabilizer",
                aliases = listOf("xanthan", "xanthangum"),
                commonOcrErrors = listOf("e415i", "e415l", "e4151")
            ),
            ENumberEntry(
                code = "e412",
                canonicalName = "guar gum",
                category = "thickener",
                aliases = listOf("guargum", "guar"),
                commonOcrErrors = listOf("e412i", "e412l")
            ),
            ENumberEntry(
                code = "e407",
                canonicalName = "carrageenan",
                category = "thickener / stabilizer",
                aliases = listOf("carrageen"),
                commonOcrErrors = listOf("e407i", "e407l")
            ),
            ENumberEntry(
                code = "e282",
                canonicalName = "calcium propionate",
                category = "preservative",
                aliases = listOf("calciumpropionate"),
                commonOcrErrors = listOf("e282i", "e282l")
            ),
            ENumberEntry(
                code = "e211",
                canonicalName = "sodium benzoate",
                category = "preservative",
                aliases = listOf("sodiumbenzoate"),
                commonOcrErrors = listOf("e211i", "e211l")
            ),
            ENumberEntry(
                code = "e202",
                canonicalName = "potassium sorbate",
                category = "preservative",
                aliases = listOf("potassiumsorbate"),
                commonOcrErrors = listOf("e202i", "e202l")
            ),
            ENumberEntry(
                code = "e150a",
                canonicalName = "caramel color",
                category = "color",
                aliases = listOf("caramel colour", "caramel"),
                commonOcrErrors = listOf("e150", "e15o")
            ),
            ENumberEntry(
                code = "e171",
                canonicalName = "titanium dioxide",
                category = "color",
                aliases = listOf("titaniumdioxide"),
                commonOcrErrors = listOf("e171i", "e171l")
            ),
            // Expanded Stage 8 Additives
            ENumberEntry(
                code = "e460(i)",
                canonicalName = "microcrystalline cellulose",
                category = "thickener / stabilizer",
                aliases = listOf("cellulose", "microcrystallinecellulose", "e460i", "e460"),
                commonOcrErrors = listOf("e460c", "e460i", "e460l", "e4601", "e460(l)", "e460(1)")
            ),
            ENumberEntry(
                code = "e460(ii)",
                canonicalName = "powdered cellulose",
                category = "thickener / emulsifier",
                aliases = listOf("powderedcellulose", "e460ii"),
                commonOcrErrors = listOf("e460ii", "e460c(ii)", "e46011", "e460ll")
            ),
            ENumberEntry(
                code = "e450",
                canonicalName = "diphosphates",
                category = "emulsifier / stabilizer",
                aliases = listOf("sodium diphosphate", "diphosphate"),
                commonOcrErrors = listOf("e450a", "e45o", "e45o(a)")
            ),
            ENumberEntry(
                code = "e500",
                canonicalName = "sodium carbonates",
                category = "acidity regulator / raising agent",
                aliases = listOf("sodium carbonate", "baking soda", "sodium bicarbonate"),
                commonOcrErrors = listOf("e5oo", "e5o0", "e500i")
            )
        )

        private val lookupMap = db.associateBy { it.code.lowercase(Locale.ROOT).trim() }

        /**
         * Resolves an E-number code (e.g. "e330", "E621") to its structured ENumberEntry.
         * Returns null if not present in our offline additives directory.
         */
        fun find(code: String): ENumberEntry? {
            val clean = code.lowercase(Locale.ROOT).trim()
            // Direct map lookup
            val exact = lookupMap[clean]
            if (exact != null) return exact

            // Fallback: check if clean matches any ENumberEntry code aliases or commonOcrErrors
            return db.firstOrNull { entry ->
                entry.code == clean || 
                entry.aliases.any { it.lowercase(Locale.ROOT).trim() == clean } ||
                entry.commonOcrErrors.any { it.lowercase(Locale.ROOT).trim() == clean }
            }
        }

        /**
         * Returns all known E-number entries.
         */
        fun getAll(): List<ENumberEntry> = db
    }
}
