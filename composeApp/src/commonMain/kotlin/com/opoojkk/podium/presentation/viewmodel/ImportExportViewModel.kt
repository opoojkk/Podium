package com.opoojkk.podium.presentation.viewmodel

import com.opoojkk.podium.data.repository.PodcastRepository
import com.opoojkk.podium.util.ErrorHandler
import com.opoojkk.podium.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for import operations.
 */
data class ImportState(
    val text: String = "",
    val isProcessing: Boolean = false,
    val result: PodcastRepository.OpmlImportResult? = null,
    val errorMessage: String? = null
)

/**
 * State for export operations.
 */
data class ExportState(
    val format: PodcastRepository.ExportFormat = PodcastRepository.ExportFormat.OPML,
    val content: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing OPML import and export operations.
 */
class ImportExportViewModel(
    private val repository: PodcastRepository,
    private val scope: CoroutineScope
) {
    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _exportState = MutableStateFlow(ExportState())
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /**
     * Update import text.
     */
    fun setImportText(text: String) {
        _importState.update { it.copy(text = text) }
    }

    /**
     * Start import process.
     */
    fun startImport() {
        val content = _importState.value.text.trim()
        if (content.isEmpty() || _importState.value.isProcessing) {
            return
        }

        _importState.update {
            it.copy(
                isProcessing = true,
                result = null,
                errorMessage = null
            )
        }

        scope.launch {
            try {
                Logger.d("ImportExportViewModel") { "Starting import process" }
                val result = repository.importSubscriptions(content)
                Logger.i("ImportExportViewModel") {
                    "Import completed - Success: ${result.successCount}, Failed: ${result.failedCount}"
                }

                _importState.update {
                    it.copy(
                        result = result,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                val userError = ErrorHandler.logAndHandle("ImportExportViewModel", e)
                _importState.update {
                    it.copy(
                        errorMessage = userError.message,
                        isProcessing = false
                    )
                }
            }
        }
    }

    /**
     * Reset import state.
     */
    fun resetImport() {
        _importState.value = ImportState()
    }

    /**
     * Set export format and trigger export.
     */
    fun setExportFormat(format: PodcastRepository.ExportFormat) {
        _exportState.update { it.copy(format = format) }
        startExport()
    }

    /**
     * Start export process.
     */
    fun startExport() {
        _exportState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                content = null
            )
        }

        scope.launch {
            try {
                Logger.d("ImportExportViewModel") { "Starting export process with format: ${_exportState.value.format}" }
                val content = repository.exportSubscriptions(_exportState.value.format)
                Logger.i("ImportExportViewModel") { "Export completed, content length: ${content.length}" }

                _exportState.update {
                    it.copy(
                        content = content,
                        isProcessing = false
                    )
                }
            } catch (e: Exception) {
                val userError = ErrorHandler.logAndHandle("ImportExportViewModel", e)
                _exportState.update {
                    it.copy(
                        errorMessage = userError.message,
                        isProcessing = false
                    )
                }
            }
        }
    }

    /**
     * Reset export state.
     */
    fun resetExport() {
        _exportState.value = ExportState()
    }
}
