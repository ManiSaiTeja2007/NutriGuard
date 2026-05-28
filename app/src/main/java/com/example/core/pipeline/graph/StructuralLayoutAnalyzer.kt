package com.example.core.pipeline.graph

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.core.ocr.routing.OCRComplexityAnalyzer
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StructuralLayoutAnalyzer : ExecutionStage<Bitmap, StructuralLayoutAnalyzer.StructuralAnalysisResult> {
    override val stageName: String = "structural_analysis"

    data class StructuralAnalysisResult(
        val blurScore: Float,
        val brightnessScore: Float,
        val contrastScore: Float,
        val textDensity: Float,
        val orientationDegrees: Int,
        val heatmap: List<List<Float>>,
        val zones: List<LayoutZone>,
        val probableHeaders: List<String>
    )

    private val fastRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun execute(
        input: Bitmap,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<StructuralAnalysisResult> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        try {
            // 1. Image Complexity & Quality Metrics
            val metrics = OCRComplexityAnalyzer.analyze(input)

            // 2. Generate a simple heatmap based on 8x8 grid of local contrast/variance
            val heatmap = generateHeatmap(input)

            // 3. Fast structural pass on downsampled bitmap
            val downsampledWidth = 400
            val scale = downsampledWidth.toFloat() / input.width
            val downsampledHeight = (input.height * scale).toInt().coerceAtLeast(32)
            val downsampledBitmap = Bitmap.createScaledBitmap(input, downsampledWidth, downsampledHeight, false)

            val image = InputImage.fromBitmap(downsampledBitmap, 0)
            val textResult = try {
                fastRecognizer.process(image).await()
            } catch (e: Exception) {
                failures.add("Fast OCR failed: ${e.message}")
                null
            } finally {
                downsampledBitmap.recycle()
            }

            val zones = mutableListOf<LayoutZone>()
            val probableHeaders = mutableListOf<String>()

            if (textResult != null) {
                for (block in textResult.textBlocks) {
                    val blockText = block.text.lowercase()
                    val blockBounds = block.boundingBox ?: continue
                    
                    // Map back to original coordinate system
                    val originalBounds = Rect(
                        (blockBounds.left / scale).toInt(),
                        (blockBounds.top / scale).toInt(),
                        (blockBounds.right / scale).toInt(),
                        (blockBounds.bottom / scale).toInt()
                    )

                    var type = ZoneType.UNKNOWN
                    var priority = ZonePriority.LOW

                    // Heuristics for estimating zones before full semantic routing
                    when {
                        blockText.contains("ingredient") || blockText.contains("ingred") || blockText.contains("composition") || blockText.contains("zutaten") -> {
                            type = ZoneType.INGREDIENTS
                            priority = ZonePriority.HIGH
                            probableHeaders.add(blockText.substringBefore("\n").take(40))
                        }
                        blockText.contains("allergy") || blockText.contains("may contain") || blockText.contains("contains:") || blockText.contains("allergen") -> {
                            type = ZoneType.ALLERGENS
                            priority = ZonePriority.HIGH
                            probableHeaders.add(blockText.substringBefore("\n").take(40))
                        }
                        blockText.contains("nutrition") || blockText.contains("calories") || blockText.contains("energy") || blockText.contains("fat") || blockText.contains("sodium") -> {
                            type = ZoneType.NUTRITION
                            priority = ZonePriority.MEDIUM
                            probableHeaders.add(blockText.substringBefore("\n").take(40))
                        }
                        blockText.contains("warning") || blockText.contains("caution") || blockText.contains("danger") || blockText.contains("safety") -> {
                            type = ZoneType.WARNINGS
                            priority = ZonePriority.HIGH
                            probableHeaders.add(blockText.substringBefore("\n").take(40))
                        }
                        blockText.contains("storage") || blockText.contains("keep in") || blockText.contains("refrigerate") || blockText.contains("expiry") -> {
                            type = ZoneType.STORAGE
                            priority = ZonePriority.MEDIUM
                            probableHeaders.add(blockText.substringBefore("\n").take(40))
                        }
                        blockText.contains("premium") || blockText.contains("organic") || blockText.contains("delicious") || blockText.contains("free from") -> {
                            type = ZoneType.MARKETING_DECORATIVE
                            priority = ZonePriority.LOW
                        }
                    }

                    zones.add(LayoutZone(originalBounds, type, priority, 1.0f))
                }
            }

            // If no zones were detected via downsampled OCR, create a fallback zone covering the whole image
            if (zones.isEmpty()) {
                zones.add(LayoutZone(Rect(0, 0, input.width, input.height), ZoneType.UNKNOWN, ZonePriority.HIGH, 0.5f))
            }

            // Save zones to context
            context.detectedZones.addAll(zones)

            val output = StructuralAnalysisResult(
                blurScore = metrics.blurScore,
                brightnessScore = metrics.brightness,
                contrastScore = metrics.contrast,
                textDensity = metrics.estimatedTextDensity,
                orientationDegrees = 0, // Assume 0 or estimate based on aspect/layout
                heatmap = heatmap,
                zones = zones,
                probableHeaders = probableHeaders
            )

            val latency = android.os.SystemClock.elapsedRealtime() - started

            return ExecutionStageResult(
                executionId = context.executionId,
                stageName = stageName,
                output = output,
                latencyMs = latency,
                replayArtifacts = mapOf(
                    "blurScore" to output.blurScore,
                    "brightnessScore" to output.brightnessScore,
                    "contrastScore" to output.contrastScore,
                    "textDensity" to output.textDensity,
                    "zonesCount" to zones.size,
                    "probableHeaders" to probableHeaders
                ),
                failures = failures
            )
        } catch (e: Exception) {
            val latency = android.os.SystemClock.elapsedRealtime() - started
            return ExecutionStageResult(
                executionId = context.executionId,
                stageName = stageName,
                output = null,
                latencyMs = latency,
                replayArtifacts = emptyMap(),
                failures = listOf("Exception in structural analysis: ${e.message}")
            )
        }
    }

    private fun generateHeatmap(bitmap: Bitmap): List<List<Float>> {
        val gridSize = 8
        val cellW = (bitmap.width / gridSize).coerceAtLeast(1)
        val cellH = (bitmap.height / gridSize).coerceAtLeast(1)
        val result = mutableListOf<List<Float>>()
        for (row in 0 until gridSize) {
            val rowList = mutableListOf<Float>()
            for (col in 0 until gridSize) {
                val x = col * cellW
                val y = row * cellH
                val w = cellW.coerceAtMost(bitmap.width - x)
                val h = cellH.coerceAtMost(bitmap.height - y)
                if (w > 0 && h > 0) {
                    val pixels = IntArray(w * h)
                    bitmap.getPixels(pixels, 0, w, x, y, w, h)
                    var sum = 0f
                    for (p in pixels) {
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        sum += (r + g + b) / 3f
                    }
                    rowList.add(sum / pixels.size)
                } else {
                    rowList.add(0f)
                }
            }
            result.add(rowList)
        }
        return result
    }

    private suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }
}
