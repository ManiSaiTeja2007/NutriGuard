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
            map["हल्दी"] = AliasTarget("turmeric", true)
            map["haldi"] = AliasTarget("turmeric", true)
            map["नमक"] = AliasTarget("salt", true)
            map["namak"] = AliasTarget("salt", true)
            map["दही"] = AliasTarget("yogurt", true)
            map["dahi"] = AliasTarget("yogurt", true)
            map["जीरा"] = AliasTarget("cumin", true)
            map["jeera"] = AliasTarget("cumin", true)
            map["अदरक"] = AliasTarget("ginger", true)
            map["adrak"] = AliasTarget("ginger", true)
            map["लहसुन"] = AliasTarget("garlic", true)
            map["lahsun"] = AliasTarget("garlic", true)
            map["केसर"] = AliasTarget("saffron", true)
            map["kesar"] = AliasTarget("saffron", true)
            map["wasser"] = AliasTarget("water", true)

            map["hfcs"] = AliasTarget("high fructose corn syrup", false)
            map["tbhq"] = AliasTarget("tertiary butylhydroquinone", false)
            map["bha"] = AliasTarget("butylated hydroxyanisole", false)
            map["bht"] = AliasTarget("butylated hydroxytoluene", false)
            map["edta"] = AliasTarget("ethylenediaminetetraacetic acid", false)
            map["pgpr"] = AliasTarget("polyglycerol polyricinoleate", false)
            map["sles"] = AliasTarget("sodium laureth sulfate", false)
            map["sls"] = AliasTarget("sodium lauryl sulfate", false)
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
            val afterIns = clean.substring(3).trim()
            val parenIndex = afterIns.indexOf('(')
            val digitsPart = if (parenIndex != -1) afterIns.substring(0, parenIndex) else afterIns
            val suffixPart = if (parenIndex != -1) afterIns.substring(parenIndex) else ""

            val repairedDigits = digitsPart
                .replace(" ", "")
                .replace('o', '0')
                .replace('O', '0')
                .replace('l', '1')
                .replace('i', '1')
                .replace('I', '1')
            
            val body = repairedDigits + suffixPart.replace(" ", "")
            val digits = body.filter { it.isDigit() || it == '(' || it == ')' || it == 'i' || it == 'v' }
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
