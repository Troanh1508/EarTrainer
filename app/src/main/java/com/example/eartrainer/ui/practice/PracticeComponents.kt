package com.example.eartrainer.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.eartrainer.R
import com.example.eartrainer.ui.theme.Stop

@Composable
fun PlaybackButton(
    isPlaying: Boolean,
    hasHeardPrompt: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier.heightIn(min = 64.dp)
    if (isPlaying) {
        FilledTonalButton(onClick = onStop, modifier = buttonModifier) {
            ButtonContent(Icons.Filled.Stop, stringResource(R.string.practice_stop))
        }
    } else {
        Button(onClick = onPlay, modifier = buttonModifier) {
            if (hasHeardPrompt) {
                ButtonContent(Icons.Filled.Refresh, stringResource(R.string.practice_replay))
            } else {
                ButtonContent(Icons.Filled.PlayArrow, stringResource(R.string.practice_play))
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize * 1.25f))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label, style = MaterialTheme.typography.titleMedium)
}

/** Text for one answer button: a main label and an optional short form, e.g. "Major third" / "M3". */
data class ChoiceLabel(val title: String, val subtitle: String? = null)

@Composable
fun <A> AnswerGrid(
    choices: List<A>,
    labelOf: @Composable (A) -> ChoiceLabel,
    appearanceOf: (A) -> ChoiceAppearance,
    onChoiceSelected: (A) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { choice ->
                    AnswerChoice(
                        label = labelOf(choice),
                        appearance = appearanceOf(choice),
                        onClick = { onChoiceSelected(choice) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class ChoiceColors(val container: Color, val content: Color, val border: BorderStroke)

@Composable
private fun AnswerChoice(
    label: ChoiceLabel,
    appearance: ChoiceAppearance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = when (appearance) {
        ChoiceAppearance.Available ->
            ChoiceColors(scheme.surfaceContainerLow, scheme.onSurface, BorderStroke(1.dp, scheme.outlineVariant))
        ChoiceAppearance.Disabled ->
            ChoiceColors(scheme.surfaceContainerLow, scheme.onSurface.copy(alpha = 0.38f), BorderStroke(1.dp, scheme.outlineVariant))
        ChoiceAppearance.SelectedCorrect ->
            ChoiceColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, BorderStroke(2.dp, scheme.tertiary))
        ChoiceAppearance.RevealedCorrect ->
            ChoiceColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, BorderStroke(1.dp, scheme.tertiary))
        ChoiceAppearance.SelectedIncorrect ->
            ChoiceColors(scheme.errorContainer, scheme.onErrorContainer, BorderStroke(2.dp, scheme.error))
        ChoiceAppearance.Dimmed ->
            ChoiceColors(scheme.surfaceContainerLow, scheme.onSurface.copy(alpha = 0.6f), BorderStroke(1.dp, scheme.outlineVariant))
    }
    val (icon, stateLabel) = when (appearance) {
        ChoiceAppearance.SelectedCorrect, ChoiceAppearance.RevealedCorrect ->
            Icons.Filled.Check to stringResource(R.string.practice_choice_correct)
        ChoiceAppearance.SelectedIncorrect ->
            Icons.Filled.Close to stringResource(R.string.practice_choice_incorrect)
        else -> null to null
    }

    Surface(
        onClick = onClick,
        enabled = appearance == ChoiceAppearance.Available,
        shape = RoundedCornerShape(16.dp),
        color = colors.container,
        contentColor = colors.content,
        border = colors.border,
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics { if (stateLabel != null) stateDescription = stateLabel },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label.title, style = MaterialTheme.typography.titleMedium)
                label.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.content.copy(alpha = colors.content.alpha * 0.8f),
                    )
                }
            }
            // Icons back up the colour change so the result never relies on colour alone.
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AnswerFeedback(
    isCorrect: Boolean,
    selectedLabel: String,
    /** What the right answer was, e.g. "Major third · 4 semitones". */
    correctSummary: String,
    notesAsPlayed: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (isCorrect) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (isCorrect) R.string.practice_feedback_correct else R.string.practice_feedback_incorrect),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
            }
            if (!isCorrect) {
                Text(
                    stringResource(R.string.practice_feedback_your_answer, selectedLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                correctSummary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                notesAsPlayed,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.practice_next), style = MaterialTheme.typography.titleMedium)
        }
    }
}
