package com.example.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageSource

data class OCRWord(
    val text: String,
    val confidence: Float,
    val bounds: Rect
)

data class OCRLine(
    val words: List<OCRWord>,
    val bounds: Rect,
    val confidence: Float
)

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
    val skippedReason: String? = null,
    
    // Structured properties for layout-aware reconstruction & debugging
    val ocrWords: List<OCRWord> = emptyList(),
    val reconstructedLines: List<OCRLine> = emptyList(),
    val detectedParagraphs: List<OCRLine> = emptyList(),
    val passesRun: List<String> = emptyList(),
    val failures: List<com.example.core.intelligence.correction.FailureType> = emptyList(),
    
    // Adaptive OCR routing diagnostics
    val blurScore: Float = 0f,
    val contrastScore: Float = 0f,
    val brightnessScore: Float = 0f,
    val complexityRating: String = "LOW",
    val routedStrategy: String = "STANDARD",
    val tileRegions: List<Rect> = emptyList()
)

data class OcrSegment(
    val bitmap: Bitmap,
    val recycleAfterUse: Boolean
)
