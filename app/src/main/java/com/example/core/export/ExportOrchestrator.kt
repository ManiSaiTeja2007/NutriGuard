package com.example.core.export

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    data class Success(val executionId: String, val path: String) : ExportState
    data class Failure(val message: String) : ExportState
}

object ExportOrchestrator {

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    fun clearState() {
        _state.value = ExportState.Idle
    }

    fun exportSession(context: Context, snapshot: PipelineSnapshot) {
        _state.value = ExportState.Exporting
        try {
            val writer = ExportFileWriter(context)
            val exporter = SessionExporter(writer)
            val exportPath = exporter.export(snapshot)
            if (exportPath != null) {
                _state.value = ExportState.Success(snapshot.executionId, exportPath)
            } else {
                _state.value = ExportState.Failure("SessionExporter returned null path")
            }
        } catch (e: Exception) {
            _state.value = ExportState.Failure(e.message ?: "Unknown export error")
        }
    }

    fun exportReplay(context: Context, snapshot: PipelineSnapshot) {
        exportSession(context, snapshot)
    }

    fun exportOverlay(context: Context, snapshot: PipelineSnapshot) {
        exportSession(context, snapshot)
    }
}
