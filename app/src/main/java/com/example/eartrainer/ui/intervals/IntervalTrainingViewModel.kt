package com.example.eartrainer.ui.intervals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.audio.AudioTrackPlayer
import com.example.eartrainer.exercise.ExerciseEngine
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.exercise.IntervalQuestionGenerator
import com.example.eartrainer.music.Interval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IntervalTrainingViewModel(
    private val engine: ExerciseEngine<Interval, IntervalQuestion>,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntervalTrainingUiState(exercise = engine.start()))
    val uiState: StateFlow<IntervalTrainingUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    /** Plays the current question's prompt. Used for both the first listen and replays. */
    fun onPlayClick() {
        val previous = playbackJob
        playbackJob = viewModelScope.launch {
            // Wait for the previous playback to finish releasing its audio before starting.
            previous?.cancelAndJoin()
            val exercise = _uiState.value.exercise
            val questionNumber = exercise.questionNumber
            _uiState.update {
                it.copy(playback = PlaybackStatus.Playing, exercise = it.exercise.markPromptHeard())
            }
            val outcome = try {
                audioPlayer.play(exercise.question.prompt)
                PlaybackStatus.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaybackStatus.Failed
            }
            // Ignore results that arrive after the session has moved on to another question.
            _uiState.update { if (it.exercise.questionNumber == questionNumber) it.copy(playback = outcome) else it }
        }
    }

    fun onStopClick() = stopPlayback()

    fun onAnswerSelected(answer: Interval) {
        _uiState.update { it.copy(exercise = it.exercise.submitAnswer(answer)) }
    }

    fun onNextClick() {
        if (!_uiState.value.canAdvance) return
        stopPlayback()
        _uiState.update { it.copy(exercise = engine.advance(it.exercise)) }
        onPlayClick()
    }

    /** Called when the screen leaves the foreground so audio never keeps playing in the background. */
    fun onScreenHidden() = stopPlayback()

    private fun stopPlayback() {
        // Keep the job reference so the next play can join it and wait for its cleanup.
        playbackJob?.cancel()
        _uiState.update { if (it.isPlaying) it.copy(playback = PlaybackStatus.Idle) else it }
    }

    override fun onCleared() {
        playbackJob?.cancel()
        audioPlayer.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val generator = IntervalQuestionGenerator()
                IntervalTrainingViewModel(
                    engine = ExerciseEngine(generator::generate),
                    audioPlayer = AudioTrackPlayer(),
                )
            }
        }
    }
}
