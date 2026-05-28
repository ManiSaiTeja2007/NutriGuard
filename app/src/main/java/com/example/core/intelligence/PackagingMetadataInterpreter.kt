package com.example.core.intelligence

import com.example.core.ocr.OCRLine
import java.util.Locale

data class MetadataInterpretation(
    val originalText: String,
    val manufacturer: String?,
    val distributor: String?,
    val netWeight: String?
)

object PackagingMetadataInterpreter {
    fun interpret(lines: List<OCRLine>): MetadataInterpretation {
        val fullText = lines.flatMap { it.words }.joinToString(" ") { it.text }
        val lowercase = fullText.lowercase(Locale.ROOT)

        var manufacturer: String? = null
        var distributor: String? = null
        var netWeight: String? = null

        val mfgRegex = Regex("(manufactured by|mfd by|made by|mfg)\\s*:?\\s*([a-zA-Z0-9\\s\\.,]+)")
        val distRegex = Regex("(distributed by|dist. by|imported by|distrib)\\s*:?\\s*([a-zA-Z0-9\\s\\.,]+)")
        val wtRegex = Regex("(net wt|net weight|weight|netto|mass)\\s*:?\\s*(\\d+\\s*(g|oz|kg|lbs|ml))")

        mfgRegex.find(lowercase)?.let {
            manufacturer = it.groupValues[2].trim()
        }

        distRegex.find(lowercase)?.let {
            distributor = it.groupValues[2].trim()
        }

        wtRegex.find(lowercase)?.let {
            netWeight = it.groupValues[2].trim()
        }

        return MetadataInterpretation(
            originalText = fullText,
            manufacturer = manufacturer,
            distributor = distributor,
            netWeight = netWeight
        )
    }
}
