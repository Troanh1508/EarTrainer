package com.troanhmolo.eartrainer.ui.settings

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
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.exercise.DictationSettings
import com.troanhmolo.eartrainer.exercise.KeyChoice
import com.troanhmolo.eartrainer.exercise.KeyContext
import com.troanhmolo.eartrainer.music.Mode
import com.troanhmolo.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun DictationSettingsRoute(viewModel: DictationSettingsViewModel, onNavigateUp: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    DictationSettingsScreen(
        settings = loaded,
        onModeToggled = viewModel::onModeToggled,
        onLengthSelected = viewModel::onLengthSelected,
        onKeyChoiceSelected = viewModel::onKeyChoiceSelected,
        onContextSelected = viewModel::onContextSelected,
        onRestoreDefaults = viewModel::onRestoreDefaults,
        onFocusOnWeakSpotsChanged = viewModel::onFocusOnWeakSpotsChanged,
        onNavigateUp = onNavigateUp,
        instrumentSetting = { InstrumentSettingRoute() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationSettingsScreen(
    settings: DictationSettings,
    onModeToggled: (Mode) -> Unit,
    onLengthSelected: (Int) -> Unit,
    onKeyChoiceSelected: (KeyChoice) -> Unit,
    onContextSelected: (KeyContext) -> Unit,
    onRestoreDefaults: () -> Unit,
    onFocusOnWeakSpotsChanged: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
    // App-wide, so the route supplies it; left out of previews.
    instrumentSetting: @Composable () -> Unit = {},
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
                    title = stringResource(R.string.settings_modes_title),
                    description = stringResource(R.string.settings_modes_description),
                ) {
                    Mode.entries.forEach { mode ->
                        SettingsChip(mode.displayName, selected = mode in settings.modes, onClick = { onModeToggled(mode) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.dictation_settings_length_title),
                    description = stringResource(R.string.dictation_settings_length_description),
                ) {
                    DictationSettings.LENGTH_OPTIONS.forEach { length ->
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

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.settings_context_title),
                    description = stringResource(R.string.settings_context_description),
                ) {
                    KeyContext.entries.forEach { context ->
                        SettingsChip(context.displayName, selected = settings.context == context, onClick = { onContextSelected(context) })
                    }
                }

                HorizontalDivider()

                SettingsSwitch(
                    title = stringResource(R.string.settings_focus_title),
                    description = stringResource(R.string.settings_focus_description),
                    checked = settings.focusOnWeakSpots,
                    onCheckedChange = onFocusOnWeakSpotsChanged,
                )

                TextButton(
                    onClick = onRestoreDefaults,
                    enabled = settings != DictationSettings(),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.settings_restore_defaults))
                }

                HorizontalDivider()

                instrumentSetting()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DictationSettingsPreview() {
    EarTrainerTheme {
        DictationSettingsScreen(
            settings = DictationSettings(modes = Mode.entries.toSet(), length = 6),
            onModeToggled = {}, onLengthSelected = {}, onKeyChoiceSelected = {}, onContextSelected = {},
            onRestoreDefaults = {}, onFocusOnWeakSpotsChanged = {}, onNavigateUp = {},
        )
    }
}
