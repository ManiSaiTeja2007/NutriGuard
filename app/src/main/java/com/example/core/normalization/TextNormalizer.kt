package com.example.core.normalization

import java.util.Locale

object TextNormalizer {

    /**
     * Converts noisy raw OCR text into a normalized lowercase form.
     * Preserves list delimiters (commas, semicolons, colons, parentheses) for the extraction stage,
     * but removes other OCR artifacts and recovers hyphenated linebreaks.
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""

        // 1. Convert to lowercase
        var result = text.lowercase(Locale.ROOT)

        // 2. Recover hyphenated linebreaks (e.g. "citnc-\n acid" -> "citnc acid")
        // Match a hyphen followed by optional spaces, a newline, and optional spaces
        result = result.replace(Regex("-\\s*[\\r\\n]+\\s*"), " ")
                       .replace(Regex("-\\s+"), " ")

        // 3. Normalize newlines, carriage returns, and tabs to spaces
        result = result.replace('\r', ' ')
                      .replace('\n', ' ')
                      .replace('\t', ' ')

        // 4. Unicode normalization & cleanup of common OCR junk characters (replace with spaces)
        val junkChars = charArrayOf('|', '*', '_', '•', '~', '^', '\\', '/', '#', '@', '<', '>')
        for (char in junkChars) {
            result = result.replace(char, ' ')
        }

        // 5. Standardize spaces around list delimiters (commas, semicolons, colons)
        // e.g. "slt , " -> "slt, " and "ingredients : " -> "ingredients: "
        result = result.replace(Regex("\\s*,\\s*"), ", ")
                       .replace(Regex("\\s*;\\s*"), "; ")
                       .replace(Regex("\\s*:\\s*"), ": ")

        // 6. Clean up duplicate spaces
        result = result.replace(Regex("\\s+"), " ")

        // 7. Trim leading/trailing spaces
        return result.trim()
    }
}
