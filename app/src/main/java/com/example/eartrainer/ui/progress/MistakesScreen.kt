package com.example.eartrainer.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.eartrainer.R
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.ui.theme.EarTrainerTheme
import com.example.eartrainer.ui.theme.weakSpot
import kotlin.math.roundToInt

/** One slice of the answers pie and its legend row. */
private data class PieSlice(val label: String, val count: Int, val color: Color, val isRight: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakesScreen(
    state: MistakesUiState?,
    onItemSelected: (String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.mistakes_title))
                        if (state != null) {
                            Text(
                                stringResource(R.string.mistakes_scope, exerciseName(state.exercise), filterLabel(state.filter)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state == null) {
            Surface(Modifier.padding(innerPadding).fillMaxSize()) {}
            return@Scaffold
        }
        val cardModifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp)
        LazyColumn(
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding() + 8.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { AnswersCard(state, onItemSelected, cardModifier) }
            item { PairsCard(state, cardModifier) }
        }
    }
}

@Composable
private fun MistakesCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun AnswersCard(state: MistakesUiState, onItemSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val slices = pieSlices(state)
    val total = slices.sumOf { it.count }
    val pieDescription = stringResource(
        R.string.mistakes_pie_description,
        state.selected.label,
        slices.joinToString(", ") { "${it.label} ${percentOf(it.count, total)}" },
    )
    MistakesCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(whenLabel(state.exercise)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ItemPicker(state, onItemSelected)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnswersPie(slices, total, pieDescription)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.mistakes_you_answered),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            slices.forEach { LegendRow(it, total) }
        }
        MistakeTip(state)
    }
}

@Composable
private fun whenLabel(exercise: ExerciseKind) = when (exercise) {
    ExerciseKind.INTERVALS -> R.string.mistakes_when_interval
    ExerciseKind.CHORDS -> R.string.mistakes_when_answer
    ExerciseKind.SCALE_DEGREES, ExerciseKind.DICTATION -> R.string.mistakes_when_note
    ExerciseKind.PROGRESSIONS -> R.string.mistakes_when_chord
    ExerciseKind.RHYTHM -> R.string.mistakes_when_figure
}

/** A button naming the selected item that opens a menu of every practiced item, weakest first. */
@Composable
private fun ItemPicker(state: MistakesUiState, onItemSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(state.selected.label, style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.mistakes_choose_item))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mistakes_item_option, item.label, percent(item.accuracy ?: 0f))) },
                    onClick = {
                        expanded = false
                        onItemSelected(item.label)
                    },
                )
            }
        }
    }
}

/**
 * The right answer, the two most common wrong answers and everything else together, so the pie
 * stays readable however many different answers were given.
 */
@Composable
private fun pieSlices(state: MistakesUiState): List<PieSlice> {
    val weak = MaterialTheme.colorScheme.weakSpot
    val wrongColors = listOf(weak, weak.copy(alpha = 0.5f))
    val somethingElse = stringResource(R.string.mistakes_something_else)
    val right = state.answers.filter { it.isRight }.map {
        PieSlice(stringResource(R.string.mistakes_right_answer, state.selected.label), it.count, MaterialTheme.colorScheme.primary, isRight = true)
    }
    val wrong = state.answers.filter { !it.isRight }
    val named = wrong.take(wrongColors.size).mapIndexed { index, share ->
        PieSlice(share.label ?: somethingElse, share.count, wrongColors[index], isRight = false)
    }
    val rest = wrong.drop(wrongColors.size)
    val other = if (rest.isEmpty()) {
        emptyList()
    } else {
        listOf(PieSlice(stringResource(R.string.mistakes_other), rest.sumOf { it.count }, MaterialTheme.colorScheme.outline, isRight = false))
    }
    return right + named + other
}

@Composable
private fun AnswersPie(slices: List<PieSlice>, total: Int, description: String) {
    val gap = MaterialTheme.colorScheme.surfaceContainer
    Box(Modifier.size(200.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 40.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.count / total
                drawArc(slice.color, start, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                // A thin line in the card's color keeps neighboring slices apart.
                if (slices.size > 1) {
                    drawArc(gap, start - 0.6f, 1.2f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                pluralStringResource(R.plurals.mistakes_times_heard, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(slice: PieSlice, total: Int) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(slice.color))
        Text(
            slice.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (slice.isRight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.mistakes_count, slice.count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            percentOf(slice.count, total),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

@Composable
private fun MistakeTip(state: MistakesUiState) {
    val mistake = state.mostCommonMistake
    val noMistakes = mistake == null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (noMistakes) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (noMistakes) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                mistake == null -> stringResource(R.string.mistakes_none)
                mistake.label == null -> stringResource(R.string.mistakes_tip_something_else)
                else -> stringResource(R.string.mistakes_tip, mistake.label)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PairsCard(state: MistakesUiState, modifier: Modifier = Modifier) {
    val most = state.confusedPairs.maxOfOrNull { it.count } ?: 0
    MistakesCard(modifier) {
        Text(stringResource(R.string.mistakes_pairs_title), style = MaterialTheme.typography.titleMedium)
        if (state.confusedPairs.isEmpty()) {
            Text(
                stringResource(R.string.mistakes_pairs_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.confusedPairs.forEach { pair ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.mistakes_pair, pair.first, pair.second),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(pair.count.toFloat() / most).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.weakSpot),
                        )
                    }
                    Text(
                        pluralStringResource(R.plurals.mistakes_pair_count, pair.count, pair.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun percentOf(count: Int, total: Int) = "${(count * 100f / total).roundToInt()}%"

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun MistakesPreview() {
    val items = listOf(
        AccuracyItem("Minor sixth", 23, 12),
        AccuracyItem("Tritone", 19, 11),
        AccuracyItem("Major sixth", 25, 18),
    )
    EarTrainerTheme {
        MistakesScreen(
            state = MistakesUiState(
                filter = ProgressFilter.IntervalStyleFilter(null),
                items = items,
                selected = items.first(),
                answers = listOf(
                    AnswerShare("Minor sixth", 12, isRight = true),
                    AnswerShare("Major sixth", 6, isRight = false),
                    AnswerShare("Perfect fifth", 3, isRight = false),
                    AnswerShare("Octave", 1, isRight = false),
                    AnswerShare("Tritone", 1, isRight = false),
                ),
                confusedPairs = listOf(
                    ConfusedPair("Minor sixth", "Major sixth", 11),
                    ConfusedPair("Tritone", "Perfect fourth", 8),
                ),
            ),
            onItemSelected = {},
            onNavigateUp = {},
        )
    }
}
