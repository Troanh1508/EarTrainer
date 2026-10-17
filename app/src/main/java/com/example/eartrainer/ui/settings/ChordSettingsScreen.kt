package com.example.eartrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ChordExerciseSettings
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.ui.theme.EarTrainerTheme

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
        onNavigateUp = onNavigateUp,
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

                TextButton(
                    onClick = onRestoreDefaults,
                    enabled = settings != ChordExerciseSettings(),
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.settings_restore_defaults))
                }
            }
        }
    }
}

@Composable
private fun InversionsToggle(settings: ChordExerciseSettings, onChanged: (Boolean) -> Unit) {
    // Naming inversions always uses them, so the switch only matters when naming qualities.
    val namingInversions = settings.target == ChordTarget.INVERSION
    val checked = namingInversions || settings.includeInversions
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = !namingInversions, role = Role.Switch, onValueChange = onChanged),
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.chord_settings_inversions_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    if (namingInversions) R.string.chord_settings_inversions_always else R.string.chord_settings_inversions_description,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row handles the toggle, so the switch itself is not separately clickable.
        Switch(checked = checked, onCheckedChange = null, enabled = !namingInversions)
    }
}

@Preview(showBackground = true)
@Composable
private fun ChordSettingsPreview() {
    EarTrainerTheme {
        ChordSettingsScreen(
            settings = ChordExerciseSettings(includeInversions = true, styles = setOf(ChordStyle.BLOCK, ChordStyle.ARPEGGIATED)),
            onTargetSelected = {}, onQualityToggled = {}, onSelectAllQualities = {}, onIncludeInversionsChanged = {},
            onStyleToggled = {}, onRestoreDefaults = {}, onNavigateUp = {},
        )
    }
}
