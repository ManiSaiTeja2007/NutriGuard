package com.example.core.intelligence

import com.example.core.ontology.IngredientCategory
import com.example.core.confidence.ConfidenceBand

data class InterpretedIngredient(
    val originalText: String,
    val normalizedText: String,
    val canonicalName: String?,
    val category: IngredientCategory,
    val confidence: ConfidenceBand,
    val additiveCode: String?,
    val explanation: String?,
    val warnings: List<String>,
    val failures: List<InterpretationFailure> = emptyList(),
    val trace: InterpretationTrace? = null,
    val resolutionSource: ResolutionSource = ResolutionSource.UNKNOWN
) {
    val canonicalText: String? get() = canonicalName
}
