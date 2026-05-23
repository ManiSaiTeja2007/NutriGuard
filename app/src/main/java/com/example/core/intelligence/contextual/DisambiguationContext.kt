package com.example.core.intelligence.contextual

/**
 * Lightweight context window used by [ContextualDisambiguator].
 *
 * @param precedingTokens  Up to 3 canonical tokens appearing BEFORE the ambiguous token.
 * @param followingTokens  Up to 3 canonical tokens appearing AFTER the ambiguous token.
 * @param ontologyCategories Set of resolved ontology category names for neighboring tokens.
 * @param ingredientFrequencyHint  [0.0, 1.0] frequency hint from vocabulary (higher = more common).
 *                                 Defaults to 0.5f (unknown).
 */
data class DisambiguationContext(
    val precedingTokens: List<String>,
    val followingTokens: List<String>,
    val ontologyCategories: Set<String> = emptySet(),
    val ingredientFrequencyHint: Float = 0.5f
)
