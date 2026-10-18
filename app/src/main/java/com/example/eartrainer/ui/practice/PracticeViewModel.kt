package com.example.eartrainer.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.exercise.ExerciseEngine
import com.example.eartrainer.exercise.ExerciseQuestion
import com.example.eartrainer.exercise.ExerciseState
import com.example.eartrainer.music.ListeningPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Drives any listen-and-answer exercise: playback, answering, scoring, advancing and recording.
 * Each exercise type supplies how to make questions from its settings ([questionSource]) and
 * how to save an answered question ([recordAttempt]).
 *
 * @param A answer type, @param Q question type, @param S settings type.
 */
class PracticeViewModel<A, Q : ExerciseQuestion<A>, S>(
    settings: Flow<S>,
    private val questionSource: (S) -> () -> Q,
    private val recordAttempt: (state: ExerciseState<A, Q>, sessionId: String) -> Unit,
    private val audioPlayer: AudioPlayer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    /** Null until the saved settings have loaded and the first session has started. */
    private val _uiState = MutableStateFlow<PracticeUiState<A, Q>?>(null)
    val uiState: StateFlow<PracticeUiState<A, Q>?> = _uiState.asStateFlow()

    private val _currentSettings = MutableStateFlow<S?>(null)

    /** The settings the current session runs under; null until they have loaded. */
    val currentSettings: StateFlow<S?> = _currentSettings.asStateFlow()

    private lateinit var engine: ExerciseEngine<A, Q>
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

    fun onAnswerSelected(answer: A) {
        val current = _uiState.value ?: return
        val next = current.exercise.submitAnswer(answer, clock())
        // Invalid submissions (duplicate taps, not yet heard) return the same state.
        if (next === current.exercise) return
        _uiState.value = current.copy(exercise = next)
        recordAttempt(next, sessionId)
    }

    fun onNextClick() {
        val current = _uiState.value ?: return
        if (!current.canAdvance) return
        stopPlayback()
        _uiState.update { it?.copy(exercise = engine.advance(it.exercise)) }
        onPlayClick()
    }

    /**
     * Plays [prompts] one after another, e.g. reference examples before answering. Unlike
     * [onPlayClick] this does not count as hearing the question.
     */
    fun onPlayPrompts(prompts: List<ListeningPrompt>) {
        if (_uiState.value == null || prompts.isEmpty()) return
        val previous = playbackJob
        playbackJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            val session = sessionId
            _uiState.update { it?.copy(playback = PlaybackStatus.Playing) }
            val outcome = try {
                prompts.forEachIndexed { index, prompt ->
                    if (index > 0) delay(PROMPT_GAP_MILLIS)
                    audioPlayer.play(prompt)
                }
                PlaybackStatus.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaybackStatus.Failed
            }
            _uiState.update { if (it != null && session == sessionId) it.copy(playback = outcome) else it }
        }
    }

    /** Starts a fresh session with the same settings: new questions, a new score. */
    fun onRestartClick() {
        _currentSettings.value?.let(::startSession)
    }

    /** Called when the screen leaves the foreground so audio never keeps playing in the background. */
    fun onScreenHidden() = stopPlayback()

    private fun startSession(settings: S) {
        stopPlayback()
        _currentSettings.value = settings
        engine = ExerciseEngine(questionSource(settings))
        sessionId = newSessionId()
        _uiState.value = PracticeUiState(exercise = engine.start())
    }

    private fun stopPlayback() {
        // Keep the job reference so the next play can join it and wait for its cleanup.
        playbackJob?.cancel()
        _uiState.update { if (it != null && it.isPlaying) it.copy(playback = PlaybackStatus.Idle) else it }
    }

    private companion object {
        /** Pause between prompts played back to back, so each is heard as its own example. */
        const val PROMPT_GAP_MILLIS = 600L
    }

    override fun onCleared() {
        playbackJob?.cancel()
        audioPlayer.release()
    }
}
