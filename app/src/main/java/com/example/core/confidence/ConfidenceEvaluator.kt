package com.example.core.confidence

import com.example.core.utils.AssetLoader
import org.json.JSONObject
import java.util.Locale

data class ConfidenceAssessment(
    val band: ConfidenceBand,
    val displayMessage: String,
    val isAmbiguous: Boolean
)

object ConfidenceEvaluator {
    private var highThreshold = 0.85f
    private var moderateThreshold = 0.70f
    private var lowThreshold = 0.50f
    private val messages = mutableMapOf<String, String>()

    init {
        try {
            val jsonStr = AssetLoader.loadAsset("confidence/confidence.json")
            val json = JSONObject(jsonStr)
            highThreshold = json.getDouble("high_threshold").toFloat()
            moderateThreshold = json.getDouble("moderate_threshold").toFloat()
            lowThreshold = json.getDouble("low_threshold").toFloat()
            
            val msgsJson = json.getJSONObject("messages")
            msgsJson.keys().forEach { key ->
                messages[key] = msgsJson.getString(key)
            }
        } catch (e: Exception) {
            // Static fallbacks
            messages["HIGH"] = "Exact Ontology Match"
            messages["MODERATE"] = "Alias Repair Match"
            messages["LOW"] = "Fuzzy Ambiguous Match"
            messages["UNCERTAIN"] = "Unknown Ingredient"
        }
    }

    /**
     * Categorizes a match confidence float score into a discrete [ConfidenceBand] bucket.
     * Generates standard display messages that show visible uncertainty for lower bands.
     */
    fun assess(confidence: Float, canonicalName: String): ConfidenceAssessment {
        return when {
            confidence >= highThreshold -> ConfidenceAssessment(
                band = ConfidenceBand.HIGH,
                displayMessage = canonicalName,
                isAmbiguous = false
            )
            confidence >= moderateThreshold -> ConfidenceAssessment(
                band = ConfidenceBand.MODERATE,
                displayMessage = "Possible Match: $canonicalName",
                isAmbiguous = true
            )
            confidence >= lowThreshold -> ConfidenceAssessment(
                band = ConfidenceBand.LOW,
                displayMessage = "Uncertain Match: $canonicalName",
                isAmbiguous = true
            )
            else -> ConfidenceAssessment(
                band = ConfidenceBand.UNCERTAIN,
                displayMessage = "Unrecognized Match: $canonicalName",
                isAmbiguous = true
            )
        }
    }
}
