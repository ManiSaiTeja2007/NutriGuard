package com.example.core.ocr.reconstruction

import com.example.core.ocr.OCRLine

object IngredientRegionDetector {

    private val INGREDIENT_INDICATORS = listOf(
        "ingredient", "ingred", "gred", "contains", "contain", "composition", "zutaten"
    )

    private val IGNORE_PATTERNS = listOf(
        "distributed by", "dist. by", "manufactured by", "product of", "net wt",
        "best before", "expiry", "exp date", "store in", "keep refrigerated", "nutrition facts",
        "for feedback", "customer care", "regd. office", "batch no", "mfg. date"
    )

    /**
     * Extracts lines that represent the ingredient label section.
     *
     * @param lines The reconstructed OCR lines.
     * @param vocabulary A set of known ingredient names to measure token density.
     */
    fun detectRegion(lines: List<OCRLine>, vocabulary: Set<String>): List<OCRLine> {
        if (lines.isEmpty()) return emptyList()

        // 1. Locate the starting line using indicator keywords
        var startIndex = -1
        for (i in lines.indices) {
            val text = lines[i].words.joinToString(" ") { it.text }.lowercase()
            if (INGREDIENT_INDICATORS.any { text.contains(it) }) {
                startIndex = i
                break
            }
        }

        // 2. Fallback: if no keyword matched, find the line with the highest density of ingredient vocabulary
        if (startIndex == -1) {
            var maxScore = 0f
            for (i in lines.indices) {
                val text = lines[i].words.joinToString(" ") { it.text }.lowercase()
                val tokens = text.split(Regex("[^a-zA-Z]")).filter { it.length > 2 }
                val matchingTokens = tokens.count { vocabulary.contains(it) }
                val score = if (tokens.isNotEmpty()) matchingTokens.toFloat() / tokens.size else 0f
                if (score > 0.4f && score > maxScore) {
                    maxScore = score
                    startIndex = i
                }
            }
        }

        val actualStart = if (startIndex != -1) startIndex else 0

        // 3. Collect subsequent lines until a stop pattern or layout gap is encountered
        val resultLines = mutableListOf<OCRLine>()
        for (i in actualStart until lines.size) {
            val lineText = lines[i].words.joinToString(" ") { it.text }.lowercase()
            
            // Stop if an ignore pattern is matched after we already have some ingredient text
            if (IGNORE_PATTERNS.any { lineText.contains(it) } && resultLines.isNotEmpty()) {
                break
            }
            
            // Stop if the vertical distance to the previous line is too large (likely different section)
            if (resultLines.isNotEmpty()) {
                val prevLine = resultLines.last()
                val gap = lines[i].bounds.top - prevLine.bounds.bottom
                val avgHeight = (lines[i].bounds.height() + prevLine.bounds.height()) / 2f
                if (gap > avgHeight * 3.0f) {
                    break
                }
            }

            resultLines.add(lines[i])
        }

        return resultLines
    }
}
