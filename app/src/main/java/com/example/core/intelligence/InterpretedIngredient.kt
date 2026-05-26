package com.example.core.intelligence

import com.example.core.ontology.IngredientCategory
import com.example.core.confidence.ConfidenceBand

data class InterpretedIngredient(
    val canonicalName: String,
    val category: IngredientCategory,
    val confidence: Float,
    val confidenceBand: ConfidenceBand,
    val additiveCode: String?,
    val explanation: String?,
    val warnings: List<String>,
    val failures: List<InterpretationFailure> = emptyList(),
    val trace: InterpretationTrace? = null
)
