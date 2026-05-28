package com.example.core.intelligence

import com.example.core.ocr.OCRLine
import java.util.Locale

data class StorageInterpretation(
    val originalText: String,
    val instructions: List<String>,
    val isPerishable: Boolean
)

object StorageInstructionInterpreter {
    private val STORAGE_INDICATORS = listOf(
        "keep in a cool", "store in a cool", "refrigerate", "keep refrigerated",
        "do not freeze", "freeze", "once opened", "keep away from", "dry place"
    )

    fun interpret(lines: List<OCRLine>): StorageInterpretation {
        val fullText = lines.flatMap { it.words }.joinToString(" ") { it.text }
        val lowercase = fullText.lowercase(Locale.ROOT)

        val foundInstructions = mutableListOf<String>()
        var isPerishable = false

        lines.forEach { line ->
            val text = line.words.joinToString(" ") { it.text }
            val lowerText = text.lowercase(Locale.ROOT)
            if (STORAGE_INDICATORS.any { lowerText.contains(it) }) {
                foundInstructions.add(text)
            }
        }

        if (lowercase.contains("refrigerate") || lowercase.contains("keep refrigerated") || lowercase.contains("perishable")) {
            isPerishable = true
        }

        return StorageInterpretation(
            originalText = fullText,
            instructions = foundInstructions,
            isPerishable = isPerishable
        )
    }
}
