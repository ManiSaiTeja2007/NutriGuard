package com.example.core.replay

import android.content.Context
import android.graphics.Rect
import com.example.core.intelligence.correction.CorrectionResult
import com.example.core.ocr.OCRLine
import com.example.core.ocr.OCRWord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReplayStorageHelper {
    
    fun generateReplayId(sourceName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(sourceName.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun saveReplay(
        context: Context,
        sourceImage: String,
        ocrOutput: String,
        normalizedText: String,
        extractedIngredients: List<String>,
        canonicalIngredients: List<CorrectionResult>,
        metrics: Map<String, Double>,
        failures: List<Map<String, Any>>,
        latencyMetrics: Map<String, Long>,
        ocrWords: List<OCRWord> = emptyList(),
        reconstructedLines: List<OCRLine> = emptyList(),
        detectedParagraphs: List<OCRLine> = emptyList(),
        passesRun: List<String> = emptyList()
    ): String {
        val replayId = generateReplayId(sourceImage + System.currentTimeMillis())
        
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())

        val replayObj = JSONObject().apply {
            put("replay_id", replayId)
            put("source_image", sourceImage)
            put("ocr_output", ocrOutput)
            put("normalized_text", normalizedText)
            
            // Extracted
            val extArr = JSONArray()
            extractedIngredients.forEach { extArr.put(it) }
            put("extracted_ingredients", extArr)

            // Canonical Corrections
            val canonArr = JSONArray()
            canonicalIngredients.forEach { result ->
                val ingObj = JSONObject().apply {
                    put("canonical", result.canonical)
                    put("confidence", result.confidence.toDouble())
                    put("originalToken", result.originalToken)
                    put("ontologyCategory", result.ontologyCategory ?: "")
                    put("disambiguationRule", result.disambiguationRule ?: "")
                    put("groupPath", result.groupPath)

                    val stepsArr = JSONArray()
                    result.debugSteps.forEach { stepsArr.put(it) }
                    put("debugSteps", stepsArr)

                    val failsArr = JSONArray()
                    result.failures.forEach { failsArr.put(it.name) }
                    put("failures", failsArr)

                    val phraseArr = JSONArray()
                    result.phraseWindow.forEach { phraseArr.put(it) }
                    put("phraseWindow", phraseArr)
                }
                canonArr.put(ingObj)
            }
            put("canonical_ingredients", canonArr)

            // Metrics
            val metricObj = JSONObject()
            metrics.forEach { (k, v) -> metricObj.put(k, v) }
            put("metrics", metricObj)

            // Failures
            val failArr = JSONArray()
            failures.forEach { fail ->
                val fObj = JSONObject()
                fail.forEach { (k, v) -> fObj.put(k, v) }
                failArr.put(fObj)
            }
            put("failures", failArr)

            // Latency
            val latencyObj = JSONObject()
            latencyMetrics.forEach { (k, v) -> latencyObj.put(k, v) }
            put("latency_metrics_ms", latencyObj)

            // Structured OCR Data
            val wordsArr = JSONArray()
            ocrWords.forEach { word ->
                wordsArr.put(JSONObject().apply {
                    put("text", word.text)
                    put("confidence", word.confidence.toDouble())
                    put("bounds", JSONObject().apply {
                        put("left", word.bounds.left)
                        put("top", word.bounds.top)
                        put("right", word.bounds.right)
                        put("bottom", word.bounds.bottom)
                    })
                })
            }
            put("ocr_words", wordsArr)

            val linesArr = JSONArray()
            reconstructedLines.forEach { line ->
                linesArr.put(JSONObject().apply {
                    put("confidence", line.confidence.toDouble())
                    put("bounds", JSONObject().apply {
                        put("left", line.bounds.left)
                        put("top", line.bounds.top)
                        put("right", line.bounds.right)
                        put("bottom", line.bounds.bottom)
                    })
                    val lineWordsArr = JSONArray()
                    line.words.forEach { w ->
                        lineWordsArr.put(w.text)
                    }
                    put("words", lineWordsArr)
                })
            }
            put("reconstructed_lines", linesArr)

            val paragraphsArr = JSONArray()
            detectedParagraphs.forEach { para ->
                paragraphsArr.put(JSONObject().apply {
                    put("confidence", para.confidence.toDouble())
                    put("bounds", JSONObject().apply {
                        put("left", para.bounds.left)
                        put("top", para.bounds.top)
                        put("right", para.bounds.right)
                        put("bottom", para.bounds.bottom)
                    })
                    val paraWordsArr = JSONArray()
                    para.words.forEach { w ->
                        paraWordsArr.put(w.text)
                    }
                    put("words", paraWordsArr)
                })
            }
            put("detected_paragraphs", paragraphsArr)

            val passesArr = JSONArray()
            passesRun.forEach { passesArr.put(it) }
            put("passes_run", passesArr)

            put("pipeline_version", "1.0.0")
            put("benchmark_schema_version", "1.0.0")
            put("dataset_version", "1.0.0")
            put("timestamp", timestamp)
        }

        val file = File(context.cacheDir, "${replayId}_replay.json")
        file.writeText(replayObj.toString(2))
        return replayId
    }
}
