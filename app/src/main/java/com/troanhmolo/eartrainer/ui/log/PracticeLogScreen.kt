package com.troanhmolo.eartrainer.ui.log

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.data.history.DayCount
import com.troanhmolo.eartrainer.data.history.SessionSummary
import com.troanhmolo.eartrainer.exercise.ExerciseKind
import com.troanhmolo.eartrainer.ui.progress.AccuracyItem
import com.troanhmolo.eartrainer.ui.progress.exerciseName
import com.troanhmolo.eartrainer.ui.progress.scoringLabels
import com.troanhmolo.eartrainer.ui.theme.EarTrainerTheme
import com.troanhmolo.eartrainer.ui.theme.weakSpot
import java.text.DateFormatSymbols

@Composable
fun PracticeLogRoute(viewModel: PracticeLogViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PracticeLogScreen(state, onNavigateUp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeLogScreen(state: PracticeLogUiState?, onNavigateUp: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_title)) },
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
            item { StreakCards(state, cardModifier) }
            item { CalendarCard(state, cardModifier) }
            item { WeekCard(state, cardModifier) }
            item { SessionsCard(state, cardModifier) }
        }
    }
}

@Composable
private fun LogCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun CardHeader(title: String, note: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StreakCards(state: PracticeLogUiState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakCard(
            days = state.currentStreak,
            label = stringResource(R.string.log_current_streak),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
        )
        StreakCard(
            days = state.longestStreak,
            label = stringResource(R.string.log_longest_streak),
            container = MaterialTheme.colorScheme.surfaceContainer,
            content = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StreakCard(days: Int, label: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = container, contentColor = content, modifier = modifier) {
        Column(Modifier.padding(18.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pluralStringResource(R.plurals.log_days, days, days),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Monday-first weekday names in the device language, from [DateFormatSymbols] (Sunday-first). */
@Composable
private fun weekdayNames(): List<String> = remember {
    val names = DateFormatSymbols.getInstance().shortWeekdays
    listOf(2, 3, 4, 5, 6, 7, 1).map { names[it] }
}

@Composable
private fun CalendarCard(state: PracticeLogUiState, modifier: Modifier = Modifier) {
    val weekdays = weekdayNames()
    val description = pluralStringResource(R.plurals.log_calendar_description, state.practicedDays, state.practicedDays, state.calendar.count { !it.isFuture })
    LogCard(modifier) {
        CardHeader(
            pluralStringResource(R.plurals.log_calendar_title, PracticeLogUiState.WEEKS, PracticeLogUiState.WEEKS),
            stringResource(R.string.log_answers_per_day),
        )
        Column(Modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekdays.forEach { name ->
                    Text(
                        name.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.calendar.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { day -> CalendarCell(day, Modifier.weight(1f)) }
                }
            }
        }
        CalendarLegend(Modifier.align(Alignment.End))
    }
}

/** Darker for more practice; the shades differ in lightness, not only in color. */
@Composable
private fun levelColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceContainerHighest
    else -> MaterialTheme.colorScheme.primary.copy(alpha = LEVEL_ALPHAS[level - 1])
}

private val LEVEL_ALPHAS = listOf(0.25f, 0.45f, 0.7f, 1f)

@Composable
private fun CalendarCell(day: CalendarDay, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val color = if (day.isFuture) Color.Transparent else levelColor(day.level)
    var cell = modifier.height(36.dp).clip(shape).background(color)
    if (day.isToday) cell = cell.border(2.dp, MaterialTheme.colorScheme.onSurface, shape)
    if (day.isFuture) cell = cell.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    Box(cell)
}

@Composable
private fun CalendarLegend(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(R.string.log_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..4).forEach { level -> Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(levelColor(level))) }
        Text(stringResource(R.string.log_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekCard(state: PracticeLogUiState, modifier: Modifier = Modifier) {
    val weekdays = weekdayNames()
    val most = state.week.maxOf { it.answers }.coerceAtLeast(1)
    val description = state.week.zip(weekdays).joinToString(", ") { (day, name) -> "$name ${day.answers}" }
    LogCard(modifier) {
        CardHeader(
            stringResource(R.string.log_this_week),
            pluralStringResource(R.plurals.log_answers, state.weekAnswers, state.weekAnswers),
        )
        Row(
            Modifier.fillMaxWidth().height(BAR_AREA).clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.week.forEach { day ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                ) {
                    if (!day.isFuture) {
                        Text("${day.answers}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    val color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    Box(
                        Modifier.fillMaxWidth()
                            .height(BAR_MAX_HEIGHT * (day.answers.toFloat() / most))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(color),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            weekdays.forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val BAR_AREA = 120.dp
private val BAR_MAX_HEIGHT = 92.dp

@Composable
private fun SessionsCard(state: PracticeLogUiState, modifier: Modifier = Modifier) {
    LogCard(modifier) {
        Text(stringResource(R.string.log_recent_sessions), style = MaterialTheme.typography.titleMedium)
        if (!state.hasHistory) {
            Text(
                stringResource(R.string.progress_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.sessions.forEach { SessionRow(it) }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val labels = scoringLabels(session.exercise)
    val share = if (session.items == 0) 0f else session.itemsCorrect.toFloat() / session.items
    val weak = share < AccuracyItem.WEAK_THRESHOLD
    val name = exerciseName(session.exercise)
    val whenText = remember(session.startedAtMillis) {
        DateUtils.getRelativeDateTimeString(context, session.startedAtMillis, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
    }
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                pluralStringResource(labels.sessionScore, session.items, session.itemsCorrect, session.items),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (weak) MaterialTheme.colorScheme.weakSpot else MaterialTheme.colorScheme.onSurface,
            )
            Box(Modifier.width(72.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(
                    Modifier.fillMaxWidth(share).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(if (weak) MaterialTheme.colorScheme.weakSpot else MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun PracticeLogPreview() {
    val today = 20_400L
    val days = listOf(1, 0, 2, 3, 0, 1, 1, 2, 4, 3, 2, 3).mapIndexed { index, level ->
        DayCount(today - 11 + index, listOf(0, 6, 18, 40, 70)[level])
    }
    EarTrainerTheme {
        PracticeLogScreen(
            state = PracticeLogUiState.from(
                days,
                listOf(
                    SessionSummary("a", ExerciseKind.INTERVALS, 1_760_000_000_000, 20, 17),
                    SessionSummary("b", ExerciseKind.PROGRESSIONS, 1_759_900_000_000, 10, 4, items = 30, itemsCorrect = 22),
                ),
                today,
            ),
            onNavigateUp = {},
        )
    }
}
