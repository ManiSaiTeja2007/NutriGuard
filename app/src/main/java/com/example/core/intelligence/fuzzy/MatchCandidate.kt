package com.example.core.intelligence.fuzzy

data class MatchCandidate(
    val candidate: String,
    val confidence: Float,
    val distance: Int
)
