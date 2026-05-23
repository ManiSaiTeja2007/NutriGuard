package com.example.core.ingredient

object IngredientExtractor {

    private val HEADERS = listOf(
        "ingredients:",
        "ingredients",
        "contains:",
        "contains less than 2% of:",
        "contains less than 2% of",
        "other ingredients:",
        "other ingredients",
        "active ingredients:",
        "active ingredients",
        "inactive ingredients:",
        "inactive ingredients"
    )

    /**
     * Extracts the raw ingredient section text after a known header.
     * If no header is found, returns the entire input string.
     */
    fun extractRawSection(text: String): String {
        val lowerText = text.lowercase().trim()
        for (header in HEADERS) {
            val index = lowerText.indexOf(header)
            if (index != -1) {
                val start = index + header.length
                if (start < text.length) {
                    return text.substring(start).trim()
                }
            }
        }
        return text
    }

    /**
     * Splits the raw section into tokens using parenthesis-aware parsing.
     * Splits by commas and semicolons at the top level.
     * If no commas/semicolons are found, falls back to space-based splitting (spacing recovery).
     * Adjacent words are merged if they form a known vocabulary entry or canonical alias.
     */
    fun tokenize(sectionText: String, vocabulary: Set<String> = emptySet()): List<String> {
        val trimmedSection = sectionText.trim()
        if (trimmedSection.isEmpty()) return emptyList()

        // Check if there are any top-level commas or semicolons
        var hasTopLevelDelimiters = false
        var depth = 0
        for (char in trimmedSection) {
            when (char) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth > 0) depth--
                ',', ';' -> {
                    if (depth == 0) {
                        hasTopLevelDelimiters = true
                        break
                    }
                }
            }
        }

        val initialTokens = if (hasTopLevelDelimiters) {
            splitByDelimiter(trimmedSection, listOf(',', ';'))
        } else {
            splitByDelimiter(trimmedSection, listOf(' '))
        }

        if (hasTopLevelDelimiters || vocabulary.isEmpty()) {
            return initialTokens
        }

        // Spacing recovery: merge adjacent tokens if they form a known vocabulary entry or canonical alias
        val mergedTokens = mutableListOf<String>()
        var i = 0
        while (i < initialTokens.size) {
            var merged = false
            // Try to match multi-word entries, from longest (4 words) down to 2 words
            for (len in 4 downTo 2) {
                if (i + len <= initialTokens.size) {
                    val candidate = initialTokens.subList(i, i + len).joinToString(" ")
                    val cleanCandidate = candidate.lowercase().trim()
                    val canonical = IngredientCanonicalizer.canonicalize(cleanCandidate)
                    
                    val isInVocab = vocabulary.contains(cleanCandidate) || vocabulary.contains(canonical)
                    val isKnownAlias = IngredientCanonicalizer.isAlias(cleanCandidate) || IngredientCanonicalizer.isAlias(canonical)

                    if (isInVocab || isKnownAlias) {
                        mergedTokens.add(candidate)
                        i += len
                        merged = true
                        break
                    }
                }
            }
            if (!merged) {
                mergedTokens.add(initialTokens[i])
                i++
            }
        }
        return mergedTokens
    }

    private fun splitByDelimiter(text: String, delimiters: List<Char>): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var bracketDepth = 0

        for (char in text) {
            when (char) {
                '(', '{' -> {
                    parenDepth++
                    current.append(char)
                }
                ')', '}' -> {
                    if (parenDepth > 0) parenDepth--
                    current.append(char)
                }
                '[' -> {
                    bracketDepth++
                    current.append(char)
                }
                ']' -> {
                    if (bracketDepth > 0) bracketDepth--
                    current.append(char)
                }
                in delimiters -> {
                    if (parenDepth == 0 && bracketDepth == 0) {
                        val token = cleanToken(current.toString())
                        if (token.isNotEmpty()) {
                            tokens.add(token)
                        }
                        current.setLength(0)
                    } else {
                        current.append(char)
                    }
                }
                else -> {
                    current.append(char)
                }
            }
        }

        val lastToken = cleanToken(current.toString())
        if (lastToken.isNotEmpty()) {
            tokens.add(lastToken)
        }

        return tokens
    }

    private fun cleanToken(token: String): String {
        var clean = token.trim()

        // Strip leading/trailing punctuation
        while (clean.isNotEmpty() && (clean.startsWith(",") || clean.startsWith(".") || clean.startsWith(":") || clean.startsWith(";"))) {
            clean = clean.substring(1).trim()
        }
        while (clean.isNotEmpty() && (clean.endsWith(",") || clean.endsWith(".") || clean.endsWith(":") || clean.endsWith(";"))) {
            clean = clean.substring(0, clean.length - 1).trim()
        }

        // Collapse duplicate internal spaces
        clean = clean.replace(Regex("\\s+"), " ")

        return clean.trim()
    }
}
