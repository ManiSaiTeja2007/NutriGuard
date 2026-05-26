package com.example.core.confidence

import java.util.Locale

data class ConfidenceAssessment(
    val band: ConfidenceBand,
    val displayMessage: String,
    val isAmbiguous: Boolean
)

object ConfidenceEvaluator {
    /**
     * Categorizes a match confidence float score into a discrete [ConfidenceBand] bucket.
     * Generates standard display messages that show visible uncertainty for lower bands.
     */
    fun assess(confidence: Float, canonicalName: String): ConfidenceAssessment {
        return when {
            confidence >= 0.85f -> ConfidenceAssessment(
                band = ConfidenceBand.HIGH,
                displayMessage = canonicalName,
                isAmbiguous = false
            )
            confidence >= 0.70f -> ConfidenceAssessment(
                band = ConfidenceBand.MODERATE,
                displayMessage = "Possible Match: $canonicalName",
                isAmbiguous = true
            )
            confidence >= 0.50f -> ConfidenceAssessment(
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
