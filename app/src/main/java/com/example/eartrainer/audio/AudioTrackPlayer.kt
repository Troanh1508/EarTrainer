package com.example.eartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.example.eartrainer.music.ListeningPrompt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [AudioPlayer] backed by a static-mode [AudioTrack].
 *
 * Each prompt is rendered into one PCM buffer up front and handed to the track in a single
 * write. Note onsets and gaps are therefore fixed by sample positions in that buffer and played
 * out by the audio hardware clock, independent of the main thread, coroutine scheduling or
 * Compose recomposition.
 */
class AudioTrackPlayer(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val timing: PlaybackTiming = PlaybackTiming(),
    private val synthesizer: ToneSynthesizer = ToneSynthesizer(),
) : AudioPlayer {

    private class RenderedPrompt(val prompt: ListeningPrompt, val schedule: NoteSchedule, val pcm: ShortArray)

    private class ActivePlayback(val track: AudioTrack) {
        val finished = CompletableDeferred<Unit>()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var active: ActivePlayback? = null // guarded by lock
    private var released = false // guarded by lock

    // Replays of the same question reuse the rendered buffer instead of synthesising again.
    @Volatile
    private var lastRender: RenderedPrompt? = null

    override suspend fun play(prompt: ListeningPrompt) {
        // Assigned inside withContext rather than returned from it: if the caller is cancelled
        // while switching back, withContext discards its result, and the track would leak.
        var created: ActivePlayback? = null
        try {
            val rendered = withContext(Dispatchers.Default) {
                render(prompt).also { created = ActivePlayback(createLoadedTrack(it.pcm)) }
            }
            val playback = checkNotNull(created)
            begin(playback)
            playback.track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack) {
                        playback.finished.complete(Unit)
                    }

                    override fun onPeriodicNotification(track: AudioTrack) = Unit
                },
                mainHandler,
            )
            // The marker sits at the end of the last note, inside the buffer: some devices
            // never report a marker placed on the final frame of a static buffer.
            playback.track.notificationMarkerPosition = rendered.schedule.contentEndFrame
            playback.track.play()

            // The marker is the normal completion signal. The timeout only guards against
            // devices that fail to deliver it; it plays no part in note timing.
            val expectedMillis = (rendered.schedule.durationSeconds * 1000).toLong()
            withTimeoutOrNull(expectedMillis + COMPLETION_GRACE_MILLIS) { playback.finished.await() }
        } catch (e: IllegalStateException) {
            throw AudioPlaybackException("Audio output could not start", e)
        } finally {
            created?.let(::end)
        }
    }

    override fun release() {
        synchronized(lock) {
            released = true
            active?.let { silence(it) }
            active = null
        }
        lastRender = null
    }

    private fun render(prompt: ListeningPrompt): RenderedPrompt {
        lastRender?.takeIf { it.prompt == prompt }?.let { return it }
        val schedule = NoteSchedule.from(prompt, sampleRate, timing)
        return RenderedPrompt(prompt, schedule, synthesizer.render(schedule)).also { lastRender = it }
    }

    private fun createLoadedTrack(pcm: ShortArray): AudioTrack {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
                .build()
        } catch (e: UnsupportedOperationException) {
            throw AudioPlaybackException("Audio format is not supported on this device", e)
        } catch (e: IllegalArgumentException) {
            throw AudioPlaybackException("Audio track configuration was rejected", e)
        }

        val written = track.write(pcm, 0, pcm.size)
        if (track.state != AudioTrack.STATE_INITIALIZED || written != pcm.size) {
            track.release()
            throw AudioPlaybackException("Audio track could not be initialised (write result $written)")
        }
        return track
    }

    /** Registers [playback] as the only sounding prompt, silencing whatever played before. */
    private fun begin(playback: ActivePlayback) {
        synchronized(lock) {
            if (released) throw AudioPlaybackException("Player has been released")
            active?.let { silence(it) }
            active = playback
        }
    }

    /** Runs on completion, cancellation and failure alike; each track is released exactly once by its owner. */
    private fun end(playback: ActivePlayback) {
        synchronized(lock) {
            if (active === playback) active = null
        }
        silence(playback)
        playback.track.setPlaybackPositionUpdateListener(null)
        playback.track.release()
    }

    private fun silence(playback: ActivePlayback) {
        try {
            if (playback.track.playState != AudioTrack.PLAYSTATE_STOPPED) playback.track.stop()
        } catch (_: IllegalStateException) {
            // Already stopped or never started; nothing left to silence.
        }
        playback.finished.complete(Unit)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        private const val COMPLETION_GRACE_MILLIS = 1_000L
    }
}
