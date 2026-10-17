package com.example.eartrainer.ui.intervals

import com.example.eartrainer.audio.AudioPlaybackException
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.exercise.ExerciseEngine
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntervalTrainingViewModelTest {

    /** Playback that lasts until the test calls [finish], so every intermediate state is observable. */
    private class FakeAudioPlayer : AudioPlayer {
        val played = mutableListOf<ListeningPrompt>()
        var cancellations = 0
        var failNext = false
        private var pending: CompletableDeferred<Unit>? = null

        override suspend fun play(prompt: ListeningPrompt) {
            played += prompt
            if (failNext) {
                failNext = false
                throw AudioPlaybackException("No audio output")
            }
            val done = CompletableDeferred<Unit>().also { pending = it }
            try {
                done.await()
            } catch (e: CancellationException) {
                cancellations++
                throw e
            }
        }

        fun finish() {
            pending?.complete(Unit)
        }

        override fun release() = Unit
    }

    private val choices = listOf(Interval.MAJOR_THIRD, Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH)
    private val questions = listOf(
        IntervalQuestion(Pitch(60), Interval.MAJOR_THIRD, IntervalStyle.MELODIC_ASCENDING, choices),
        IntervalQuestion(Pitch(62), Interval.PERFECT_FIFTH, IntervalStyle.MELODIC_ASCENDING, choices),
        IntervalQuestion(Pitch(64), Interval.PERFECT_FOURTH, IntervalStyle.MELODIC_ASCENDING, choices),
    )

    private lateinit var player: FakeAudioPlayer
    private lateinit var viewModel: IntervalTrainingViewModel

    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val remaining = ArrayDeque(questions)
        player = FakeAudioPlayer()
        viewModel = IntervalTrainingViewModel(ExerciseEngine { remaining.removeFirst() }, player)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.playToEnd() {
        viewModel.onPlayClick()
        advanceUntilIdle()
        player.finish()
        advanceUntilIdle()
    }

    @Test
    fun startsUnheardAndIdle() {
        assertEquals(1, state.exercise.questionNumber)
        assertEquals(ExercisePhase.Unheard, state.exercise.phase)
        assertEquals(PlaybackStatus.Idle, state.playback)
        assertFalse(state.canAnswer)
    }

    @Test
    fun playingReflectsPlaybackAndUnlocksAnswers() = runTest {
        viewModel.onPlayClick()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.Playing, state.playback)
        assertTrue(state.canAnswer)
        assertEquals(listOf(questions[0].prompt), player.played)

        player.finish()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.Idle, state.playback)
        assertTrue(state.canAnswer)
    }

    @Test
    fun answersBeforeListeningAreIgnored() = runTest {
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        assertEquals(SessionScore(), state.exercise.score)
        assertEquals(ExercisePhase.Unheard, state.exercise.phase)
    }

    @Test
    fun correctAnswerUpdatesScoreExactlyOnce() = runTest {
        playToEnd()
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        viewModel.onAnswerSelected(Interval.PERFECT_FIFTH)

        assertEquals(SessionScore(answered = 1, correct = 1), state.exercise.score)
        assertEquals(ExercisePhase.Answered(Interval.MAJOR_THIRD, isCorrect = true), state.exercise.phase)
        assertEquals(ChoiceAppearance.SelectedCorrect, state.appearanceOf(Interval.MAJOR_THIRD))
    }

    @Test
    fun incorrectAnswerRevealsTheCorrectOne() = runTest {
        playToEnd()
        viewModel.onAnswerSelected(Interval.PERFECT_FIFTH)

        assertEquals(SessionScore(answered = 1, correct = 0), state.exercise.score)
        assertEquals(ChoiceAppearance.SelectedIncorrect, state.appearanceOf(Interval.PERFECT_FIFTH))
        assertEquals(ChoiceAppearance.RevealedCorrect, state.appearanceOf(Interval.MAJOR_THIRD))
        assertEquals(ChoiceAppearance.Dimmed, state.appearanceOf(Interval.PERFECT_FOURTH))
    }

    @Test
    fun replayDoesNotAffectScoreOrQuestion() = runTest {
        playToEnd()
        playToEnd()
        val beforeAnswer = state.exercise
        assertEquals(ExercisePhase.AwaitingAnswer, beforeAnswer.phase)

        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        val answered = state.exercise
        playToEnd()
        playToEnd()

        assertEquals(answered, state.exercise)
        assertEquals(1, state.exercise.questionNumber)
        assertEquals(SessionScore(answered = 1, correct = 1), state.exercise.score)
        assertEquals(List(4) { questions[0].prompt }, player.played)
    }

    @Test
    fun replayWhilePlayingRestartsInsteadOfOverlapping() = runTest {
        viewModel.onPlayClick()
        advanceUntilIdle()
        viewModel.onPlayClick()
        advanceUntilIdle()

        assertEquals(1, player.cancellations)
        assertEquals(2, player.played.size)
        assertEquals(PlaybackStatus.Playing, state.playback)
    }

    @Test
    fun stopCancelsPlayback() = runTest {
        viewModel.onPlayClick()
        advanceUntilIdle()
        viewModel.onStopClick()
        advanceUntilIdle()

        assertEquals(1, player.cancellations)
        assertEquals(PlaybackStatus.Idle, state.playback)
        assertTrue(state.canAnswer)
    }

    @Test
    fun cannotAdvanceBeforeAnswering() = runTest {
        playToEnd()
        viewModel.onNextClick()
        advanceUntilIdle()
        assertEquals(1, state.exercise.questionNumber)
        assertEquals(1, player.played.size)
    }

    @Test
    fun nextStopsCurrentAudioAndPlaysTheNewQuestion() = runTest {
        viewModel.onPlayClick()
        advanceUntilIdle()
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        viewModel.onNextClick()
        advanceUntilIdle()

        assertEquals(1, player.cancellations)
        assertEquals(2, state.exercise.questionNumber)
        assertEquals(questions[1], state.question)
        assertEquals(questions[1].prompt, player.played.last())
        assertEquals(PlaybackStatus.Playing, state.playback)
        assertEquals(ExercisePhase.AwaitingAnswer, state.exercise.phase)
        assertEquals(SessionScore(answered = 1, correct = 1), state.exercise.score)

        player.finish()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.Idle, state.playback)
    }

    @Test
    fun playbackFailureIsReportedAndCanBeRetried() = runTest {
        player.failNext = true
        viewModel.onPlayClick()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.Failed, state.playback)

        playToEnd()
        assertEquals(PlaybackStatus.Idle, state.playback)
        assertEquals(1, state.exercise.questionNumber)
    }

    @Test
    fun hidingTheScreenStopsPlayback() = runTest {
        viewModel.onPlayClick()
        advanceUntilIdle()
        viewModel.onScreenHidden()
        advanceUntilIdle()

        assertEquals(1, player.cancellations)
        assertEquals(PlaybackStatus.Idle, state.playback)
    }
}
