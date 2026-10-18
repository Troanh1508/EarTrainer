package com.example.eartrainer.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.backup.AppDataBackup
import com.example.eartrainer.data.backup.BackupException
import com.example.eartrainer.data.backup.DocumentStreams
import com.example.eartrainer.data.backup.ImportResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class BackupAction { EXPORT, IMPORT }

sealed interface BackupMessage {
    data class Exported(val answers: Int) : BackupMessage
    data class Imported(val result: ImportResult) : BackupMessage
    data class Failed(val action: BackupAction, val reason: FailureReason) : BackupMessage
}

enum class FailureReason {
    /** The file could not be opened, read or written. */
    FILE_ERROR,
    NOT_A_BACKUP,
    NEWER_VERSION,
    TOO_LARGE,
}

data class BackupUiState(
    /** The export or import in progress, if any; only one runs at a time. */
    val working: BackupAction? = null,
    /** The outcome of the last export or import, until the screen has shown it. */
    val message: BackupMessage? = null,
)

class BackupViewModel(
    private val backup: AppDataBackup,
    private val documents: DocumentStreams,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Exports to the document the user just created with the file picker. */
    fun onExportTo(uri: String) = run(BackupAction.EXPORT) {
        val output = withContext(ioDispatcher) { documents.openOutput(uri) }
        BackupMessage.Exported(backup.export(output))
    }

    /** Imports from the document the user just picked. */
    fun onImportFrom(uri: String) = run(BackupAction.IMPORT) {
        val input = withContext(ioDispatcher) { documents.openInput(uri) }
        BackupMessage.Imported(backup.import(input))
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun run(action: BackupAction, block: suspend () -> BackupMessage) {
        if (_state.value.working != null) return
        _state.update { BackupUiState(working = action) }
        viewModelScope.launch {
            val message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                BackupMessage.Failed(action, e.reason.toFailureReason())
            } catch (_: IOException) {
                BackupMessage.Failed(action, FailureReason.FILE_ERROR)
            } catch (_: SecurityException) {
                // The picker's permission was revoked or the provider refused access.
                BackupMessage.Failed(action, FailureReason.FILE_ERROR)
            }
            _state.update { BackupUiState(message = message) }
        }
    }

    private fun BackupException.Reason.toFailureReason() = when (this) {
        BackupException.Reason.NOT_A_BACKUP -> FailureReason.NOT_A_BACKUP
        BackupException.Reason.NEWER_VERSION -> FailureReason.NEWER_VERSION
        BackupException.Reason.TOO_LARGE -> FailureReason.TOO_LARGE
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                BackupViewModel(container.dataBackup, container.documents)
            }
        }
    }
}
