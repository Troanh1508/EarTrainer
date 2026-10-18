package com.example.eartrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Interval
import com.example.eartrainer.ui.theme.EarTrainerTheme

@Composable
fun IntervalSettingsRoute(viewModel: IntervalSettingsViewModel, onNavigateUp: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: run {
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    IntervalSettingsScreen(
        settings = loaded,
        onIntervalToggled = viewModel::onIntervalToggled,
        onSelectAllIntervals = viewModel::onSelectAllIntervals,
        onStyleToggled = viewModel::onStyleToggled,
        onChoiceCountSelected = viewModel::onChoiceCountSelected,
        onRestoreDefaults = viewModel::onRestoreDefaults,
        onFocusOnWeakSpotsChanged = viewModel::onFocusOnWeakSpotsChanged,
        onNavigateUp = onNavigateUp,
        instrumentSetting = { InstrumentSettingRoute() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalSettingsScreen(
    settings: IntervalExerciseSettings,
    onIntervalToggled: (Interval) -> Unit,
    onSelectAllIntervals: () -> Unit,
    onStyleToggled: (IntervalStyle) -> Unit,
    onChoiceCountSelected: (Int) -> Unit,
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
                    title = stringResource(R.string.settings_intervals_title),
                    description = stringResource(R.string.settings_intervals_description, IntervalExerciseSettings.MIN_INTERVALS),
                    action = {
                        TextButton(onClick = onSelectAllIntervals, enabled = settings.intervals.size < Interval.entries.size) {
                            Text(stringResource(R.string.settings_select_all))
                        }
                    },
                ) {
                    Interval.entries.forEach { interval ->
                        SettingsChip(
                            label = interval.displayName,
                            selected = interval in settings.intervals,
                            onClick = { onIntervalToggled(interval) },
                        )
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.settings_styles_title),
                    description = stringResource(R.string.settings_styles_description),
                ) {
                    IntervalStyle.entries.forEach { style ->
                        SettingsChip(
                            label = style.displayName,
                            selected = style in settings.styles,
                            onClick = { onStyleToggled(style) },
                        )
                    }
                }

                HorizontalDivider()

                SettingsSection(
                    title = stringResource(R.string.settings_choices_title),
                    description = stringResource(R.string.settings_choices_description),
                ) {
                    IntervalExerciseSettings.CHOICE_COUNT_OPTIONS.forEach { count ->
                        SettingsChip(
                            label = if (count == Interval.entries.size) stringResource(R.string.settings_choices_all) else count.toString(),
                            selected = settings.choiceCount == count,
                            onClick = { onChoiceCountSelected(count) },
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
                    enabled = settings != IntervalExerciseSettings(),
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
private fun IntervalSettingsPreview() {
    EarTrainerTheme {
        IntervalSettingsScreen(
            settings = IntervalExerciseSettings(
                intervals = listOf(Interval.MINOR_THIRD, Interval.MAJOR_THIRD, Interval.PERFECT_FIFTH),
                styles = setOf(IntervalStyle.MELODIC_ASCENDING, IntervalStyle.HARMONIC),
            ),
            onIntervalToggled = {}, onSelectAllIntervals = {}, onStyleToggled = {},
            onChoiceCountSelected = {}, onRestoreDefaults = {}, onFocusOnWeakSpotsChanged = {}, onNavigateUp = {},
        )
    }
}
