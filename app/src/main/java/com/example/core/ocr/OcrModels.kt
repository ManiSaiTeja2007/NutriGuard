package com.example.core.ocr

import android.graphics.Bitmap
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageSource

data class OcrResult(
    val text: String,
    val processingLatencyMs: Long,
    val averageConfidence: Float?,
    val textBlockCount: Int,
    val lineCount: Int,
    val elementCount: Int,
    val source: ImageSource,
    val frame: FrameAnalysisResult,
    val segmentsProcessed: Int = 1,
    val skippedReason: String? = null
)

data class OcrSegment(
    val bitmap: Bitmap,
    val recycleAfterUse: Boolean
)

enum class MatchType {
    EXACT,
    ALIAS_MAP,
    FUZZY,
    UNKNOWN
}

data class NormalizedIngredient(
    val originalToken: String,
    val correctedToken: String,
    val canonicalToken: String,
    val confidence: Float,
    val matchType: MatchType
)

