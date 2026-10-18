package com.example.eartrainer.ui.backup

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.ui.theme.EarTrainerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Accepted when picking a backup to import; file managers label JSON files inconsistently. */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/*")

@Composable
fun BackupRoute(viewModel: BackupViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.onExportTo(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onImportFrom(it.toString()) }
    }
    val fileName = stringResource(R.string.backup_file_name, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    BackupScreen(
        state = state,
        onExport = { exportLauncher.launch(fileName) },
        onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
        onMessageShown = viewModel::onMessageShown,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onMessageShown: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message.text(resources))
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                BackupSection(
                    title = stringResource(R.string.backup_auto_title),
                    description = stringResource(R.string.backup_auto_description),
                )
                HorizontalDivider()
                BackupSection(
                    title = stringResource(R.string.backup_export_title),
                    description = stringResource(R.string.backup_export_description),
                ) {
                    ActionButton(
                        label = stringResource(R.string.backup_export_button),
                        primary = true,
                        working = state.working == BackupAction.EXPORT,
                        enabled = state.working == null,
                        onClick = onExport,
                    )
                }
                HorizontalDivider()
                BackupSection(
                    title = stringResource(R.string.backup_import_title),
                    description = stringResource(R.string.backup_import_description),
                ) {
                    ActionButton(
                        label = stringResource(R.string.backup_import_button),
                        primary = false,
                        working = state.working == BackupAction.IMPORT,
                        enabled = state.working == null,
                        onClick = onImport,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupSection(title: String, description: String, action: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

/** A button that shows a spinner beside its label while its action runs. */
@Composable
private fun ActionButton(label: String, primary: Boolean, working: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(label)
        }
    }
    if (primary) {
        Button(onClick = onClick, enabled = enabled) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { content() }
    }
}

private fun BackupMessage.text(resources: Resources): String = when (this) {
    is BackupMessage.Exported -> resources.getQuantityString(R.plurals.backup_exported, answers, answers)
    is BackupMessage.Imported -> buildList {
        add(resources.getQuantityString(R.plurals.backup_imported, result.added, result.added))
        if (result.alreadyPresent > 0) {
            add(resources.getQuantityString(R.plurals.backup_imported_already_present, result.alreadyPresent, result.alreadyPresent))
        }
        if (result.unreadable > 0) {
            add(resources.getQuantityString(R.plurals.backup_imported_unreadable, result.unreadable, result.unreadable))
        }
        if (result.settingsRestored) add(resources.getString(R.string.backup_imported_settings))
    }.joinToString(" ")
    is BackupMessage.Failed -> resources.getString(
        when (reason) {
            FailureReason.FILE_ERROR -> if (action == BackupAction.EXPORT) R.string.backup_export_failed else R.string.backup_import_failed
            FailureReason.NOT_A_BACKUP -> R.string.backup_not_a_backup
            FailureReason.NEWER_VERSION -> R.string.backup_newer_version
            FailureReason.TOO_LARGE -> R.string.backup_too_large
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BackupScreenPreview() {
    EarTrainerTheme {
        BackupScreen(state = BackupUiState(working = BackupAction.EXPORT), onExport = {}, onImport = {}, onMessageShown = {}, onNavigateUp = {})
    }
}
