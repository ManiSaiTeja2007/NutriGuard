package com.example.core.ambiguity

import com.example.core.utils.AssetLoader
import org.json.JSONArray
import java.util.Locale

object AmbiguityResolver {
    private val ambiguousTerms = mutableSetOf<String>()

    init {
        try {
            val jsonStr = AssetLoader.loadAsset("ambiguity/ambiguity.json")
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                ambiguousTerms.add(obj.getString("term").lowercase(Locale.ROOT).trim())
            }
        } catch (e: Exception) {
            // Static fallbacks
            ambiguousTerms.addAll(listOf(
                "natural flavors", "natural flavor", "spices", 
                "vegetable oil", "color added", "flavouring substances"
            ))
        }
    }

    /**
     * Returns true if the ingredient is inherently ambiguous and should not be aggressively inferred.
     */
    fun isAmbiguous(name: String): Boolean {
        val clean = name.lowercase(Locale.ROOT).trim()
        return ambiguousTerms.contains(clean) || 
               clean.contains("flavor") || 
               clean.contains("flavour") || 
               clean == "spice" || 
               clean == "spices" || 
               clean == "vegetable oil"
    }
}
