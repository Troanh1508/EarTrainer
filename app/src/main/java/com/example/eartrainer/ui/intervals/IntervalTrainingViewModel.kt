package com.example.eartrainer.ui.intervals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.EarTrainerApplication
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.exercise.ExerciseEngine
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.exercise.IntervalQuestionGenerator
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class IntervalTrainingViewModel(
    settings: Flow<IntervalExerciseSettings>,
    private val history: PracticeHistory,
    private val audioPlayer: AudioPlayer,
    private val questionSource: (IntervalExerciseSettings) -> () -> IntervalQuestion =
        { IntervalQuestionGenerator(it)::generate },
    private val clock: () -> Long = System::currentTimeMillis,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    /** Null until the saved settings have loaded and the first session has started. */
    private val _uiState = MutableStateFlow<IntervalTrainingUiState?>(null)
    val uiState: StateFlow<IntervalTrainingUiState?> = _uiState.asStateFlow()

    private lateinit var engine: ExerciseEngine<Interval, IntervalQuestion>
    private var sessionId = ""
    private var playbackJob: Job? = null

    init {
        // A session is one run of questions under one set of settings, so changing settings
        // (e.g. from the settings screen) starts a fresh session with a fresh score.
        viewModelScope.launch {
            settings.distinctUntilChanged().collect(::startSession)
        }
    }

    /** Plays the current question's prompt. Used for both the first listen and replays. */
    fun onPlayClick() {
        if (_uiState.value == null) return
        val previous = playbackJob
        playbackJob = viewModelScope.launch {
            // Wait for the previous playback to finish releasing its audio before starting.
            previous?.cancelAndJoin()
            val state = _uiState.value ?: return@launch
            val session = sessionId
            val questionNumber = state.exercise.questionNumber
            _uiState.update {
                it?.copy(playback = PlaybackStatus.Playing, exercise = it.exercise.promptPlayed(clock()))
            }
            val outcome = try {
                audioPlayer.play(state.question.prompt)
                PlaybackStatus.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaybackStatus.Failed
            }
            // Ignore results that arrive after the session has moved on to another question.
            _uiState.update {
                if (it != null && session == sessionId && it.exercise.questionNumber == questionNumber) {
                    it.copy(playback = outcome)
                } else {
                    it
                }
            }
        }
    }

    fun onStopClick() = stopPlayback()

    fun onAnswerSelected(answer: Interval) {
        val current = _uiState.value ?: return
        val next = current.exercise.submitAnswer(answer, clock())
        // Invalid submissions (duplicate taps, not yet heard) return the same state.
        if (next === current.exercise) return
        _uiState.value = current.copy(exercise = next)
        IntervalAttempt.from(next, sessionId)?.let(history::record)
    }

    fun onNextClick() {
        val current = _uiState.value ?: return
        if (!current.canAdvance) return
        stopPlayback()
        _uiState.update { it?.copy(exercise = engine.advance(it.exercise)) }
        onPlayClick()
    }

    /** Called when the screen leaves the foreground so audio never keeps playing in the background. */
    fun onScreenHidden() = stopPlayback()

    private fun startSession(settings: IntervalExerciseSettings) {
        stopPlayback()
        engine = ExerciseEngine(questionSource(settings))
        sessionId = newSessionId()
        _uiState.value = IntervalTrainingUiState(exercise = engine.start())
    }

    private fun stopPlayback() {
        // Keep the job reference so the next play can join it and wait for its cleanup.
        playbackJob?.cancel()
        _uiState.update { if (it != null && it.isPlaying) it.copy(playback = PlaybackStatus.Idle) else it }
    }

    override fun onCleared() {
        playbackJob?.cancel()
        audioPlayer.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as EarTrainerApplication).container
                IntervalTrainingViewModel(
                    settings = container.intervalSettings.settings,
                    history = container.practiceHistory,
                    audioPlayer = container.createAudioPlayer(),
                )
            }
        }
    }
}
