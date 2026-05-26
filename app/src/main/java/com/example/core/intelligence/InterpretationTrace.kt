package com.example.core.intelligence

data class InterpretationTrace(
    val matchedAlias: String?,
    val confidence: Float,
    val ontologySource: String?,
    val resolutionPath: String,
    val rejectedCandidates: List<String> = emptyList()
)
