package com.example.core.intelligence

data class InterpretationTrace(
    val ocrText: String,
    val normalizedText: String,
    val aliasRepairedText: String,
    val ontologyMatchedName: String?,
    val confidenceBand: String,
    val finalInterpretation: String
) {
    /**
     * Renders a human-readable flowchart trace showing exactly why and how
     * the ingredient resolved, capturing the six stages of the pipeline.
     */
    val flowchart: String
        get() = "OCR: $ocrText\n" +
                "↓\n" +
                "Normalization: $normalizedText\n" +
                "↓\n" +
                "Alias Repair: $aliasRepairedText\n" +
                "↓\n" +
                "Additive Resolution: ${ontologyMatchedName ?: "None"}\n" +
                "↓\n" +
                "Confidence Calibration: $confidenceBand\n" +
                "↓\n" +
                "Final Interpretation:\n$finalInterpretation"
}
