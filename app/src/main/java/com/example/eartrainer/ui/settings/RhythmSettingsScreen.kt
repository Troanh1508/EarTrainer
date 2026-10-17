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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.exercise.RhythmSettings
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.exercise.RhythmVocabulary
import com.example.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun RhythmSettingsRoute(viewModel: RhythmSettingsViewModel, onNavigateUp: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    RhythmSettingsScreen(
        settings = loaded,
        onVocabularySelected = viewModel::onVocabularySelected,
        onBarsSelected = viewModel::onBarsSelected,
        onTempoSelected = viewModel::onTempoSelected,
        onRestoreDefaults = viewModel::onRestoreDefaults,
        onFocusOnWeakSpotsChanged = viewModel::onFocusOnWeakSpotsChanged,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmSettingsScreen(
    settings: RhythmSettings,
    onVocabularySelected: (RhythmVocabulary) -> Unit,
    onBarsSelected: (Int) -> Unit,
    onTempoSelected: (RhythmTempo) -> Unit,
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
                    title = stringResource(R.string.rhythm_settings_vocabulary_title),
                    description = stringResource(R.string.rhythm_settings_vocabulary_description),
                ) {
                    RhythmVocabulary.entries.forEach { vocabulary ->
                        SettingsChip(vocabulary.displayName, selected = settings.vocabulary == vocabulary, onClick = { onVocabularySelected(vocabulary) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.rhythm_settings_bars_title),
                    description = stringResource(R.string.rhythm_settings_bars_description),
                ) {
                    RhythmSettings.BAR_OPTIONS.forEach { bars ->
                        SettingsChip(
                            pluralStringResource(R.plurals.rhythm_bars, bars, bars),
                            selected = settings.bars == bars,
                            onClick = { onBarsSelected(bars) },
                        )
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.rhythm_settings_tempo_title),
                    description = stringResource(R.string.rhythm_settings_tempo_description),
                ) {
                    RhythmTempo.entries.forEach { tempo ->
                        SettingsChip(
                            stringResource(R.string.rhythm_settings_tempo_option, tempo.displayName, tempo.bpm),
                            selected = settings.tempo == tempo,
                            onClick = { onTempoSelected(tempo) },
                        )
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
                    enabled = settings != RhythmSettings(),
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
private fun RhythmSettingsPreview() {
    EarTrainerTheme {
        RhythmSettingsScreen(
            settings = RhythmSettings(vocabulary = RhythmVocabulary.SYNCOPATION, bars = 2),
            onVocabularySelected = {}, onBarsSelected = {}, onTempoSelected = {}, onRestoreDefaults = {},
            onFocusOnWeakSpotsChanged = {}, onNavigateUp = {},
        )
    }
}
