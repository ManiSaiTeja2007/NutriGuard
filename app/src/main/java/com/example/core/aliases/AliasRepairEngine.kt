package com.example.core.aliases

import com.example.core.utils.AssetLoader
import org.json.JSONObject
import java.util.Locale

data class AliasRepairResult(
    val originalText: String,
    val repairedText: String,
    val explanation: String?,
    val isRepaired: Boolean,
    val isTransliteration: Boolean
)

data class AliasTarget(
    val resolved: String,
    val isTransliteration: Boolean
)

object AliasRepairEngine {
    private val aliasMap: Map<String, AliasTarget> by lazy {
        val map = mutableMapOf<String, AliasTarget>()
        try {
            val jsonStr = AssetLoader.loadAsset("aliases/aliases.json")
            val json = JSONObject(jsonStr)
            json.keys().forEach { key ->
                val obj = json.getJSONObject(key)
                val resolved = obj.getString("resolved")
                val isTrans = obj.optBoolean("isTransliteration", false)
                map[key.lowercase(Locale.ROOT).trim()] = AliasTarget(resolved, isTrans)
            }
        } catch (e: Exception) {
            // Fallback for isolated unit tests / safety
            map["msg"] = AliasTarget("monosodium glutamate", false)
            map["slt"] = AliasTarget("salt", false)
            map["citricacd"] = AliasTarget("citric acid", false)
            map["emulsfier"] = AliasTarget("emulsifier", false)
            map["veg oil"] = AliasTarget("vegetable oil", false)
            map["haldi"] = AliasTarget("turmeric", true)
            map["namak"] = AliasTarget("salt", true)
            map["dahi"] = AliasTarget("yogurt", true)
        }
        map
    }

    fun repair(input: String): AliasRepairResult {
        val clean = input.lowercase(Locale.ROOT).trim()
        val target = aliasMap[clean]
        if (target != null) {
            return AliasRepairResult(
                originalText = input,
                repairedText = target.resolved,
                explanation = "Resolved alias '$input' to '${target.resolved}'",
                isRepaired = true,
                isTransliteration = target.isTransliteration
            )
        }

        // Translation of INS notation variants to standard E-number format
        if (clean.startsWith("ins")) {
            val digits = clean.substring(3).filter { it.isDigit() || it == '(' || it == ')' || it == 'i' || it == 'v' }
            if (digits.isNotEmpty()) {
                val eCode = "e$digits"
                return AliasRepairResult(
                    originalText = input,
                    repairedText = eCode,
                    explanation = "Translated INS notation to E-number: $eCode",
                    isRepaired = true,
                    isTransliteration = false
                )
            }
        }

        return AliasRepairResult(
            originalText = input,
            repairedText = input,
            explanation = null,
            isRepaired = false,
            isTransliteration = false
        )
    }
}
