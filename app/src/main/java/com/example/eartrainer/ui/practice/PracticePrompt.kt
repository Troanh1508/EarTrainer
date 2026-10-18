package com.example.eartrainer.ui.practice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eartrainer.R
import com.example.eartrainer.ui.theme.Stop

/** Answers shown in the session strip; older ones scroll off the start. */
private const val STRIP_SLOTS = 20

/**
 * The question number, the score, and one segment per answer this session (right, wrong, the
 * current question, then empty slots), so how the session is going reads at a glance.
 */
@Composable
internal fun SessionStrip(state: PracticeUiState<*, *>, modifier: Modifier = Modifier) {
    val results = state.results
    val right = results.count { it }
    val description = if (results.isEmpty()) {
        stringResource(R.string.practice_score_empty)
    } else {
        pluralStringResource(R.plurals.practice_session_right, results.size, right, results.size)
    }
    // The current question takes a slot until it is answered, when it joins the results.
    val shown = results.takeLast(if (state.answer == null) STRIP_SLOTS - 1 else STRIP_SLOTS)
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error
    val current = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = shown.map { if (it) tertiary else error } + (if (state.answer == null) listOf(current) else emptyList())
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.practice_session_question, state.exercise.questionNumber),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                description,
                style = MaterialTheme.typography.labelLarge,
                color = if (right == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(STRIP_SLOTS) { index ->
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.getOrNull(index) ?: empty))
            }
        }
    }
}

/**
 * How the question is played (e.g. "Ascending melodic"), a large play button with a halo that
 * pulses while sound is playing, and a hint underneath.
 */
@Composable
internal fun PromptPlayer(
    state: PracticeUiState<*, *>,
    detail: String,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "halo scale",
    )
    val haloScale = if (state.isPlaying) pulse else 1f
    val (icon, label) = when {
        state.isPlaying -> Icons.Filled.Stop to stringResource(R.string.practice_stop)
        state.hasHeardPrompt -> Icons.Filled.Refresh to stringResource(R.string.practice_replay)
        else -> Icons.Filled.PlayArrow to stringResource(R.string.practice_play)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(detail, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().scale(haloScale).clip(CircleShape).background(primary.copy(alpha = 0.08f)))
            Box(Modifier.size(122.dp).scale(haloScale).clip(CircleShape).background(primary.copy(alpha = 0.14f)))
            Surface(
                onClick = if (state.isPlaying) onStop else onPlay,
                shape = CircleShape,
                color = primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(96.dp).semantics {
                    contentDescription = label
                    role = Role.Button
                },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp)) }
            }
        }
        val failed = state.playback == PlaybackStatus.Failed
        Text(
            when {
                failed -> stringResource(R.string.practice_playback_failed)
                state.isPlaying -> stringResource(R.string.practice_playing)
                !state.hasHeardPrompt -> stringResource(R.string.practice_listen_first)
                else -> stringResource(R.string.practice_hear_again)
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
