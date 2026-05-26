package com.example.ui.features.developer.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.export.PipelineSnapshotRepository
import com.example.core.export.SessionExporter
import com.example.core.export.ExportFileWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File

sealed interface ExportStatus {
    object Idle : ExportStatus
    object Exporting : ExportStatus
    data class Success(val path: String) : ExportStatus
    data class Failure(val message: String) : ExportStatus
}

data class DeveloperExportUiState(
    val isSnapshotAvailable: Boolean = false,
    val exportStatus: ExportStatus = ExportStatus.Idle,
    val exportedSessions: List<String> = emptyList()
)

class DeveloperExportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DeveloperExportUiState())
    val uiState: StateFlow<DeveloperExportUiState> = _uiState.asStateFlow()

    fun checkSnapshotStatus(context: Context) {
        val snapshot = PipelineSnapshotRepository.latest()
        val exportsDir = context.getExternalFilesDir("exports")
        val sessions = exportsDir?.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sortedDescending() ?: emptyList()

        _uiState.update {
            it.copy(
                isSnapshotAvailable = snapshot != null,
                exportedSessions = sessions
            )
        }
    }

    fun exportLatestSession(context: Context) {
        val snapshot = PipelineSnapshotRepository.latest() ?: run {
            _uiState.update { it.copy(exportStatus = ExportStatus.Failure("No execution snapshot available. Run the pipeline first.")) }
            return
        }

        _uiState.update { it.copy(exportStatus = ExportStatus.Exporting) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val writer = ExportFileWriter(context)
                val exporter = SessionExporter(writer)
                val exportPath = exporter.export(snapshot)
                if (exportPath != null) {
                    _uiState.update {
                        it.copy(
                            exportStatus = ExportStatus.Success(exportPath)
                        )
                    }
                    checkSnapshotStatus(context)
                } else {
                    _uiState.update {
                        it.copy(
                            exportStatus = ExportStatus.Failure("SessionExporter returned null path")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportStatus = ExportStatus.Failure(e.message ?: "Unknown error")
                    )
                }
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(exportStatus = ExportStatus.Idle) }
    }
}
