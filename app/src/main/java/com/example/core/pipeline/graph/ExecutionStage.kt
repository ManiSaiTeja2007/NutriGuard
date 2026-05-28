package com.example.core.pipeline.graph

interface ExecutionStage<in Input, out Output> {
    val stageName: String
    suspend fun execute(
        input: Input,
        context: SemanticRoutingContext,
        profiler: ExecutionProfiler
    ): ExecutionStageResult<Output>
}
