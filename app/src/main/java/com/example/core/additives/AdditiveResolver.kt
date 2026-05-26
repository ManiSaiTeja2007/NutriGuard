package com.example.core.additives

import java.util.Locale

object AdditiveResolver {
    /**
     * Specialized token-aware additive resolver. Parses E-number codes and INS notation variants
     * (e.g. E621, INS621, E 621, ins 621) and standardizes roman numerals, spaces, and casing.
     * Strict mode ensures malformed codes (e.g. INS 50O(ii) with letter O) are rejected to UNKNOWN.
     */
    fun resolve(input: String): ENumberEntry? {
        val clean = input.lowercase(Locale.ROOT).trim()

        // 1. Tokenize by splitting and introducing spaces around parentheses
        val tokens = clean
            .replace("(", " ( ")
            .replace(")", " ) ")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var codeDigits: String? = null
        var suffix: String? = null

        // 2. Identify prefix and digit components
        if (tokens.isNotEmpty()) {
            val first = tokens[0]
            if (first == "ins" || first == "e") {
                if (tokens.size >= 2) {
                    val second = tokens[1]
                    if (isValidDigits(second)) {
                        codeDigits = second
                        suffix = extractSuffix(tokens, 2)
                    }
                }
            } else {
                // Check if code prefix is attached to digits directly, e.g. "ins500" or "ins500ii"
                if (first.startsWith("ins") || first.startsWith("e")) {
                    val prefixLen = if (first.startsWith("ins")) 3 else 1
                    val remainder = first.substring(prefixLen)
                    val digits = remainder.takeWhile { it.isDigit() }
                    if (digits.isNotEmpty()) {
                        codeDigits = digits
                        val trailing = remainder.substring(digits.length)
                        if (trailing.isNotEmpty()) {
                            suffix = trailing
                        } else {
                            suffix = extractSuffix(tokens, 1)
                        }
                    }
                }
            }
        }

        if (codeDigits != null) {
            val baseCode = "e$codeDigits"
            
            // Normalize roman numerals casing/formatting
            val normalizedSuffix = when (suffix?.lowercase(Locale.ROOT)?.trim()) {
                "i" -> "(i)"
                "ii" -> "(ii)"
                "iii" -> "(iii)"
                "iv" -> "(iv)"
                "v" -> "(v)"
                "(i)" -> "(i)"
                "(ii)" -> "(ii)"
                "(iii)" -> "(iii)"
                "(iv)" -> "(iv)"
                "(v)" -> "(v)"
                else -> null
            }

            val codeToFind = if (normalizedSuffix != null) {
                "$baseCode$normalizedSuffix"
            } else {
                baseCode
            }

            val hit = ENumberRepository.find(codeToFind) ?: ENumberRepository.find(baseCode)
            if (hit != null) return hit
        }

        return null
    }

    private fun isValidDigits(s: String): Boolean {
        // Strict mode: Only allow actual digits. E.g. "50o" is invalid.
        return s.isNotEmpty() && s.all { it.isDigit() }
    }

    private fun extractSuffix(tokens: List<String>, startIndex: Int): String? {
        if (startIndex >= tokens.size) return null
        val sub = tokens.subList(startIndex, tokens.size)
        val joined = sub.joinToString("").replace("(", "").replace(")", "").trim()
        return if (joined.isNotEmpty()) joined else null
    }
}
