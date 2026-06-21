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

    private val HEADER_PATTERNS = listOf(
        Regex("\\bother\\s+[i1l!|][nv]gred[ie1l!|]ent[s5]?[;:\\s]*", RegexOption.IGNORE_CASE),
        Regex("\\bactive\\s+[i1l!|][nv]gred[ie1l!|]ent[s5]?[;:\\s]*", RegexOption.IGNORE_CASE),
        Regex("\\binactive\\s+[i1l!|][nv]gred[ie1l!|]ent[s5]?[;:\\s]*", RegexOption.IGNORE_CASE),
        Regex("\\b[i1l!|][nv]gred[ie1l!|]ent[s5]?[;:\\s]*", RegexOption.IGNORE_CASE),
        Regex("\\bcontains\\s+less\\s+than\\s+2%\\s+of[;:\\s]*", RegexOption.IGNORE_CASE),
        Regex("\\bcontains[;:\\s]*", RegexOption.IGNORE_CASE)
    )

    /**
     * Extracts the raw ingredient section text after a known header.
     * If no header is found, returns the entire input string.
     */
    fun extractRawSection(text: String): String {
        // 1. Try matching with fuzzy regex patterns
        for (pattern in HEADER_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val start = match.range.last + 1
                if (start < text.length) {
                    return text.substring(start).trim()
                }
            }
        }

        // 2. Fallback to exact list matching
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

    private fun shouldSplitMergedToken(token: String, vocabulary: Set<String>): Boolean {
        val clean = token.lowercase().trim()
        if (!clean.contains(' ')) return false

        // If the whole token is in vocabulary or is a known alias, we do not split it
        if (vocabulary.contains(clean) || IngredientCanonicalizer.isAlias(clean)) return false
        val canonical = IngredientCanonicalizer.canonicalize(clean)
        if (vocabulary.contains(canonical) || IngredientCanonicalizer.isAlias(canonical)) return false

        // Common phrases check
        val commonPhrases = setOf(
            "citric acid", "ascorbic acid", "malic acid", "lactic acid",
            "high fructose corn syrup", "corn syrup", "soy lecithin", "sunflower lecithin",
            "modified corn starch", "enriched wheat flour", "enriched flour", "wheat flour",
            "palm oil", "canola oil", "soybean oil", "vegetable oil", "reduced iron",
            "natural flavor", "artificial flavor", "monosodium glutamate", "sodium chloride"
        )
        if (commonPhrases.contains(clean) || commonPhrases.contains(canonical)) return false

        // Retain parenthetical groupings intact
        if (clean.contains('(') || clean.contains('[') || clean.contains('{')) return false

        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2) return false

        // Count how many words are individually known ingredients/aliases or E-numbers/additives
        var knownCount = 0
        for (word in words) {
            val wordCanonical = IngredientCanonicalizer.canonicalize(word)
            val isKnown = vocabulary.contains(word) || 
                          vocabulary.contains(wordCanonical) ||
                          IngredientCanonicalizer.isAlias(word) || 
                          IngredientCanonicalizer.isAlias(wordCanonical) ||
                          word.matches(Regex("e\\d{3,4}.*")) ||
                          word.matches(Regex("ins\\s?\\d{3,4}.*"))
            if (isKnown) {
                knownCount++
            }
        }

        // If at least two words are individually known ingredients, it's likely a merged spacing error
        return knownCount >= 2
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

        if (hasTopLevelDelimiters) {
            val finalTokens = mutableListOf<String>()
            for (token in initialTokens) {
                if (shouldSplitMergedToken(token, vocabulary)) {
                    val subTokens = token.split(Regex("\\s+")).filter { it.isNotBlank() }
                    finalTokens.addAll(subTokens)
                } else {
                    finalTokens.add(token)
                }
            }
            return finalTokens
        }

        if (vocabulary.isEmpty()) {
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
