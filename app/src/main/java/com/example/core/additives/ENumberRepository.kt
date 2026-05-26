package com.example.core.additives

import com.example.core.ontology.IngredientCategory
import com.example.core.utils.AssetLoader
import org.json.JSONArray
import java.util.Locale

object ENumberRepository {
    private val db: List<ENumberEntry> by lazy {
        val list = mutableListOf<ENumberEntry>()
        try {
            val jsonStr = AssetLoader.loadAsset("additives/additives.json")
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val code = obj.getString("code")
                val canon = obj.getString("canonicalName")
                val category = IngredientCategory.valueOf(obj.getString("category"))
                val desc = obj.getString("description")
                
                val aliasesArr = obj.getJSONArray("aliases")
                val aliases = mutableListOf<String>()
                for (j in 0 until aliasesArr.length()) {
                    aliases.add(aliasesArr.getString(j))
                }

                val errorsArr = obj.getJSONArray("commonOcrErrors")
                val errors = mutableListOf<String>()
                for (j in 0 until errorsArr.length()) {
                    errors.add(errorsArr.getString(j))
                }

                list.add(ENumberEntry(code, canon, category, aliases, desc, errors))
            }
        } catch (e: Exception) {
            println("ENumberRepository load error: ${e.message}")
            e.printStackTrace()
            // Static fallback for safety
            list.add(ENumberEntry("E330", "citric acid", IngredientCategory.ACIDITY_REGULATOR, listOf("citricacid"), "acidity regulator and antioxidant", listOf("citnc acid")))
            list.add(ENumberEntry("E621", "monosodium glutamate", IngredientCategory.FLAVOUR_ENHANCER, listOf("msg"), "flavour enhancer", listOf("e62i")))
            list.add(ENumberEntry("E407", "carrageenan", IngredientCategory.STABILIZER, listOf("carrageen"), "thickener and stabilizer", listOf("e407i")))
            list.add(ENumberEntry("E500", "sodium carbonates", IngredientCategory.ACIDITY_REGULATOR, listOf("sodium carbonate", "baking soda", "sodium bicarbonate"), "acidity regulator and raising agent", listOf("e5oo", "e5o0")))
        }
        list
    }

    private val aliasMap: Map<String, ENumberEntry> by lazy { buildAliasMap() }

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
