package com.example.core.utils.text

import kotlin.math.min

object Levenshtein {

    /**
     * Calculates the edit distance between two strings using a space-optimized DP approach.
     * Running time: O(N * M)
     * Space complexity: O(min(N, M))
     */
    fun distance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0) return len2
        if (len2 == 0) return len1

        // Use the shorter string for DP array size optimization
        val str1 = if (len1 >= len2) s1 else s2
        val str2 = if (len1 >= len2) s2 else s1

        val dp = IntArray(str2.length + 1) { it }
        for (i in 1..str1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..str2.length) {
                val temp = dp[j]
                if (str1[i - 1] == str2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + 1)
                }
                prev = temp
            }
        }
        return dp[str2.length]
    }
}
