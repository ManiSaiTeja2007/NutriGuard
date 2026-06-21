package com.example.core.pipeline.graph

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.ocr.OCRBlock
import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord
import com.example.core.ocr.OcrResult
import com.example.core.pipeline.OCRPipeline
import com.example.core.imaging.ImageFrame
import com.example.core.frame.FrameAnalysisResult
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.correction.FailureType

class TargetedOcrCoordinator(
    private val ocrPipeline: OCRPipeline
) : ExecutionStage<Bitmap, OcrResult> {
    override val stageName: String = "targeted_ocr"

    override suspend fun execute(
        input: Bitmap,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<OcrResult> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        // 1. Filter zones based on priority (exclude IGNORE, prioritize HIGH and MEDIUM)
        val sortedZones = context.detectedZones
            .filter { it.priority != ZonePriority.IGNORE }
            .sortedBy { if (it.priority == ZonePriority.HIGH) 0 else 1 }

        // Adaptive early exit: If no high/medium priority zones found, skip or exit early
        if (sortedZones.isEmpty()) {
            val latency = android.os.SystemClock.elapsedRealtime() - started
            val emptyResult = OcrResult(
                text = "",
                processingLatencyMs = latency,
                averageConfidence = 1.0f,
                textBlockCount = 0,
                lineCount = 0,
                elementCount = 0,
                source = ImageSource.CAMERA_X,
                frame = FrameAnalysisResult(input.width, input.height, 0, System.nanoTime(), ImageSource.CAMERA_X, true, latency)
            )
            return ExecutionStageResult(
                executionId = context.executionId,
                stageName = stageName,
                output = emptyResult,
                latencyMs = latency,
                replayArtifacts = mapOf("skipped" to "No prioritized zones detected"),
                failures = listOf("No prioritized zones found; OCR execution skipped.")
            )
        }

        val allWords = mutableListOf<OCRWord>()
        val allBlocks = mutableListOf<OCRBlock>()
        val allLines = mutableListOf<OCRLine>()
        val passesRun = mutableListOf<String>()

        var totalOcrLatency = 0L

        // ISSUE-009 FIX: Cap zone processing to MAX_ZONES_PER_SCAN.
        // With unlimited zones and serial ML Kit execution, scan time grows linearly:
        // 4 zones × 5-8s = 20-34s (observed ~34s). Capping at 3 zones bounds worst-case to ~24s.
        // HIGH priority zones are already first in sortedZones (sorted above).
        val MAX_ZONES_PER_SCAN = 3
        val cappedZones = sortedZones.take(MAX_ZONES_PER_SCAN)

        // Budgeting/Parallel execution strategy
        for (zone in cappedZones) {

            val rect = Rect(
                zone.rect.left.coerceIn(0, input.width),
                zone.rect.top.coerceIn(0, input.height),
                zone.rect.right.coerceIn(0, input.width),
                zone.rect.bottom.coerceIn(0, input.height)
            )

            if (rect.width() < 32 || rect.height() < 32) continue

            // Crop the zone
            val croppedBitmap = try {
                Bitmap.createBitmap(input, rect.left, rect.top, rect.width(), rect.height())
            } catch (e: Exception) {
                failures.add("Failed to crop zone ${zone.type}: ${e.message}")
                continue
            }

            val zoneStart = android.os.SystemClock.elapsedRealtime()
            val ocrResult = try {
                ocrPipeline.runDirectOcr(croppedBitmap)
            } catch (e: Exception) {
                failures.add("OCR execution failed on zone ${zone.type}: ${e.message}")
                null
            } finally {
                croppedBitmap.recycle()
            }

            totalOcrLatency += (android.os.SystemClock.elapsedRealtime() - zoneStart)

            if (ocrResult != null) {
                // Offset words, lines, and blocks back to global coordinates
                ocrResult.ocrWords.forEach { word ->
                    val globalBounds = Rect(
                        word.bounds.left + rect.left,
                        word.bounds.top + rect.top,
                        word.bounds.right + rect.left,
                        word.bounds.bottom + rect.top
                    )
                    allWords.add(OCRWord(word.text, word.confidence, globalBounds))
                }

                ocrResult.ocrBlocks.forEach { block ->
                    val globalBounds = Rect(
                        block.bounds.left + rect.left,
                        block.bounds.top + rect.top,
                        block.bounds.right + rect.left,
                        block.bounds.bottom + rect.top
                    )
                    val mappedLines = block.lines.map { line ->
                        val globalLineBounds = Rect(
                            line.bounds.left + rect.left,
                            line.bounds.top + rect.top,
                            line.bounds.right + rect.left,
                            line.bounds.bottom + rect.top
                        )
                        val mappedWords = line.words.map { word ->
                            val gwB = Rect(
                                word.bounds.left + rect.left,
                                word.bounds.top + rect.top,
                                word.bounds.right + rect.left,
                                word.bounds.bottom + rect.top
                            )
                            OCRWord(word.text, word.confidence, gwB)
                        }
                        OCRLine(mappedWords, globalLineBounds, line.confidence)
                    }
                    allBlocks.add(OCRBlock(mappedLines, globalBounds, block.confidence))
                }

                ocrResult.reconstructedLines.forEach { line ->
                    val globalBounds = Rect(
                        line.bounds.left + rect.left,
                        line.bounds.top + rect.top,
                        line.bounds.right + rect.left,
                        line.bounds.bottom + rect.top
                    )
                    val mappedWords = line.words.map { word ->
                        val gwB = Rect(
                            word.bounds.left + rect.left,
                            word.bounds.top + rect.top,
                            word.bounds.right + rect.left,
                            word.bounds.bottom + rect.top
                        )
                        OCRWord(word.text, word.confidence, gwB)
                    }
                    allLines.add(OCRLine(mappedWords, globalBounds, line.confidence))
                }

                passesRun.addAll(ocrResult.passesRun)
            }
        }

        val deduplicatedWords = deduplicateWords(allWords)

        // Save OCR results to context
        context.targetedOcrBlocks.addAll(allBlocks)
        context.targetedOcrLines.addAll(allLines)

        val finalOcrText = allLines.joinToString(separator = "\n") { line ->
            line.words.joinToString(separator = " ") { it.text }
        }

        val confidenceValues = deduplicatedWords.map { it.confidence }
        val averageConfidence = if (confidenceValues.isNotEmpty()) confidenceValues.average().toFloat() else 0.8f

        val finalOcrResult = OcrResult(
            text = finalOcrText,
            processingLatencyMs = totalOcrLatency,
            averageConfidence = averageConfidence,
            textBlockCount = allBlocks.size,
            lineCount = allLines.size,
            elementCount = deduplicatedWords.size,
            source = ImageSource.CAMERA_X,
            frame = FrameAnalysisResult(input.width, input.height, 0, System.nanoTime(), ImageSource.CAMERA_X, true, totalOcrLatency),
            segmentsProcessed = sortedZones.size,
            ocrBlocks = allBlocks,
            ocrWords = deduplicatedWords,
            reconstructedLines = allLines,
            detectedParagraphs = allLines,
            passesRun = passesRun.distinct(),
            failures = failures.map { FailureType.OCR_PIPELINE_ROUTING_FAILURE }
        )

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = finalOcrResult,
            latencyMs = latency,
            replayArtifacts = mapOf(
                "ocrText" to finalOcrText,
                "latencyMs" to latency,
                "averageConfidence" to averageConfidence,
                "linesCount" to allLines.size,
                "blocksCount" to allBlocks.size
            ),
            failures = failures
        )
    }

    private fun deduplicateWords(words: List<OCRWord>): List<OCRWord> {
        val merged = mutableListOf<OCRWord>()
        val sorted = words.sortedByDescending { it.confidence }

        for (word in sorted) {
            var hasOverlap = false
            for (existing in merged) {
                val iou = calculateIoU(word.bounds, existing.bounds)
                if (iou > 0.4f) {
                    hasOverlap = true
                    break
                }
            }
            if (!hasOverlap) {
                merged.add(word)
            }
        }
        return merged
    }

    private fun calculateIoU(rectA: Rect, rectB: Rect): Float {
        val left = maxOf(rectA.left, rectB.left)
        val top = maxOf(rectA.top, rectB.top)
        val right = minOf(rectA.right, rectB.right)
        val bottom = minOf(rectA.bottom, rectB.bottom)

        if (left >= right || top >= bottom) return 0f

        val intersectionArea = (right - left) * (bottom - top)
        val areaA = rectA.width() * rectA.height()
        val areaB = rectB.width() * rectB.height()
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea > 0) intersectionArea.toFloat() / unionArea else 0f
    }
}
