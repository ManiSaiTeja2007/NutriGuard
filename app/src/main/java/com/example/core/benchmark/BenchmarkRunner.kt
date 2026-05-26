package com.example.core.benchmark

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.example.core.imaging.ImageSource
import com.example.core.intelligence.vocabulary.IngredientVocabulary
import com.example.core.intelligence.fuzzy.Levenshtein
import com.example.core.ontology.OntologyRepository
import com.example.core.ontology.IngredientCategory
import com.example.core.additives.ENumberRepository
import com.example.core.pipeline.*
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BenchmarkProgress(
    val current: Int,
    val total: Int,
    val currentImage: String
)

data class SingleRecordResult(
    val imagePath: String,
    val category: String,
    val cer: Float,
    val wer: Float,
    val extractionPrecision: Float,
    val extractionRecall: Float,
    val extractionF1: Float,
    val canonicalPrecision: Float,
    val canonicalRecall: Float,
    val canonicalF1: Float,
    val canonicalAccuracy: Float,
    val failuresCount: Int,
    val latencyTotalMs: Long,
    val latencyOcrMs: Long,
    val latencySemanticMs: Long,
    val ingredientsCount: Int,
    // Stage 10 fields:
    val additiveAccuracy: Float = 1.0f,
    val ontologyMatchAccuracy: Float = 1.0f,
    val falseInterpretationRate: Float = 0.0f,
    val unknownPreservationRate: Float = 1.0f,
    val confidenceCalibrationAccuracy: Float = 1.0f
)

data class BenchmarkSummary(
    val timestamp: String,
    val pipelineVersion: String,
    val datasetVersion: String,
    val totalImagesProcessed: Int,
    val totalRuntimeMs: Long,
    val averageCer: Float,
    val averageWer: Float,
    val averageExtractionPrecision: Float,
    val averageExtractionRecall: Float,
    val averageExtractionF1: Float,
    val averageCanonicalPrecision: Float,
    val averageCanonicalRecall: Float,
    val averageCanonicalF1: Float,
    val averageCanonicalAccuracy: Float,
    val averageOcrLatencyMs: Long,
    val averageSemanticLatencyMs: Long,
    val averageTotalLatencyMs: Long,
    // Stage 10 fields:
    val averageAdditiveAccuracy: Float = 1.0f,
    val averageOntologyMatchAccuracy: Float = 1.0f,
    val averageFalseInterpretationRate: Float = 0.0f,
    val averageUnknownPreservationRate: Float = 1.0f,
    val averageConfidenceCalibrationAccuracy: Float = 1.0f
)

data class BenchmarkGroundTruth(
    val rawIngredients: String,
    val expectedCanonical: List<String>,
    val nutrition: Map<String, String>,
    val failureTags: List<String>
)

class BenchmarkRunner(
    private val context: Context
) {
    private val ocrPipeline = OCRPipeline()
    private val vocabulary = IngredientVocabulary()
    private val semanticPipeline = SemanticPipeline(vocabulary)
    private val pipelineRunner = PipelineRunner(ocrPipeline, semanticPipeline)

    fun close() {
        ocrPipeline.close()
    }

    suspend fun run(
        manifestPath: String = "manifests/master_manifest.json",
        subset: String = "all",
        onProgress: (BenchmarkProgress) -> Unit = {}
    ): Pair<BenchmarkSummary, List<SingleRecordResult>> {
        val startTime = SystemClock.elapsedRealtime()

        val manifestContent = context.assets.open(manifestPath).bufferedReader().use { it.readText() }
        val manifestJson = JSONObject(manifestContent)
        val entriesArray = manifestJson.optJSONArray("entries") ?: org.json.JSONArray()

        val entries = mutableListOf<JSONObject>()
        for (i in 0 until entriesArray.length()) {
            entries.add(entriesArray.getJSONObject(i))
        }

        // Filter by subset if not "all"
        val subsetMappings = mapOf(
            "clean" to listOf("raw_clean"),
            "blurry" to listOf("raw_blurry", "synth_blur"),
            "low_light" to listOf("raw_lowlight", "synth_lowlight"),
            "curved_packaging" to listOf("raw_curved"),
            "multilingual" to listOf("raw_multilingual"),
            "catastrophic_ocr" to listOf("raw_rotated", "synth_rotation", "raw_difficult_fonts", "raw_handwritten")
        )

        val filteredEntries = entries.filter { entry ->
            if (subset == "all") true
            else {
                val category = entry.optString("category", "")
                val allowed = subsetMappings[subset] ?: emptyList()
                category in allowed
            }
        }.sortedBy { it.optString("image_path", "") }

        val records = mutableListOf<SingleRecordResult>()
        val total = filteredEntries.size

        filteredEntries.forEachIndexed { index, entry ->
            val imagePathRel = entry.getString("image_path").replace("\\", "/")
            val annotationPathRel = entry.getString("annotation_path").replace("\\", "/")
            val category = entry.optString("category", "")

            onProgress(BenchmarkProgress(index + 1, total, imagePathRel))

            try {
                // Load ground truth
                val annotationText = context.assets.open(annotationPathRel).bufferedReader().use { it.readText() }
                val gt = parseAnnotationText(annotationText)
                
                val expectedIngredients = gt.rawIngredients.split(",")
                    .map { it.trim().lowercase().removeSuffix(".").removeSuffix(",") }
                    .filter { it.isNotEmpty() && !it.startsWith("ingredients") }

                // Load image
                val bitmap = context.assets.open(imagePathRel).use {
                    BitmapFactory.decodeStream(it)
                } ?: throw IllegalArgumentException("Could not load bitmap: $imagePathRel")

                // Run canonical pipeline
                val config = PipelineConfig(
                    mode = PipelineMode.BENCHMARK,
                    enableReplay = true,
                    enableMetrics = true,
                    enableOverlayData = false
                )
                val result = pipelineRunner.run(
                    bitmap = bitmap,
                    rotationDegrees = 0,
                    source = ImageSource.TEST_ASSET,
                    config = config
                )

                // 1. Calculate OCR CER/WER
                val cer = calculateCer(gt.rawIngredients, result.ocrLines.joinToString(" ") { line -> line.words.joinToString(" ") { it.text } })
                val wer = calculateWer(gt.rawIngredients, result.ocrLines.joinToString(" ") { line -> line.words.joinToString(" ") { it.text } })

                // 2. Calculate Extraction Precision/Recall/F1
                val actualExtracted = result.semanticIngredients.map { it.originalToken }
                val extMetrics = calculatePrecisionRecallF1(expectedIngredients, actualExtracted)

                // 3. Calculate Canonical Precision/Recall/F1/Accuracy
                val actualCanonical = result.semanticIngredients.map { it.canonical }
                val canonMetrics = calculatePrecisionRecallF1(gt.expectedCanonical, actualCanonical)
                val canonicalAccuracy = if (gt.expectedCanonical.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet() ==
                    actualCanonical.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()) 1.0f else 0.0f

                // 4. Calculate Stage 10 Semantic Safety metrics
                val expectedAdditives = gt.expectedCanonical.filter { it.startsWith("e", ignoreCase = true) || ENumberRepository.find(it) != null }.map { it.lowercase().trim() }.toSet()
                val actualAdditives = result.interpretedIngredients.filter { it.additiveCode != null }.map { it.canonicalName.lowercase().trim() }.toSet()
                val additiveAcc = if (expectedAdditives.isEmpty() && actualAdditives.isEmpty()) 1.0f else {
                    val intersection = expectedAdditives.intersect(actualAdditives).size
                    intersection.toFloat() / maxOf(1, expectedAdditives.size)
                }

                val expectedCategories = gt.expectedCanonical.mapNotNull { 
                    OntologyRepository.find(it)?.category ?: ENumberRepository.find(it)?.category 
                }.map { it.name }.toSet()
                val actualCategories = result.interpretedIngredients.map { it.category.name }.toSet()
                val ontologyMatchAcc = if (expectedCategories.isEmpty() && actualCategories.isEmpty()) 1.0f else {
                    val intersection = expectedCategories.intersect(actualCategories).size
                    intersection.toFloat() / maxOf(1, expectedCategories.size)
                }

                val falseInterpretations = actualCanonical.filter { it !in gt.expectedCanonical && it.lowercase().trim() != "unknown" }
                val falseInterpretationRate = if (actualCanonical.isEmpty()) 0.0f else {
                    falseInterpretations.size.toFloat() / actualCanonical.size
                }

                val expectedUnknownsCount = expectedIngredients.count { 
                    OntologyRepository.find(it) == null && ENumberRepository.find(it) == null 
                }
                val actualUnknownsPreserved = result.interpretedIngredients.count { 
                    it.category == IngredientCategory.UNKNOWN 
                }
                val unknownPreservationRate = if (expectedUnknownsCount == 0) 1.0f else {
                    actualUnknownsPreserved.toFloat() / expectedUnknownsCount
                }

                val highConfidenceCorrect = result.interpretedIngredients.count { 
                    it.confidenceBand == com.example.core.confidence.ConfidenceBand.HIGH && it.canonicalName in gt.expectedCanonical 
                }
                val totalHighConfidence = result.interpretedIngredients.count { 
                    it.confidenceBand == com.example.core.confidence.ConfidenceBand.HIGH 
                }
                val confidenceCalibrationAcc = if (totalHighConfidence == 0) 1.0f else {
                    highConfidenceCorrect.toFloat() / totalHighConfidence
                }

                records.add(
                    SingleRecordResult(
                        imagePath = imagePathRel,
                        category = category,
                        cer = cer,
                        wer = wer,
                        extractionPrecision = extMetrics.precision,
                        extractionRecall = extMetrics.recall,
                        extractionF1 = extMetrics.f1,
                        canonicalPrecision = canonMetrics.precision,
                        canonicalRecall = canonMetrics.recall,
                        canonicalF1 = canonMetrics.f1,
                        canonicalAccuracy = canonicalAccuracy,
                        failuresCount = result.failures.size,
                        latencyTotalMs = result.metrics.totalLatencyMs,
                        latencyOcrMs = result.metrics.ocrLatencyMs,
                        latencySemanticMs = result.metrics.normalizationLatencyMs +
                                result.metrics.extractionLatencyMs +
                                result.metrics.groupingLatencyMs +
                                result.metrics.phraseCorrectionLatencyMs +
                                result.metrics.correctionLatencyMs,
                        ingredientsCount = result.semanticIngredients.size,
                        additiveAccuracy = additiveAcc,
                        ontologyMatchAccuracy = ontologyMatchAcc,
                        falseInterpretationRate = falseInterpretationRate,
                        unknownPreservationRate = unknownPreservationRate,
                        confidenceCalibrationAccuracy = confidenceCalibrationAcc
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val totalRuntimeMs = SystemClock.elapsedRealtime() - startTime
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val summary = BenchmarkSummary(
            timestamp = sdf.format(Date()),
            pipelineVersion = "1.0.0",
            datasetVersion = "1.0.0",
            totalImagesProcessed = records.size,
            totalRuntimeMs = totalRuntimeMs,
            averageCer = records.map { it.cer }.average().toFloat(),
            averageWer = records.map { it.wer }.average().toFloat(),
            averageExtractionPrecision = records.map { it.extractionPrecision }.average().toFloat(),
            averageExtractionRecall = records.map { it.extractionRecall }.average().toFloat(),
            averageExtractionF1 = records.map { it.extractionF1 }.average().toFloat(),
            averageCanonicalPrecision = records.map { it.canonicalPrecision }.average().toFloat(),
            averageCanonicalRecall = records.map { it.canonicalRecall }.average().toFloat(),
            averageCanonicalF1 = records.map { it.canonicalF1 }.average().toFloat(),
            averageCanonicalAccuracy = records.map { it.canonicalAccuracy }.average().toFloat(),
            averageOcrLatencyMs = records.map { it.latencyOcrMs }.average().toLong(),
            averageSemanticLatencyMs = records.map { it.latencySemanticMs }.average().toLong(),
            averageTotalLatencyMs = records.map { it.latencyTotalMs }.average().toLong(),
            averageAdditiveAccuracy = records.map { it.additiveAccuracy }.average().toFloat(),
            averageOntologyMatchAccuracy = records.map { it.ontologyMatchAccuracy }.average().toFloat(),
            averageFalseInterpretationRate = records.map { it.falseInterpretationRate }.average().toFloat(),
            averageUnknownPreservationRate = records.map { it.unknownPreservationRate }.average().toFloat(),
            averageConfidenceCalibrationAccuracy = records.map { it.confidenceCalibrationAccuracy }.average().toFloat()
        )

        return Pair(summary, records)
    }

    private fun parseAnnotationText(content: String): BenchmarkGroundTruth {
        val sections = mutableMapOf<String, MutableList<String>>()
        var currentSection: String? = null
        var currentLines = mutableListOf<String>()

        content.lineSequence().forEach { line ->
            val lineStrip = line.trim()
            if (lineStrip.startsWith("[") && lineStrip.endsWith("]")) {
                if (currentSection != null) {
                    sections[currentSection] = currentLines
                }
                currentSection = lineStrip.substring(1, lineStrip.length - 1)
                currentLines = mutableListOf()
            } else if (lineStrip.isNotEmpty()) {
                currentLines.add(lineStrip)
            }
        }
        if (currentSection != null) {
            sections[currentSection] = currentLines
        }

        val rawIngredientsLines = sections["RAW INGREDIENTS"].orEmpty()
        val rawIngredients = if (rawIngredientsLines.isNotEmpty()) {
            val firstLine = rawIngredientsLines[0]
            if (firstLine.contains(":")) {
                firstLine.substringAfter(":").trim()
            } else {
                firstLine
            }
        } else {
            ""
        }

        val expectedCanonical = sections["EXPECTED CANONICAL"].orEmpty()

        val nutrition = mutableMapOf<String, String>()
        sections["NUTRITION VALUES"].orEmpty().forEach { line ->
            if (line.contains(":")) {
                val key = line.substringBefore(":").trim().lowercase()
                val value = line.substringAfter(":").trim()
                nutrition[key] = value
            }
        }

        val failureTags = sections["FAILURE_TAGS"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

        return BenchmarkGroundTruth(rawIngredients, expectedCanonical, nutrition, failureTags)
    }

    private fun calculateCer(groundTruth: String, hypothesis: String): Float {
        if (groundTruth.isEmpty()) {
            return if (hypothesis.isEmpty()) 0.0f else 1.0f
        }
        val dist = Levenshtein.distance(groundTruth, hypothesis)
        return dist.toFloat() / groundTruth.length
    }

    private fun calculateWer(groundTruth: String, hypothesis: String): Float {
        val gtWords = groundTruth.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val hypWords = hypothesis.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (gtWords.isEmpty()) {
            return if (hypWords.isEmpty()) 0.0f else 1.0f
        }
        val dist = wordEditDistance(gtWords, hypWords)
        return dist.toFloat() / gtWords.size
    }

    private fun wordEditDistance(s1: List<String>, s2: List<String>): Int {
        val len1 = s1.size
        val len2 = s2.size
        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val str1 = if (len1 >= len2) s1 else s2
        val str2 = if (len1 >= len2) s2 else s1

        val dp = IntArray(str2.size + 1) { it }
        for (i in 1..str1.size) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..str2.size) {
                val temp = dp[j]
                if (str1[i - 1] == str2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
                }
                prev = temp
            }
        }
        return dp[str2.size]
    }

    private data class PrecisionRecallF1(
        val precision: Float,
        val recall: Float,
        val f1: Float
    )

    private fun calculatePrecisionRecallF1(expectedList: List<String>, actualList: List<String>): PrecisionRecallF1 {
        val expectedSet = expectedList.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val actualSet = actualList.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        if (expectedSet.isEmpty() && actualSet.isEmpty()) {
            return PrecisionRecallF1(1.0f, 1.0f, 1.0f)
        }
        if (expectedSet.isEmpty() || actualSet.isEmpty()) {
            return PrecisionRecallF1(0.0f, 0.0f, 0.0f)
        }

        val truePositives = expectedSet.intersect(actualSet).size
        val precision = truePositives.toFloat() / actualSet.size
        val recall = truePositives.toFloat() / expectedSet.size
        val f1 = if (precision + recall == 0.0f) 0.0f else 2 * (precision * recall) / (precision + recall)

        return PrecisionRecallF1(precision, recall, f1)
    }
}
