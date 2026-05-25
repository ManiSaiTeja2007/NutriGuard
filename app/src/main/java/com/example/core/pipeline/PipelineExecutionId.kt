package com.example.core.pipeline

import java.util.UUID

object PipelineExecutionId {
    fun generate(): UUID = UUID.randomUUID()
}
