package com.example.core.pipeline.graph

import com.example.core.replay.ReplayStageTrace

class ReplayGenerationStage : ExecutionStage<Unit, List<ReplayStageTrace>> {
    override val stageName: String = "replay_generation"

    override suspend fun execute(
        input: Unit,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<List<ReplayStageTrace>> {
        val started = android.os.SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        val traces = mutableListOf<ReplayStageTrace>()
        
        val results = context.metadata["stageResults"] as? List<StageResult> ?: emptyList()
        
        for (res in results) {
            val inputStr = when (res.stageName) {
                "structural_analysis" -> "bitmap_${context.imageWidth}x${context.imageHeight}"
                "targeted_ocr" -> "zones_${context.detectedZones.size}"
                "section_classification" -> "ocr_lines_${context.targetedOcrLines.size}"
                "semantic_routing" -> "sections_${context.classifiedSections.size}"
                "specialized_interpretation" -> "routing_result"
                "contextual_reconstruction" -> "corrected_tokens"
                "aggregation" -> "interpreted_ingredients"
                "confidence_calibration" -> "aggregated_output"
                else -> ""
            }
            val outputStr = res.replayArtifacts.toString()
            traces.add(ReplayStageTrace(res.stageName, inputStr, outputStr, res.latencyMs))
        }

        val latency = android.os.SystemClock.elapsedRealtime() - started

        return ExecutionStageResult(
            executionId = context.executionId,
            stageName = stageName,
            output = traces,
            latencyMs = latency,
            replayArtifacts = mapOf("tracesCount" to traces.size),
            failures = failures
        )
    }
}
