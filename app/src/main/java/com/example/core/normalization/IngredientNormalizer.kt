package com.example.core.normalization

import java.util.Locale

object IngredientNormalizer {
    private val punctuationRegex = Regex("[.,;:|*•~_\\-\"]")

    /**
     * Normalizes a single ingredient token before ontology or additive lookup.
     * Lowercases the string, strips common punctuation while preserving parentheses 
     * (critical for additive codes like E460(i)), and collapses duplicate whitespace.
     */
    fun normalize(text: String): String {
        var clean = text.lowercase(Locale.ROOT).trim()
        
        // Replace punctuation with spaces to avoid joining words
        clean = clean.replace(punctuationRegex, " ")
        
        // Standardize spacing
        clean = clean.replace(Regex("\\s+"), " ")
        
        return clean.trim()
    }
}
