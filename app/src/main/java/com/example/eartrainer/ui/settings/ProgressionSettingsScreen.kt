package com.example.eartrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ChordVocabulary
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.ProgressionSettings
import com.example.eartrainer.exercise.ProgressionSource
import com.example.eartrainer.music.Mode
import com.example.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun ProgressionSettingsRoute(viewModel: ProgressionSettingsViewModel, onNavigateUp: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    ProgressionSettingsScreen(
        settings = loaded,
        onSourceSelected = viewModel::onSourceSelected,
        onModeToggled = viewModel::onModeToggled,
        onVocabularySelected = viewModel::onVocabularySelected,
        onLengthSelected = viewModel::onLengthSelected,
        onKeyChoiceSelected = viewModel::onKeyChoiceSelected,
        onRestoreDefaults = viewModel::onRestoreDefaults,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionSettingsScreen(
    settings: ProgressionSettings,
    onSourceSelected: (ProgressionSource) -> Unit,
    onModeToggled: (Mode) -> Unit,
    onVocabularySelected: (ChordVocabulary) -> Unit,
    onLengthSelected: (Int) -> Unit,
    onKeyChoiceSelected: (KeyChoice) -> Unit,
    onRestoreDefaults: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
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
                Text(
                    stringResource(R.string.settings_new_session_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingsSection(
                    title = stringResource(R.string.progression_settings_source_title),
                    description = stringResource(R.string.progression_settings_source_description),
                ) {
                    ProgressionSource.entries.forEach { source ->
                        SettingsChip(source.displayName, selected = settings.source == source, onClick = { onSourceSelected(source) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.settings_modes_title),
                    description = stringResource(R.string.settings_modes_description),
                ) {
                    Mode.entries.forEach { mode ->
                        SettingsChip(mode.displayName, selected = mode in settings.modes, onClick = { onModeToggled(mode) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.progression_settings_vocabulary_title),
                    description = stringResource(R.string.progression_settings_vocabulary_description),
                ) {
                    ChordVocabulary.entries.forEach { vocabulary ->
                        SettingsChip(
                            vocabulary.displayName,
                            selected = settings.vocabulary == vocabulary,
                            onClick = { onVocabularySelected(vocabulary) },
                        )
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.progression_settings_length_title),
                    description = stringResource(R.string.progression_settings_length_description),
                ) {
                    ProgressionSettings.LENGTH_OPTIONS.forEach { length ->
                        SettingsChip(length.toString(), selected = settings.length == length, onClick = { onLengthSelected(length) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.settings_key_title),
                    description = stringResource(R.string.settings_key_description),
                ) {
                    KeyChoice.entries.forEach { choice ->
                        SettingsChip(choice.displayName, selected = settings.keyChoice == choice, onClick = { onKeyChoiceSelected(choice) })
                    }
                }

                TextButton(
                    onClick = onRestoreDefaults,
                    enabled = settings != ProgressionSettings(),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.settings_restore_defaults))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressionSettingsPreview() {
    EarTrainerTheme {
        ProgressionSettingsScreen(
            settings = ProgressionSettings(modes = Mode.entries.toSet(), vocabulary = ChordVocabulary.ALL, length = 5),
            onSourceSelected = {}, onModeToggled = {}, onVocabularySelected = {}, onLengthSelected = {}, onKeyChoiceSelected = {},
            onRestoreDefaults = {}, onNavigateUp = {},
        )
    }
}
