package com.example.core.intelligence

import com.example.core.ocr.OCRLine
import java.util.Locale

data class NutritionInterpretation(
    val originalText: String,
    val nutrients: Map<String, String>,
    val warnings: List<String>
)

object NutritionInterpreter {
    fun interpret(lines: List<OCRLine>): NutritionInterpretation {
        val fullText = lines.flatMap { it.words }.joinToString(" ") { it.text }
        val lowercase = fullText.lowercase(Locale.ROOT)

        val nutrientMap = mutableMapOf<String, String>()
        val warnings = mutableListOf<String>()

        val patterns = listOf(
            "energy" to Regex("energy\\s*[:\\-]?\\s*(\\d+\\s*(kcal|kj|j))"),
            "calories" to Regex("calories\\s*[:\\-]?\\s*(\\d+\\s*(kcal)?)"),
            "fat" to Regex("fat\\s*[:\\-]?\\s*(\\d+g)"),
            "saturated fat" to Regex("saturated\\s*fat\\s*[:\\-]?\\s*(\\d+g)"),
            "sugar" to Regex("sugar\\s*[:\\-]?\\s*(\\d+g)"),
            "sodium" to Regex("sodium\\s*[:\\-]?\\s*(\\d+\\s*(mg|g))"),
            "protein" to Regex("protein\\s*[:\\-]?\\s*(\\d+g)")
        )

        for ((name, regex) in patterns) {
            val match = regex.find(lowercase)
            if (match != null) {
                nutrientMap[name] = match.groupValues[1]
            }
        }

        val sugarVal = nutrientMap["sugar"]?.removeSuffix("g")?.toIntOrNull()
        if (sugarVal != null && sugarVal > 15) {
            warnings.add("High sugar content: ${sugarVal}g per serving")
        }

        val sodiumVal = nutrientMap["sodium"]?.let {
            if (it.endsWith("mg")) {
                it.removeSuffix("mg").toIntOrNull()
            } else if (it.endsWith("g")) {
                ((it.removeSuffix("g").toFloatOrNull() ?: 0f) * 1000).toInt()
            } else {
                null
            }
        }
        if (sodiumVal != null && sodiumVal > 400) {
            warnings.add("High sodium content: ${sodiumVal}mg per serving")
        }

        return NutritionInterpretation(
            originalText = fullText,
            nutrients = nutrientMap,
            warnings = warnings
        )
    }
}
