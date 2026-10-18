package com.example.eartrainer.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.data.history.AnswerAccuracy
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.SessionSummary
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.ExerciseKind
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.music.Mode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** What the accuracy list shows; each exercise has its own kind of filter. */
sealed interface ProgressFilter {
    val exercise: ExerciseKind

    /** Interval accuracy for one playing style, or every style when [style] is null. */
    data class IntervalStyleFilter(val style: IntervalStyle?) : ProgressFilter {
        override val exercise get() = ExerciseKind.INTERVALS
    }

    /** Chord accuracy per quality or per inversion. */
    data class ChordTargetFilter(val target: ChordTarget) : ProgressFilter {
        override val exercise get() = ExerciseKind.CHORDS
    }

    /** Scale-degree accuracy in one mode, or every mode when [mode] is null. */
    data class ModeFilter(val mode: Mode?) : ProgressFilter {
        override val exercise get() = ExerciseKind.SCALE_DEGREES
    }

    /** Per-chord accuracy within progressions in one mode; numerals differ between modes. */
    data class ProgressionModeFilter(val mode: Mode) : ProgressFilter {
        override val exercise get() = ExerciseKind.PROGRESSIONS
    }

    /** Per-note accuracy in dictations in one mode; each mode has its own seven notes. */
    data class DictationModeFilter(val mode: Mode) : ProgressFilter {
        override val exercise get() = ExerciseKind.DICTATION
    }

    /** Accuracy per rhythm figure; rhythms have a single view. */
    data object RhythmFilter : ProgressFilter {
        override val exercise get() = ExerciseKind.RHYTHM
    }

    companion object {
        fun optionsFor(exercise: ExerciseKind): List<ProgressFilter> = when (exercise) {
            ExerciseKind.INTERVALS -> listOf(IntervalStyleFilter(null)) + IntervalStyle.entries.map(::IntervalStyleFilter)
            ExerciseKind.CHORDS -> ChordTarget.entries.map(::ChordTargetFilter)
            ExerciseKind.SCALE_DEGREES -> listOf(ModeFilter(null)) + Mode.entries.map(::ModeFilter)
            ExerciseKind.PROGRESSIONS -> Mode.entries.map(::ProgressionModeFilter)
            ExerciseKind.DICTATION -> Mode.entries.map(::DictationModeFilter)
            ExerciseKind.RHYTHM -> listOf(RhythmFilter)
        }
    }
}

/** One row of the accuracy list, ready to display. */
data class AccuracyItem(val label: String, val attempts: Int, val correct: Int) {
    val accuracy: Float? get() = if (attempts == 0) null else correct.toFloat() / attempts

    /** Practiced, but answered correctly less often than [WEAK_THRESHOLD]. */
    val needsWork: Boolean get() = accuracy?.let { it < WEAK_THRESHOLD } ?: false

    companion object {
        const val WEAK_THRESHOLD = 0.65f
    }
}

data class ProgressUiState(
    val filter: ProgressFilter,
    val accuracy: List<AccuracyItem>,
    val recentSessions: List<SessionSummary>,
    /** The selected exercise's latest sessions, oldest first, for the accuracy-over-time chart. */
    val trend: List<SessionSummary> = emptyList(),
) {
    val exercise: ExerciseKind get() = filter.exercise
    val filterOptions: List<ProgressFilter> get() = ProgressFilter.optionsFor(exercise)
    val totalAttempts: Int get() = accuracy.sumOf { it.attempts }
    val totalCorrect: Int get() = accuracy.sumOf { it.correct }
    val overallAccuracy: Float? get() = if (totalAttempts == 0) null else totalCorrect.toFloat() / totalAttempts

    /** Whether anything has ever been recorded, regardless of the current exercise or filter. */
    val hasHistory: Boolean get() = recentSessions.isNotEmpty()

    /** Practiced items from least to most accurate (more answers first on a tie), then unpracticed ones. */
    val weakestFirst: List<AccuracyItem>
        get() {
            val (practiced, unpracticed) = accuracy.partition { it.attempts > 0 }
            return practiced.sortedWith(compareBy<AccuracyItem> { it.accuracy }.thenByDescending { it.attempts }) + unpracticed
        }

    /**
     * Change in percentage points between the older and the newer half of [trend], each half
     * pooled over all its answers; null until there are enough sessions to compare.
     */
    val trendChange: Int?
        get() {
            if (trend.size < MIN_SESSIONS_FOR_CHANGE) return null
            val half = trend.size / 2
            fun List<SessionSummary>.pooled() = sumOf { it.correct }.toFloat() / sumOf { it.attempts }
            return ((trend.takeLast(half).pooled() - trend.take(half).pooled()) * 100).roundToInt()
        }

    companion object {
        const val MIN_SESSIONS_FOR_CHANGE = 4
    }
}

class ProgressViewModel(private val history: PracticeHistory) : ViewModel() {

    private val filter = MutableStateFlow<ProgressFilter>(ProgressFilter.IntervalStyleFilter(null))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState?> = filter
        .flatMapLatest { selected ->
            combine(
                accuracyFor(selected),
                history.recentSessions(RECENT_SESSION_LIMIT),
                history.recentSessions(TREND_SESSION_LIMIT, selected.exercise),
            ) { accuracy, sessions, trend ->
                ProgressUiState(selected, accuracy, sessions, trend = trend.asReversed())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onExerciseSelected(exercise: ExerciseKind) {
        if (filter.value.exercise != exercise) filter.value = ProgressFilter.optionsFor(exercise).first()
    }

    fun onFilterSelected(selected: ProgressFilter) {
        filter.value = selected
    }

    private fun accuracyFor(selected: ProgressFilter): Flow<List<AccuracyItem>> = when (selected) {
        is ProgressFilter.IntervalStyleFilter ->
            history.intervalAccuracy(selected.style).map { list -> list.map { it.toItem(it.item.displayName) } }
        is ProgressFilter.ChordTargetFilter ->
            history.chordAccuracy(selected.target).map { list -> list.map { it.toItem(it.item.displayName) } }
        is ProgressFilter.ModeFilter ->
            history.scaleDegreeAccuracy(selected.mode).map { list -> list.map { it.toItem("${it.item.label} · ${it.item.solfege}") } }
        is ProgressFilter.ProgressionModeFilter ->
            history.progressionAccuracy(selected.mode).map { list -> list.map { it.toItem(it.item.numeral) } }
        is ProgressFilter.DictationModeFilter ->
            history.dictationAccuracy(selected.mode).map { list -> list.map { it.toItem("${it.item.label} · ${it.item.solfege}") } }
        ProgressFilter.RhythmFilter ->
            history.rhythmAccuracy().map { list -> list.map { it.toItem(it.item.displayName.replaceFirstChar(Char::uppercase)) } }
    }

    private fun AnswerAccuracy<*>.toItem(label: String) = AccuracyItem(label, attempts, correct)

    companion object {
        const val RECENT_SESSION_LIMIT = 10
        const val TREND_SESSION_LIMIT = 12

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                ProgressViewModel(container.practiceHistory)
            }
        }
    }
}
