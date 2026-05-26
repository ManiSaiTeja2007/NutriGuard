package com.example.core.intelligence.enumbers

import com.example.core.additives.ENumberEntry
import java.util.Locale

object ENumberRepairEngine {

    private val eNumberRegex = Regex("^([eE1lI])\\s*(\\d+)\\s*(.*)$")

    data class RepairResult(
        val repairedCode: String,
        val canonicalName: String,
        val category: String,
        val isRepaired: Boolean
    )

    /**
     * Attempts to repair an ambiguous or corrupt E-number notation.
     * Returns a [RepairResult] if successfully repaired, otherwise null.
     */
    fun repair(token: String): RepairResult? {
        val clean = token.lowercase(Locale.ROOT).trim().replace(" ", "")
        val match = eNumberRegex.matchEntire(clean) ?: return null

        val digits = match.groupValues[2]
        val suffix = match.groupValues[3]

        // 1. Check if the raw or simple code matches directly
        val rawCode = "e$digits$suffix"
        val directMatch = ENumberEntry.find(rawCode)
        if (directMatch != null) {
            return RepairResult(directMatch.code, directMatch.canonicalName, directMatch.category.name.lowercase(Locale.ROOT), false)
        }

        // 2. Perform suffix repairs
        val repairedSuffix = when (suffix) {
            "i", "1", "l", "i", "(i)", "(1)", "(l)", "c", "(c)" -> "(i)"
            "ii", "11", "ll", "ll", "(ii)", "(11)", "(ll)", "c(ii)" -> "(ii)"
            "o", "o" -> "0"
            "oo" -> "00"
            else -> suffix
        }

        val candidateCode1 = "e$digits$repairedSuffix"
        val resolved1 = ENumberEntry.find(candidateCode1)
        if (resolved1 != null) {
            return RepairResult(resolved1.code, resolved1.canonicalName, resolved1.category.name.lowercase(Locale.ROOT), true)
        }

        // Fallback: If digits only matching, see if we have a base E-number
        val candidateCodeBase = "e$digits"
        val resolvedBase = ENumberEntry.find(candidateCodeBase)
        if (resolvedBase != null) {
            return RepairResult(resolvedBase.code, resolvedBase.canonicalName, resolvedBase.category.name.lowercase(Locale.ROOT), true)
        }

        // For cases like E460c where we have two potential matches e460(i) and e460(ii)
        // and direct matching failed, let's check if the digits starts any known ENumberEntry
        val matches = ENumberEntry.getAll().filter { it.code.startsWith(candidateCodeBase) }
        if (matches.isNotEmpty()) {
            val best = matches.first()
            return RepairResult(best.code, best.canonicalName, best.category.name.lowercase(Locale.ROOT), true)
        }

        return null
    }
}
