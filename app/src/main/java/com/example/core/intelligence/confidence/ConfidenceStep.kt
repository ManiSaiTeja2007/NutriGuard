package com.example.core.intelligence.confidence

/**
 * Pure semantic-domain model capturing structured confidence calibration steps.
 * Does not contain any UI or presentation layer details.
 */
data class ConfidenceStep(
    val baseConfidence: Float,
    val contextBonus: Float,
    val finalConfidence: Float,
    val reason: String?,
    val influencingTokens: List<String>
)
