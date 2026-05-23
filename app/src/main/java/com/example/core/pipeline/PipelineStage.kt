package com.example.core.pipeline

interface PipelineStage<I, O> {
    suspend operator fun invoke(input: I): O
}
