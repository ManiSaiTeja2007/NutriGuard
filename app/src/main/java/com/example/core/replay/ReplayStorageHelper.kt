package com.example.core.replay

import android.content.Context
import com.example.core.intelligence.correction.CorrectionResult
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
        latencyMetrics: Map<String, Long>
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
