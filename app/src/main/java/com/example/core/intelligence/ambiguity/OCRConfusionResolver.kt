package com.example.core.intelligence.ambiguity

import java.util.Locale

enum class PositionType {
    ENUMBER_SUFFIX,
    ADDITIVE_NOTATION,
    PARENTHETICAL_ADDITIVE,
    GLOBAL
}

data class CharacterConfusionRule(
    val source: Char,
    val target: Char,
    val allowedPositions: Set<PositionType>,
    val confidencePenalty: Float
)

data class ConfusionCandidate(
    val text: String,
    val penalty: Float
)

object OCRConfusionResolver {

    private val rules: List<CharacterConfusionRule> = listOf(
        // 'c' can be confused with parenthesis
        CharacterConfusionRule('c', '(', setOf(PositionType.PARENTHETICAL_ADDITIVE, PositionType.ENUMBER_SUFFIX), 0.05f),
        CharacterConfusionRule('c', ')', setOf(PositionType.PARENTHETICAL_ADDITIVE, PositionType.ENUMBER_SUFFIX), 0.05f),
        // '0' vs 'o' vs 'O'
        CharacterConfusionRule('0', 'o', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('0', 'o', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('o', '0', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('o', 'o', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.0f),
        // '1' vs 'l' vs 'i'
        CharacterConfusionRule('1', 'l', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('1', 'i', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('l', '1', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('l', 'i', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('i', '1', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('i', 'l', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        // '5' vs 's'
        CharacterConfusionRule('5', 's', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        CharacterConfusionRule('s', '5', setOf(PositionType.ADDITIVE_NOTATION, PositionType.GLOBAL), 0.05f),
        // Parentheses
        CharacterConfusionRule('(', 'c', setOf(PositionType.PARENTHETICAL_ADDITIVE), 0.08f),
        CharacterConfusionRule(')', 'c', setOf(PositionType.PARENTHETICAL_ADDITIVE), 0.08f)
    )

    /**
     * Identifies the position type for index [index] in string [s].
     */
    fun getPositionType(s: String, index: Int): PositionType {
        val char = s[index]
        val isAdditive = s.startsWith("e", ignoreCase = true) && s.length > 1 && s[1].isDigit()
        
        return when {
            char == '(' || char == ')' -> PositionType.PARENTHETICAL_ADDITIVE
            isAdditive && index == s.length - 1 && char.isLetter() -> PositionType.ENUMBER_SUFFIX
            isAdditive && char.isDigit() -> PositionType.ADDITIVE_NOTATION
            s.contains("(") && s.contains(")") && index > s.indexOf("(") && index < s.indexOf(")") -> PositionType.PARENTHETICAL_ADDITIVE
            else -> PositionType.GLOBAL
        }
    }

    /**
     * Deterministically generates correction candidates for a token based on position-aware rules.
     * Enforces strict safety caps: max 2 substitutions and max 15 candidates.
     */
    fun resolveAmbiguity(token: String): List<ConfusionCandidate> {
        val clean = token.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty()) return emptyList()

        val matchableSites = mutableListOf<Triple<Int, Char, CharacterConfusionRule>>()
        for (i in clean.indices) {
            val char = clean[i]
            val posType = getPositionType(clean, i)
            val applicableRules = rules.filter { it.source == char && posType in it.allowedPositions }
            applicableRules.forEach { rule ->
                matchableSites.add(Triple(i, char, rule))
            }
        }

        // Cap site count to prevent combinatorial explosion
        val cappedSites = matchableSites.take(8)
        val generated = mutableListOf<ConfusionCandidate>()
        
        // Base case: 0 substitutions
        generated.add(ConfusionCandidate(clean, 0.0f))

        // 1-substitution candidates
        for (site in cappedSites) {
            val index = site.first
            val rule = site.third
            val newText = clean.replaceRange(index, index + 1, rule.target.toString())
            generated.add(ConfusionCandidate(newText, rule.confidencePenalty))
        }

        // 2-substitutions candidates (combinations of 2 different indices)
        for (i in cappedSites.indices) {
            for (j in i + 1 until cappedSites.size) {
                val site1 = cappedSites[i]
                val site2 = cappedSites[j]
                
                // Ensure they target different positions
                if (site1.first == site2.first) continue
                
                val index1 = site1.first
                val rule1 = site1.third
                val index2 = site2.first
                val rule2 = site2.third

                val (firstIdx, firstRule, secondIdx, secondRule) = if (index1 < index2) {
                    listOf(index1, rule1, index2, rule2)
                } else {
                    listOf(index2, rule2, index1, rule1)
                }

                // Apply replacements sequentially (starting from the rightmost to preserve indices)
                val tempText = clean.replaceRange(secondIdx as Int, (secondIdx as Int) + 1, (secondRule as CharacterConfusionRule).target.toString())
                val finalText = tempText.replaceRange(firstIdx as Int, (firstIdx as Int) + 1, (firstRule as CharacterConfusionRule).target.toString())

                generated.add(ConfusionCandidate(finalText, (firstRule as CharacterConfusionRule).confidencePenalty + (secondRule as CharacterConfusionRule).confidencePenalty))
            }
        }

        // Sort by penalty ascending, then filter duplicate texts keeping the lowest penalty
        return generated
            .groupBy { it.text }
            .map { (_, list) -> list.minByOrNull { it.penalty }!! }
            .sortedBy { it.penalty }
            .take(15) // strict cap of 15 candidates
    }
}
