package com.example.core.intelligence.enumbers

import java.util.Locale

data class ENumberEntry(
    val code: String,
    val canonicalName: String,
    val category: String
) {
    companion object {
        // Curated static database of additives to prevent scattered hardcoded maps
        private val db = listOf(
            ENumberEntry("e330", "citric acid", "acid / antioxidant"),
            ENumberEntry("e621", "monosodium glutamate", "flavor enhancer"),
            ENumberEntry("e300", "ascorbic acid", "antioxidant"),
            ENumberEntry("e322", "soy lecithin", "emulsifier"),
            ENumberEntry("e415", "xanthan gum", "thickener / stabilizer"),
            ENumberEntry("e412", "guar gum", "thickener"),
            ENumberEntry("e407", "carrageenan", "thickener / stabilizer"),
            ENumberEntry("e282", "calcium propionate", "preservative"),
            ENumberEntry("e211", "sodium benzoate", "preservative"),
            ENumberEntry("e202", "potassium sorbate", "preservative"),
            ENumberEntry("e150a", "caramel color", "color"),
            ENumberEntry("e171", "titanium dioxide", "color")
        )

        private val lookupMap = db.associateBy { it.code.lowercase(Locale.ROOT).trim() }

        /**
         * Resolves an E-number code (e.g. "e330", "E621") to its structured ENumberEntry.
         * Returns null if not present in our offline additives directory.
         */
        fun find(code: String): ENumberEntry? {
            return lookupMap[code.lowercase(Locale.ROOT).trim()]
        }

        /**
         * Returns all known E-number entries.
         */
        fun getAll(): List<ENumberEntry> = db
    }
}
