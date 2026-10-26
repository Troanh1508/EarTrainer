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
import com.troanhmolo.eartrainer.exercise.ChordExerciseSettings
import com.troanhmolo.eartrainer.exercise.ChordStyle
import com.troanhmolo.eartrainer.exercise.ChordTarget
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun ChordSettingsRoute(viewModel: ChordSettingsViewModel, onNavigateUp: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    ChordSettingsScreen(
        settings = loaded,
        onTargetSelected = viewModel::onTargetSelected,
        onQualityToggled = viewModel::onQualityToggled,
        onSelectAllQualities = viewModel::onSelectAllQualities,
        onIncludeInversionsChanged = viewModel::onIncludeInversionsChanged,
        onStyleToggled = viewModel::onStyleToggled,
        onRestoreDefaults = viewModel::onRestoreDefaults,
        onFocusOnWeakSpotsChanged = viewModel::onFocusOnWeakSpotsChanged,
        onNavigateUp = onNavigateUp,
        instrumentSetting = { InstrumentSettingRoute() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordSettingsScreen(
    settings: ChordExerciseSettings,
    onTargetSelected: (ChordTarget) -> Unit,
    onQualityToggled: (ChordQuality) -> Unit,
    onSelectAllQualities: () -> Unit,
    onIncludeInversionsChanged: (Boolean) -> Unit,
    onStyleToggled: (ChordStyle) -> Unit,
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
                    title = stringResource(R.string.chord_settings_target_title),
                    description = stringResource(R.string.chord_settings_target_description),
                ) {
                    ChordTarget.entries.forEach { target ->
                        SettingsChip(target.displayName, selected = settings.target == target, onClick = { onTargetSelected(target) })
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.chord_settings_qualities_title),
                    description = stringResource(R.string.chord_settings_qualities_description, ChordExerciseSettings.MIN_QUALITIES),
                    action = {
                        TextButton(onClick = onSelectAllQualities, enabled = settings.qualities.size < ChordQuality.entries.size) {
                            Text(stringResource(R.string.settings_select_all))
                        }
                    },
                ) {
                    ChordQuality.entries.forEach { quality ->
                        SettingsChip(quality.displayName, selected = quality in settings.qualities, onClick = { onQualityToggled(quality) })
                    }
                }

                HorizontalDivider()

                InversionsToggle(settings, onIncludeInversionsChanged)

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.settings_styles_title),
                    description = stringResource(R.string.settings_styles_description),
                ) {
                    ChordStyle.entries.forEach { style ->
                        SettingsChip(style.displayName, selected = style in settings.styles, onClick = { onStyleToggled(style) })
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
                    enabled = settings != ChordExerciseSettings(),
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

@Composable
private fun InversionsToggle(settings: ChordExerciseSettings, onChanged: (Boolean) -> Unit) {
    // Naming inversions always uses them, so the switch only matters when naming qualities.
    val namingInversions = settings.target == ChordTarget.INVERSION
    SettingsSwitch(
        title = stringResource(R.string.chord_settings_inversions_title),
        description = stringResource(
            if (namingInversions) R.string.chord_settings_inversions_always else R.string.chord_settings_inversions_description,
        ),
        checked = namingInversions || settings.includeInversions,
        onCheckedChange = onChanged,
        enabled = !namingInversions,
    )
}

@Preview(showBackground = true)
@Composable
private fun ChordSettingsPreview() {
    EarTrainerTheme {
        ChordSettingsScreen(
            settings = ChordExerciseSettings(includeInversions = true, styles = setOf(ChordStyle.BLOCK, ChordStyle.ARPEGGIATED)),
            onTargetSelected = {}, onQualityToggled = {}, onSelectAllQualities = {}, onIncludeInversionsChanged = {},
            onStyleToggled = {}, onRestoreDefaults = {}, onFocusOnWeakSpotsChanged = {}, onNavigateUp = {},
        )
    }
}
