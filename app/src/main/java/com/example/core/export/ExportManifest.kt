package com.example.core.export

data class SnapshotMetadata(
    val pipelineVersion: String,
    val ontologyVersion: String,
    val preprocessingVersion: String,
    val executionId: String,
    val timestamp: Long
)

data class ExportManifest(
    val executionId: String,
    val schemaVersion: String = "1.0.0",
    val timestamp: Long,
    val fileHashes: Map<String, String>,
    val metadata: SnapshotMetadata
)
