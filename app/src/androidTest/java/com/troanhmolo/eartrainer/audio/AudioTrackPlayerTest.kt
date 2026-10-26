package com.troanhmolo.eartrainer.audio

import androidx.test.platform.app.InstrumentationRegistry
import com.troanhmolo.eartrainer.music.ListeningPrompt
import com.troanhmolo.eartrainer.music.Pitch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Plays through the device's real audio output. */
class AudioTrackPlayerTest {

    private val player = AudioTrackPlayer(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun tearDown() = player.release()

    @Test
    fun stoppingMidPromptIsACancellationNotAFailure() = runBlocking {
        // Several seconds of notes, so the prompt is still sounding when it is stopped.
        val prompt = ListeningPrompt.sequential((60..72).map(::Pitch))
        val playing = async { player.play(prompt) }
        delay(1_000)
        playing.cancel()
        try {
            playing.await()
            fail("A stopped prompt should not finish normally")
        } catch (e: AudioPlaybackException) {
            fail("Stopping was reported as a playback failure: $e")
        } catch (e: CancellationException) {
            // Expected: the caller stopped it.
        }
        assertTrue(playing.isCancelled)
    }
}
