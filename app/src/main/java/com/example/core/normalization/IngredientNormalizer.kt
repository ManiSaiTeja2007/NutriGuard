package com.example.core.normalization

import java.util.Locale

data class NormalizedIngredient(
    val originalText: String,
    val normalizedText: String
)

interface IngredientNormalizer {
    fun normalize(input: String): NormalizedIngredient
}

object DefaultIngredientNormalizer : IngredientNormalizer {
    private val punctuationRegex = Regex("[.,;:|*•~_\\-\"]")

    override fun normalize(input: String): NormalizedIngredient {
        var clean = input.lowercase(Locale.ROOT).trim()
        
        // Punctuation cleanup (preserve parentheses for E-numbers/INS like E460(i))
        clean = clean.replace(punctuationRegex, " ")
        
        // Spacing normalization
        clean = clean.replace(Regex("\\s+"), " ")
        
        // Normalization of common OCR spacing issues in E-numbers (e.g., "e 621" -> "e621")
        if (clean.startsWith("e ") || clean.startsWith("ins ")) {
            clean = clean.replace(" ", "")
        }

        return NormalizedIngredient(
            originalText = input,
            normalizedText = clean.trim()
        )
    }
}
