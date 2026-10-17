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
import com.example.eartrainer.exercise.DegreeSet
import com.example.eartrainer.exercise.KeyChoice
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.ScaleDegreeSettings
import com.example.eartrainer.music.Mode
import com.example.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun ScaleDegreeSettingsRoute(viewModel: ScaleDegreeSettingsViewModel, onNavigateUp: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    ScaleDegreeSettingsScreen(
        settings = loaded,
        onModeToggled = viewModel::onModeToggled,
        onDegreeSetSelected = viewModel::onDegreeSetSelected,
        onKeyChoiceSelected = viewModel::onKeyChoiceSelected,
        onContextSelected = viewModel::onContextSelected,
        onRestoreDefaults = viewModel::onRestoreDefaults,
        onFocusOnWeakSpotsChanged = viewModel::onFocusOnWeakSpotsChanged,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleDegreeSettingsScreen(
    settings: ScaleDegreeSettings,
    onModeToggled: (Mode) -> Unit,
    onDegreeSetSelected: (DegreeSet) -> Unit,
    onKeyChoiceSelected: (KeyChoice) -> Unit,
    onContextSelected: (KeyContext) -> Unit,
    onRestoreDefaults: () -> Unit,
    onFocusOnWeakSpotsChanged: (Boolean) -> Unit,
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
                    title = stringResource(R.string.settings_modes_title),
                    description = stringResource(R.string.settings_modes_description),
                ) {
                    Mode.entries.forEach { mode ->
                        SettingsChip(mode.displayName, selected = mode in settings.modes, onClick = { onModeToggled(mode) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.degree_settings_degrees_title),
                    description = stringResource(R.string.degree_settings_degrees_description),
                ) {
                    DegreeSet.entries.forEach { set ->
                        SettingsChip(set.displayName, selected = settings.degreeSet == set, onClick = { onDegreeSetSelected(set) })
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
                    title = stringResource(R.string.degree_settings_context_title),
                    description = stringResource(R.string.degree_settings_context_description),
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
                    enabled = settings != ScaleDegreeSettings(),
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
private fun ScaleDegreeSettingsPreview() {
    EarTrainerTheme {
        ScaleDegreeSettingsScreen(
            settings = ScaleDegreeSettings(modes = Mode.entries.toSet(), degreeSet = DegreeSet.CHROMATIC),
            onModeToggled = {}, onDegreeSetSelected = {}, onKeyChoiceSelected = {}, onContextSelected = {},
            onRestoreDefaults = {}, onFocusOnWeakSpotsChanged = {}, onNavigateUp = {},
        )
    }
}
