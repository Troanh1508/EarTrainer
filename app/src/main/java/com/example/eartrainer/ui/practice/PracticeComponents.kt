package com.example.eartrainer.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
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
    /** Draws a choice instead of its text label, e.g. rhythm notation; the label is still read aloud. */
    graphicOf: (@Composable (A) -> Unit)? = null,
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
                        graphic = graphicOf?.let { draw -> { draw(choice) } },
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
    graphic: (@Composable () -> Unit)? = null,
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
    // After a wrong answer, make sure the right one is visible even if it is scrolled away or
    // hidden behind the feedback panel.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(appearance) {
        if (appearance == ChoiceAppearance.RevealedCorrect) bringIntoView.bringIntoView()
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
            .bringIntoViewRequester(bringIntoView)
            .heightIn(min = 72.dp)
            .semantics { if (stateLabel != null) stateDescription = stateLabel },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (graphic != null) {
                Box(Modifier.weight(1f).semantics { contentDescription = label.title }) { graphic() }
            } else {
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
            }
            // Icons back up the color change so the result never relies on color alone.
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AnswerFeedback(
    isCorrect: Boolean,
    /** What was chosen, or null to leave it out (e.g. when the choice is drawn, not written). */
    selectedLabel: String?,
    /** What the right answer was, e.g. "Major third · 4 semitones". */
    correctSummary: String,
    /** The sounded notes, or null to leave them out. */
    notesAsPlayed: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    /** The button text; "Next" unless the next step is something else, like a result. */
    nextLabel: String = stringResource(R.string.practice_next),
    /** From first hearing the question to answering, shown with the notes when known. */
    responseMillis: Long? = null,
) {
    val accent = if (isCorrect) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val badge = if (isCorrect) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val onBadge = if (isCorrect) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val details = listOfNotNull(
        notesAsPlayed,
        responseMillis?.let { stringResource(R.string.practice_response_time, it / 1000.0) },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Surface(shape = CircleShape, color = badge, contentColor = onBadge, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (isCorrect) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (isCorrect) R.string.practice_feedback_correct else R.string.practice_feedback_incorrect),
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
                Text(correctSummary, style = MaterialTheme.typography.titleMedium)
                if (!isCorrect && selectedLabel != null) {
                    Text(
                        stringResource(R.string.practice_feedback_your_answer, selectedLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                details?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(nextLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}
