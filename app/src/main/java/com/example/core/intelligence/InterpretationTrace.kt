package com.example.core.intelligence

import com.example.core.intelligence.confidence.DatasetProvenance

data class InterpretationTrace(
    val ocrText: String,
    val normalizedText: String,
    val aliasRepairedText: String,
    val ontologyMatchedName: String?,
    val contextualReconstructionText: String?,
    val confidenceBand: String,
    val finalInterpretation: String,
    val provenance: DatasetProvenance = DatasetProvenance.REAL_WORLD
) {
    /**
     * Renders a human-readable flowchart trace showing exactly why and how
     * the ingredient resolved, capturing the seven stages of the pipeline.
     */
    val flowchart: String
        get() = "OCR: $ocrText\n" +
                "↓\n" +
                "$provenance\n" +
                "↓\n" +
                "Normalization: $normalizedText\n" +
                "↓\n" +
                "Alias Repair: $aliasRepairedText\n" +
                "↓\n" +
                "Additive Resolution: ${ontologyMatchedName ?: "None"}\n" +
                "↓\n" +
                "Contextual Reconstruction: ${contextualReconstructionText ?: "None"}\n" +
                "↓\n" +
                "Confidence Calibration: $confidenceBand\n" +
                "↓\n" +
                "Final Interpretation:\n$finalInterpretation"
}

