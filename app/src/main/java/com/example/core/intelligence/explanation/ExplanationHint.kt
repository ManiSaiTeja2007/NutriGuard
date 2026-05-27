package com.example.core.intelligence.explanation

enum class ExplanationType(val defaultFriendlyReason: String) {
    SPELLING_CORRECTION("Corrected spelling variation"),
    ALIAS_RESOLUTION("Resolved from alias name"),
    TRANSLITERATION("Translated standard terminology"),
    ADDITIVE_STANDARDIZATION("Standardized additive notation"),
    CONTEXTUAL_RECONSTRUCTION("Reconstructed from ingredient context"),
    NO_CHANGES("")
}

data class ExplanationHint(
    val type: ExplanationType,
    val originalText: String?,
    val reconstructedText: String?,
    val reason: String = type.defaultFriendlyReason
)
