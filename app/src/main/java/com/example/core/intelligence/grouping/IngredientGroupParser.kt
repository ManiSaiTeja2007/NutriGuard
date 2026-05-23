package com.example.core.intelligence.grouping

import com.example.core.intelligence.correction.FailureType
import com.example.core.intelligence.correction.PipelineStageResult

/**
 * Deterministic parenthesis-aware ingredient group parser.
 *
 * Uses a character-level state machine (no regex) to split ingredient labels into
 * nested [IngredientGroup] structures.
 *
 * Safety limits:
 *   - max 64 tokens per call              → emits TRUNCATION_FAILURE
 *   - max nesting depth 3                  → emits NESTED_STRUCTURE_FAILURE
 *   - max phrase length 128 chars/segment  → segment is preserved verbatim
 *
 * Handles:
 *   "Enriched wheat flour (wheat flour, niacin, iron), sugar, palm oil"
 *   "Salt, acidity regulator (E330, E331), color (caramel)"
 *   "Soy lecithin (emulsifier)"  — single-child parenthetical
 *
 * Does NOT handle multi-level nesting beyond depth 3 (outer parenthetical marks failure).
 */
object IngredientGroupParser {

    private const val MAX_TOKENS = 64
    private const val MAX_DEPTH = 3

    /**
     * Entry point. Parses the raw ingredient section string into a structured list of
     * [IngredientGroup] entries. Each top-level comma-separated segment becomes one group,
     * with any parenthetical children nested inside.
     */
    fun parse(raw: String): PipelineStageResult<List<IngredientGroup>> {
        val startMs = System.currentTimeMillis()
        val trace = mutableListOf<String>()
        val failures = mutableListOf<FailureType>()
        trace.add("input: \"${raw.take(120)}\"")

        val segments = splitTopLevel(raw, trace, failures)
        trace.add("top-level segments: ${segments.size} -> $segments")

        val groups = mutableListOf<IngredientGroup>()
        var tokenCount = 0

        for (segment in segments) {
            if (tokenCount >= MAX_TOKENS) {
                failures.add(FailureType.TRUNCATION_FAILURE)
                trace.add("token limit reached at segment \"${segment.take(40)}\", stopping")
                break
            }
            val group = parseSegment(segment.trim(), depth = 0, trace = trace, failures = failures)
            groups.add(group)
            tokenCount++
        }

        val latency = System.currentTimeMillis() - startMs
        return PipelineStageResult(groups, latency, trace, failures)
    }

    /**
     * Splits the raw text at top-level commas (ignoring commas inside parentheses).
     * Uses a depth counter incremented on '(' and decremented on ')'.
     */
    private fun splitTopLevel(
        text: String,
        trace: MutableList<String>,
        failures: MutableList<FailureType>
    ): List<String> {
        val segments = mutableListOf<String>()
        val buffer = StringBuilder()
        var depth = 0

        for (ch in text) {
            when (ch) {
                '(' -> {
                    depth++
                    if (depth > MAX_DEPTH) {
                        failures.add(FailureType.NESTED_STRUCTURE_FAILURE)
                        trace.add("max depth $MAX_DEPTH exceeded — excess parenthesis treated as literal")
                    }
                    buffer.append(ch)
                }
                ')' -> {
                    if (depth > 0) depth--
                    buffer.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        val seg = buffer.toString().trim()
                        if (seg.isNotEmpty()) segments.add(seg)
                        buffer.clear()
                    } else {
                        buffer.append(ch)
                    }
                }
                else -> buffer.append(ch)
            }
        }
        val last = buffer.toString().trim()
        if (last.isNotEmpty()) segments.add(last)
        return segments
    }

    /**
     * Parses a single segment into an [IngredientGroup].
     * If the segment contains a parenthetical block, the name is the text before '('
     * and the children are parsed from the content inside '(...)'.
     */
    private fun parseSegment(
        segment: String,
        depth: Int,
        trace: MutableList<String>,
        failures: MutableList<FailureType>
    ): IngredientGroup {
        val openIdx = segment.indexOf('(')

        // No parentheses → simple leaf node
        if (openIdx == -1) {
            trace.add("leaf at depth $depth: \"$segment\"")
            return IngredientGroup(name = segment.trim(), depth = depth)
        }

        val closeIdx = segment.lastIndexOf(')')
        if (closeIdx == -1 || closeIdx < openIdx) {
            // Malformed parenthesis — emit failure, treat whole segment as leaf
            failures.add(FailureType.GROUPING_FAILURE)
            trace.add("malformed parenthesis in \"${segment.take(60)}\" — treated as leaf")
            return IngredientGroup(
                name = segment.trim(),
                depth = depth,
                failures = listOf(FailureType.GROUPING_FAILURE)
            )
        }

        val parentName = segment.substring(0, openIdx).trim()
        val innerContent = segment.substring(openIdx + 1, closeIdx).trim()
        trace.add("group at depth $depth: name=\"$parentName\", inner=\"${innerContent.take(80)}\"")

        // Check depth limit before recursing into children
        val children = if (depth + 1 > MAX_DEPTH) {
            failures.add(FailureType.NESTED_STRUCTURE_FAILURE)
            trace.add("max depth exceeded inside \"$parentName\" — children not parsed")
            emptyList()
        } else {
            parseChildren(innerContent, depth + 1, trace, failures)
        }

        return IngredientGroup(
            name = parentName,
            children = children,
            isParenthetical = false,
            depth = depth
        )
    }

    /**
     * Splits the inner content of parentheses and recursively parses each child segment.
     */
    private fun parseChildren(
        inner: String,
        depth: Int,
        trace: MutableList<String>,
        failures: MutableList<FailureType>
    ): List<IngredientGroup> {
        // Use top-level split at this nested depth — commas inside sub-parens are preserved
        val childSegments = splitTopLevel(inner, trace, failures)
        return childSegments.map { seg ->
            parseSegment(seg.trim(), depth, trace, failures)
        }
    }
}
