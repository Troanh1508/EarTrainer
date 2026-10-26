package com.troanhmolo.eartrainer.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.troanhmolo.eartrainer.ui.theme.TrendingDown
import com.troanhmolo.eartrainer.ui.theme.TrendingFlat
import com.troanhmolo.eartrainer.ui.theme.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.ui.progress.scoringLabels
import com.troanhmolo.eartrainer.ui.theme.EarTrainerTheme
import com.troanhmolo.eartrainer.ui.theme.weakSpot
import com.troanhmolo.eartrainer.ui.theme.weakSpotContainer
import com.troanhmolo.eartrainer.ui.theme.onWeakSpotContainer
import kotlin.math.abs

/**
 * The end of a session: the score against the recent average, time and replays, how each item
 * went, what was mistaken for what, and what to do next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSummaryScreen(
    summary: PracticeSummary,
    title: String,
    exercise: ExerciseKind,
    onRetryMissed: () -> Unit,
    onNewSession: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = { SummaryActions(summary.missedQuestions, onRetryMissed, onNewSession, onDone) },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                ScoreSection(summary, exercise)
                StatsRow(summary)
                if (summary.tallies.isNotEmpty()) TalliesCard(summary.tallies)
                MistakesCard(summary.mistakes)
            }
        }
    }
}

@Composable
private fun ScoreSection(summary: PracticeSummary, exercise: ExerciseKind) {
    val labels = scoringLabels(exercise)
    val caption = stringResource(labels.donutCaption)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.summary_complete),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        ScoreRing(
            accuracy = summary.accuracy,
            score = stringResource(R.string.summary_score, summary.itemsRight, summary.items),
            caption = stringResource(R.string.summary_percent, summary.percent, caption),
            description = pluralStringResource(labels.sessionScore, summary.items, summary.itemsRight, summary.items),
        )
        // Exercises scored item by item also say how many whole answers were right.
        labels.fullyRight?.let { fullyRight ->
            Text(
                pluralStringResource(fullyRight, summary.questions, summary.questionsRight, summary.questions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        summary.pointsAboveAverage?.let { AverageChip(it) }
    }
}

@Composable
private fun ScoreRing(accuracy: Float, score: String, caption: String, description: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    val thickness = 16.dp
    Box(Modifier.size(180.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (accuracy > 0f) {
                drawArc(
                    fill, startAngle = -90f, sweepAngle = 360f * accuracy, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            Text(
                caption,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
    }
}

@Composable
private fun AverageChip(points: Int) {
    val (container, content) = when {
        points > 0 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        points < 0 -> MaterialTheme.colorScheme.weakSpotContainer to MaterialTheme.colorScheme.onWeakSpotContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        points > 0 -> Icons.Filled.TrendingUp
        points < 0 -> Icons.Filled.TrendingDown
        else -> Icons.Filled.TrendingFlat
    }
    val text = when {
        points > 0 -> pluralStringResource(R.plurals.summary_above_average, points, points)
        points < 0 -> pluralStringResource(R.plurals.summary_below_average, abs(points), abs(points))
        else -> stringResource(R.string.summary_level_with_average)
    }
    Surface(shape = RoundedCornerShape(14.dp), color = container, contentColor = content) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatsRow(summary: PracticeSummary) {
    val seconds = summary.durationMillis / 1000
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
            value = stringResource(R.string.summary_duration, seconds / 60, seconds % 60),
            label = stringResource(R.string.summary_duration_label),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = stringResource(R.string.summary_seconds, summary.averageResponseMillis / 1000.0),
            label = stringResource(R.string.summary_per_answer),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = summary.replays.toString(),
            label = pluralStringResource(R.plurals.summary_replays, summary.replays),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.semantics(mergeDescendants = true) {}.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryCard(color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = color, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Right and missed counts per item, on one scale so items that came up more have longer bars. */
@Composable
private fun TalliesCard(tallies: List<ItemTally>) {
    val right = MaterialTheme.colorScheme.primary
    val missed = MaterialTheme.colorScheme.weakSpot
    val most = tallies.maxOf { it.total }.toFloat()
    SummaryCard(MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.summary_this_session),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clearAndSetSemantics {}) {
                LegendKey(right, stringResource(R.string.summary_right))
                LegendKey(missed, stringResource(R.string.summary_missed))
            }
        }
        tallies.forEach { tally ->
            val description = pluralStringResource(R.plurals.summary_tally_description, tally.total, tally.label, tally.right, tally.total)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Text(tally.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(112.dp))
                Row(Modifier.weight(1f).height(14.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (tally.right > 0) Bar(right, tally.right.toFloat())
                    if (tally.missed > 0) Bar(missed, tally.missed.toFloat())
                    if (tally.total < most) Spacer(Modifier.weight(most - tally.total))
                }
                Text(
                    stringResource(R.string.summary_score, tally.right, tally.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

/** Weights share the row out by count, so the busiest item fills it and every bar is on one scale. */
@Composable
private fun RowScope.Bar(color: Color, count: Float) {
    Box(Modifier.weight(count).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MistakesCard(mistakes: List<SessionMistake>) {
    if (mistakes.isEmpty()) {
        SummaryCard(MaterialTheme.colorScheme.tertiaryContainer) {
            Text(
                stringResource(R.string.summary_no_mistakes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        return
    }
    SummaryCard(MaterialTheme.colorScheme.weakSpotContainer) {
        Text(
            stringResource(R.string.summary_you_missed),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onWeakSpotContainer,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            mistakes.forEach { mistake ->
                val text = mistake.chosenLabel?.let { stringResource(R.string.summary_mistake, mistake.label, it) } ?: mistake.label
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(
                        if (mistake.count > 1) stringResource(R.string.summary_mistake_times, text, mistake.count) else text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryActions(missedQuestions: Int, onRetryMissed: () -> Unit, onNewSession: () -> Unit, onDone: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val buttonModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            if (missedQuestions > 0) {
                Button(onClick = onRetryMissed, modifier = buttonModifier) {
                    Text(pluralStringResource(R.plurals.summary_retry_missed, missedQuestions, missedQuestions), style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onNewSession, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.summary_new_session), style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                Button(onClick = onNewSession, modifier = buttonModifier) {
                    Text(stringResource(R.string.summary_new_session), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onDone, modifier = buttonModifier) {
                    Text(stringResource(R.string.summary_done), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun PracticeSummaryPreview() {
    EarTrainerTheme {
        PracticeSummaryScreen(
            summary = PracticeSummary(
                questions = 20, questionsRight = 16, items = 20, itemsRight = 16,
                durationMillis = 400_000, averageResponseMillis = 2_100, replays = 9,
                tallies = listOf(
                    ItemTally("Minor sixth", 1, 2), ItemTally("Tritone", 2, 1), ItemTally("Major seventh", 2, 1),
                    ItemTally("Major third", 4, 0), ItemTally("Perfect fifth", 4, 0), ItemTally("Octave", 3, 0),
                ),
                mistakes = listOf(
                    SessionMistake("Minor sixth", "Major sixth", 1), SessionMistake("Tritone", "Perfect fourth", 1),
                    SessionMistake("Minor sixth", "Perfect fifth", 1), SessionMistake("Major seventh", "Octave", 1),
                ),
                pastAccuracy = 0.79f,
            ),
            title = "Interval Training",
            exercise = ExerciseKind.INTERVALS,
            onRetryMissed = {}, onNewSession = {}, onDone = {},
        )
    }
}
