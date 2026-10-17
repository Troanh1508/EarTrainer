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

    companion object {
        fun optionsFor(exercise: ExerciseKind): List<ProgressFilter> = when (exercise) {
            ExerciseKind.INTERVALS -> listOf(IntervalStyleFilter(null)) + IntervalStyle.entries.map(::IntervalStyleFilter)
            ExerciseKind.CHORDS -> ChordTarget.entries.map(::ChordTargetFilter)
            ExerciseKind.SCALE_DEGREES -> listOf(ModeFilter(null)) + Mode.entries.map(::ModeFilter)
        }
    }
}

/** One row of the accuracy list, ready to display. */
data class AccuracyItem(val label: String, val attempts: Int, val correct: Int) {
    val accuracy: Float? get() = if (attempts == 0) null else correct.toFloat() / attempts
}

data class ProgressUiState(
    val filter: ProgressFilter,
    val accuracy: List<AccuracyItem>,
    val recentSessions: List<SessionSummary>,
) {
    val exercise: ExerciseKind get() = filter.exercise
    val filterOptions: List<ProgressFilter> get() = ProgressFilter.optionsFor(exercise)
    val totalAttempts: Int get() = accuracy.sumOf { it.attempts }
    val totalCorrect: Int get() = accuracy.sumOf { it.correct }
    val overallAccuracy: Float? get() = if (totalAttempts == 0) null else totalCorrect.toFloat() / totalAttempts

    /** Whether anything has ever been recorded, regardless of the current exercise or filter. */
    val hasHistory: Boolean get() = recentSessions.isNotEmpty()
}

class ProgressViewModel(private val history: PracticeHistory) : ViewModel() {

    private val filter = MutableStateFlow<ProgressFilter>(ProgressFilter.IntervalStyleFilter(null))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState?> = filter
        .flatMapLatest { selected ->
            combine(accuracyFor(selected), history.recentSessions(RECENT_SESSION_LIMIT)) { accuracy, sessions ->
                ProgressUiState(selected, accuracy, sessions)
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
    }

    private fun AnswerAccuracy<*>.toItem(label: String) = AccuracyItem(label, attempts, correct)

    companion object {
        const val RECENT_SESSION_LIMIT = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                ProgressViewModel(container.practiceHistory)
            }
        }
    }
}
