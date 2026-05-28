package com.example.core.pipeline.graph

import android.graphics.Rect
import com.example.core.intelligence.correction.OcrMetadata
import com.example.core.ocr.OCRBlock
import com.example.core.ocr.OCRLine
import java.util.UUID

enum class ZoneType {
    INGREDIENTS,
    ALLERGENS,
    NUTRITION,
    WARNINGS,
    STORAGE,
    MARKETING_DECORATIVE,
    UNKNOWN
}

enum class ZonePriority {
    HIGH,
    MEDIUM,
    LOW,
    IGNORE
}

data class LayoutZone(
    val rect: Rect,
    val type: ZoneType,
    val priority: ZonePriority,
    val confidence: Float
)

enum class SectionType {
    INGREDIENTS,
    ALLERGENS,
    NUTRITION,
    STORAGE,
    WARNINGS,
    MARKETING,
    MANUFACTURER,
    UNKNOWN
}

data class ClassifiedSection(
    val type: SectionType,
    val headerLine: OCRLine?,
    val bodyLines: List<OCRLine>,
    val confidence: Float,
    val classificationSource: String
)

data class SemanticRoutingContext(
    val executionId: UUID = UUID.randomUUID(),
    var imageWidth: Int = 0,
    var imageHeight: Int = 0,
    var ocrMetadata: OcrMetadata? = null,
    val detectedZones: MutableList<LayoutZone> = mutableListOf(),
    val targetedOcrBlocks: MutableList<OCRBlock> = mutableListOf(),
    val targetedOcrLines: MutableList<OCRLine> = mutableListOf(),
    val classifiedSections: MutableList<ClassifiedSection> = mutableListOf(),
    val routingDecisions: MutableList<String> = mutableListOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
)
