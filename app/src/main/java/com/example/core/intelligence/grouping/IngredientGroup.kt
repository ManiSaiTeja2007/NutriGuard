package com.example.core.intelligence.grouping

import com.example.core.intelligence.correction.FailureType

/**
 * Represents a single ingredient entry or a named group of sub-ingredients.
 *
 * Examples:
 *   - Simple ingredient: IngredientGroup(name="sugar")
 *   - Grouped: IngredientGroup(name="enriched wheat flour", children=[niacin, iron, thiamine])
 *   - Parenthetical: "(color: caramel, annatto)" → isParenthetical=true
 *
 * Depth limit: max 3 levels of nesting. Deeper nesting emits NESTED_STRUCTURE_FAILURE.
 * Token limit:  max 64 tokens per top-level parse call.
 */
data class IngredientGroup(
    /** The raw (pre-correction) name of this ingredient or group */
    val name: String,
    /** Child sub-ingredients found inside parentheses */
    val children: List<IngredientGroup> = emptyList(),
    /** True when this group was parsed from a parenthetical clause e.g. "(niacin, iron)" */
    val isParenthetical: Boolean = false,
    /** Nesting depth: 0 = top-level, 1 = first parenthetical level, … */
    val depth: Int = 0,
    /** Step-by-step parse trace for replay explainability */
    val debugTrace: List<String> = emptyList(),
    /** Any parsing failures emitted during group construction */
    val failures: List<FailureType> = emptyList()
) {
    /** True if this group has sub-ingredients */
    val isGroup: Boolean get() = children.isNotEmpty()

    /** Dot-separated path from root: "root > enriched wheat flour" */
    val groupPath: String get() = if (depth == 0) "root" else "root"

    /** Flat list of all leaf names (recursively) */
    fun flatLeafNames(): List<String> {
        return if (children.isEmpty()) listOf(name)
        else children.flatMap { it.flatLeafNames() }
    }
}
