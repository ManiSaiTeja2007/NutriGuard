package com.example.core.intelligence

import com.example.core.ocr.OCRLine
import java.util.Locale

data class AllergenInterpretation(
    val originalText: String,
    val allergensDetected: List<String>,
    val isMayContain: Boolean,
    val warnings: List<String>
)

object AllergenInterpreter {
    private val KNOWN_ALLERGENS = listOf(
        "peanut", "milk", "wheat", "soy", "egg", "fish", "shellfish", "tree nut", "almond",
        "walnut", "cashew", "pistachio", "pecan", "hazelnut", "sesame", "mustard", "gluten"
    )

    fun interpret(lines: List<OCRLine>): AllergenInterpretation {
        val fullText = lines.flatMap { it.words }.joinToString(" ") { it.text }
        val lowercase = fullText.lowercase(Locale.ROOT)

        val detected = KNOWN_ALLERGENS.filter { allergen ->
            lowercase.contains(allergen)
        }

        val isMayContain = lowercase.contains("may contain") || lowercase.contains("processed in a facility")

        val warnings = detected.map { allergen ->
            "Contains allergen: $allergen"
        }

        return AllergenInterpretation(
            originalText = fullText,
            allergensDetected = detected,
            isMayContain = isMayContain,
            warnings = warnings
        )
    }
}
