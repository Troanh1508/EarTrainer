package com.example.eartrainer.ui.practice

import com.example.eartrainer.audio.AudioPlaybackException
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.exercise.ExercisePhase
import com.example.eartrainer.exercise.IntervalAttempt
import com.example.eartrainer.exercise.IntervalExerciseSettings
import com.example.eartrainer.exercise.IntervalQuestion
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.SessionScore
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import com.example.eartrainer.testing.FakePracticeHistory
import com.example.eartrainer.ui.intervals.IntervalPracticeViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    /** Playback that lasts until the test calls [finish], so every intermediate state is observable. */
    private class FakeAudioPlayer : AudioPlayer {
        val played = mutableListOf<ListeningPrompt>()
        var cancellations = 0
        var failNext = false
        var released = false
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

        override fun release() {
            released = true
        }
    }

    private val choices = listOf(Interval.MAJOR_THIRD, Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH)
    private val questions = List(6) { index ->
        IntervalQuestion(Pitch(60 + index), choices[index % 3], IntervalStyle.MELODIC_ASCENDING, choices)
    }

    private val settings = MutableStateFlow(IntervalExerciseSettings())
    private val requestedSettings = mutableListOf<IntervalExerciseSettings>()
    private lateinit var player: FakeAudioPlayer
    private lateinit var history: FakePracticeHistory
    private lateinit var viewModel: IntervalPracticeViewModel
    private var now = 0L
    private var sessionCounter = 0

    private val state get() = checkNotNull(viewModel.uiState.value)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        player = FakeAudioPlayer()
        history = FakePracticeHistory()
        viewModel = createViewModel(settings)
    }

    // Exercised through intervals, wired the same way as the app's IntervalPracticeFactory.
    private fun createViewModel(settingsFlow: Flow<IntervalExerciseSettings>): IntervalPracticeViewModel {
        val remaining = ArrayDeque(questions)
        return IntervalPracticeViewModel(
            settings = settingsFlow,
            questionSource = { requested ->
                requestedSettings += requested
                { remaining.removeFirst() }
            },
            recordAttempt = { state, sessionId -> IntervalAttempt.from(state, sessionId)?.let(history::record) },
            audioPlayer = player,
            clock = { now },
            newSessionId = { "session-${++sessionCounter}" },
        )
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
    fun staysLoadingUntilSettingsArrive() = runTest {
        val pendingSettings = MutableSharedFlow<IntervalExerciseSettings>()
        val loading = createViewModel(pendingSettings)
        advanceUntilIdle()
        assertNull(loading.uiState.value)
        loading.onPlayClick()
        loading.onAnswerSelected(Interval.MAJOR_THIRD)
        advanceUntilIdle()
        assertTrue(player.played.isEmpty())

        pendingSettings.emit(IntervalExerciseSettings())
        advanceUntilIdle()
        assertEquals(1, checkNotNull(loading.uiState.value).exercise.questionNumber)
    }

    @Test
    fun startsOnceSettingsLoad() = runTest {
        advanceUntilIdle()
        assertEquals(1, state.exercise.questionNumber)
        assertEquals(ExercisePhase.Unheard, state.exercise.phase)
        assertEquals(PlaybackStatus.Idle, state.playback)
        assertFalse(state.canAnswer)
        assertEquals(listOf(IntervalExerciseSettings()), requestedSettings)
    }

    @Test
    fun playingReflectsPlaybackAndUnlocksAnswers() = runTest {
        advanceUntilIdle()
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
        advanceUntilIdle()
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        assertEquals(SessionScore(), state.exercise.score)
        assertTrue(history.recorded.isEmpty())
    }

    @Test
    fun correctAnswerUpdatesScoreAndHistoryExactlyOnce() = runTest {
        advanceUntilIdle()
        now = 1_000
        playToEnd()
        now = 3_500
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        viewModel.onAnswerSelected(Interval.PERFECT_FIFTH)

        assertEquals(SessionScore(answered = 1, correct = 1), state.exercise.score)
        assertEquals(ChoiceAppearance.SelectedCorrect, state.appearanceOf(Interval.MAJOR_THIRD))
        assertEquals(
            listOf(
                IntervalAttempt(
                    sessionId = "session-1",
                    answeredAtMillis = 3_500,
                    interval = Interval.MAJOR_THIRD,
                    style = IntervalStyle.MELODIC_ASCENDING,
                    lowerPitch = Pitch(60),
                    answer = Interval.MAJOR_THIRD,
                    isCorrect = true,
                    playCount = 1,
                    responseMillis = 2_500,
                ),
            ),
            history.recorded,
        )
    }

    @Test
    fun incorrectAnswerRevealsTheCorrectOne() = runTest {
        advanceUntilIdle()
        playToEnd()
        viewModel.onAnswerSelected(Interval.PERFECT_FIFTH)

        assertEquals(SessionScore(answered = 1, correct = 0), state.exercise.score)
        assertEquals(ChoiceAppearance.SelectedIncorrect, state.appearanceOf(Interval.PERFECT_FIFTH))
        assertEquals(ChoiceAppearance.RevealedCorrect, state.appearanceOf(Interval.MAJOR_THIRD))
        assertEquals(ChoiceAppearance.Dimmed, state.appearanceOf(Interval.PERFECT_FOURTH))
        assertFalse(history.recorded.single().isCorrect)
    }

    @Test
    fun replayDoesNotAffectScoreQuestionOrHistory() = runTest {
        advanceUntilIdle()
        playToEnd()
        playToEnd()
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        val answered = state.exercise
        playToEnd()
        playToEnd()

        assertEquals(1, state.exercise.questionNumber)
        assertEquals(answered.phase, state.exercise.phase)
        assertEquals(SessionScore(answered = 1, correct = 1), state.exercise.score)
        assertEquals(List(4) { questions[0].prompt }, player.played)
        // Recorded once, with the two plays that happened before answering.
        assertEquals(2, history.recorded.single().playCount)
    }

    @Test
    fun replayWhilePlayingRestartsInsteadOfOverlapping() = runTest {
        advanceUntilIdle()
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
        advanceUntilIdle()
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
        advanceUntilIdle()
        playToEnd()
        viewModel.onNextClick()
        advanceUntilIdle()
        assertEquals(1, state.exercise.questionNumber)
        assertEquals(1, player.played.size)
    }

    @Test
    fun nextStopsCurrentAudioAndPlaysTheNewQuestion() = runTest {
        advanceUntilIdle()
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
        advanceUntilIdle()
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
        advanceUntilIdle()
        viewModel.onPlayClick()
        advanceUntilIdle()
        viewModel.onScreenHidden()
        advanceUntilIdle()

        assertEquals(1, player.cancellations)
        assertEquals(PlaybackStatus.Idle, state.playback)
    }

    @Test
    fun changedSettingsStartAFreshSession() = runTest {
        advanceUntilIdle()
        playToEnd()
        viewModel.onAnswerSelected(Interval.MAJOR_THIRD)
        viewModel.onNextClick()
        advanceUntilIdle()
        assertEquals(PlaybackStatus.Playing, state.playback)

        val harmonic = IntervalExerciseSettings(styles = setOf(IntervalStyle.HARMONIC))
        settings.value = harmonic
        advanceUntilIdle()

        assertEquals(1, player.cancellations)
        assertEquals(PlaybackStatus.Idle, state.playback)
        assertEquals(1, state.exercise.questionNumber)
        assertEquals(ExercisePhase.Unheard, state.exercise.phase)
        assertEquals(SessionScore(), state.exercise.score)
        assertEquals(listOf(IntervalExerciseSettings(), harmonic), requestedSettings)

        playToEnd()
        viewModel.onAnswerSelected(state.question.correctAnswer)
        assertEquals(listOf("session-1", "session-2"), history.recorded.map { it.sessionId })
    }

    @Test
    fun identicalSettingsEmissionsDoNotRestartTheSession() = runTest {
        advanceUntilIdle()
        playToEnd()
        settings.value = IntervalExerciseSettings()
        advanceUntilIdle()
        assertEquals(ExercisePhase.AwaitingAnswer, state.exercise.phase)
        assertEquals(1, requestedSettings.size)
    }
}
