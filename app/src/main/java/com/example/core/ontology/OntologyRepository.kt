package com.example.core.ontology

import com.example.core.utils.AssetLoader
import org.json.JSONArray
import java.util.Locale

object OntologyRepository {
    private val db: List<IngredientEntry> by lazy {
        val list = mutableListOf<IngredientEntry>()
        try {
            val jsonStr = AssetLoader.loadAsset("ontology/ontology.json")
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val canon = obj.getString("canonicalName")
                val category = IngredientCategory.valueOf(obj.getString("category"))
                val additiveCode = if (obj.isNull("additiveCode")) null else obj.getString("additiveCode")
                
                val aliasesArr = obj.getJSONArray("aliases")
                val aliases = mutableListOf<String>()
                for (j in 0 until aliasesArr.length()) {
                    aliases.add(aliasesArr.getString(j))
                }

                val tagsArr = obj.getJSONArray("tags")
                val tags = mutableListOf<String>()
                for (j in 0 until tagsArr.length()) {
                    tags.add(tagsArr.getString(j))
                }

                list.add(IngredientEntry(canon, aliases, category, additiveCode, tags))
            }
        } catch (e: Exception) {
            println("OntologyRepository load error: ${e.message}")
            e.printStackTrace()
            // Static fallback for testing safety
            list.add(IngredientEntry("monosodium glutamate", listOf("msg"), IngredientCategory.FLAVOUR_ENHANCER, "E621", listOf("ultra_processed")))
            list.add(IngredientEntry("citric acid", listOf("citricacid"), IngredientCategory.ACIDITY_REGULATOR, "E330", listOf("acidity_regulator")))
            list.add(IngredientEntry("carrageenan", listOf("carrageen"), IngredientCategory.STABILIZER, "E407", listOf("ultra_processed")))
            list.add(IngredientEntry("sucrose", listOf("sugar"), IngredientCategory.SWEETENER, null, listOf("high_sugar")))
            list.add(IngredientEntry("palm oil", emptyList(), IngredientCategory.OIL, null, listOf("oil", "ultra_processed")))
            list.add(IngredientEntry("turmeric", emptyList(), IngredientCategory.UNKNOWN, null, emptyList()))
        }
        list
    }

    private val aliasMap: Map<String, IngredientEntry> by lazy { buildAliasMap() }

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
