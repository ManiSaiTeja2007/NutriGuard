package com.example.core.intelligence.parsing

import java.util.Locale

/**
 * Deterministic phrase-level normalizer that runs before token correction.
 *
 * Operations (in order):
 *  1. Lowercase + trim
 *  2. Collapse hyphenated compounds:    "mono-sodium"       → "monosodium"
 *  3. Strip category labels in parens:  "salt (preservative)"→ "salt"
 *     — but ONLY when the parent name is non-empty and children will be handled by the group parser
 *  4. Repair known split-compounds:     "potas sium"        → "potassium"
 *  5. Collapse multiple whitespace to single space
 *
 * This stage is deterministic and side-effect free.
 */
object PhraseNormalizer {

    /** Known split-compound repairs (OCR splits a single word into parts) */
    private val splitCompoundRepairs = mapOf(
        "potas sium" to "potassium",
        "potas sorbate" to "potassium sorbate",
        "cal cium" to "calcium",
        "mag nesium" to "magnesium",
        "phos phate" to "phosphate",
        "fruc tose" to "fructose",
        "mal tose" to "maltose",
        "leci thin" to "lecithin",
        "glyc erol" to "glycerol",
        "cho lesterol" to "cholesterol",
        "nia cin" to "niacin",
        "ribo flavin" to "riboflavin",
        "thia mine" to "thiamine",
        "as corbic" to "ascorbic",
        "glut amate" to "glutamate",
        "ben zoate" to "benzoate",
        "sor bate" to "sorbate",
        "pro pionate" to "propionate",
        "car mine" to "carmine",
        "an natto" to "annatto",
        "xan than" to "xanthan",
        "car rageenan" to "carrageenan"
    )

    /**
     * Normalizes a raw phrase for phrase-level correction.
     * Returns the normalized string and a list of debug trace steps.
     */
    fun normalize(raw: String): Pair<String, List<String>> {
        val trace = mutableListOf<String>()
        var result = raw.lowercase(Locale.ROOT).trim()
        trace.add("input: \"$result\"")

        // 1. Collapse hyphenated compounds
        val dehyphenated = result.replace(Regex("-(?=[a-z])"), "")
        if (dehyphenated != result) {
            trace.add("dehyphenated: \"$dehyphenated\"")
            result = dehyphenated
        }

        // 2. Strip parenthetical category labels (single-word only — e.g. "(preservative)")
        val stripped = result.replace(Regex("\\s*\\(\\s*[a-z]+\\s*\\)\\s*$"), "").trim()
        if (stripped != result) {
            trace.add("stripped parenthetical label: \"$stripped\"")
            result = stripped
        }

        // 3. Repair split-compound OCR fragments
        val repaired = splitCompoundRepairs[result]
        if (repaired != null) {
            trace.add("split-compound repair: \"$repaired\"")
            result = repaired
        }

        // 4. Collapse multiple spaces
        val collapsed = result.replace(Regex("\\s+"), " ").trim()
        if (collapsed != result) {
            trace.add("collapsed whitespace: \"$collapsed\"")
            result = collapsed
        }

        trace.add("phrase-normalized: \"$result\"")
        return Pair(result, trace)
    }
}
