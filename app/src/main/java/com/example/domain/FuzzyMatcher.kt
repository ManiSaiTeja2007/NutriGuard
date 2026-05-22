package com.example.domain

object FuzzyMatcher {
    /**
     * Calculates the Levenshtein distance between two strings to support fuzzy matching.
     */
    fun calculateDistance(s1: String, s2: String): Int {
        val str1 = s1.lowercase().trim()
        val str2 = s2.lowercase().trim()
        val len1 = str1.length
        val len2 = str2.length

        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // insertion
                    prev[j] + 1,          // deletion
                    prev[j - 1] + cost    // substitution
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }
}
