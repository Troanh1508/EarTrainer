package com.example.eartrainer.audio

import com.example.eartrainer.music.ListeningPrompt

/**
 * Plays listening prompts. Implementations decide how pitches sound (sine synthesis today,
 * sampled instruments later); callers only describe what to play.
 */
interface AudioPlayer {
    /**
     * Plays [prompt] and suspends until it has finished sounding. Cancelling the calling
     * coroutine stops the sound immediately. Starting a new prompt silences any prompt that is
     * still playing, so two prompts never overlap.
     *
     * @throws AudioPlaybackException if the audio output could not be used.
     */
    suspend fun play(prompt: ListeningPrompt)

    /** Stops any playback and frees audio resources. The player must not be used afterwards. */
    fun release()
}

class AudioPlaybackException(message: String, cause: Throwable? = null) : Exception(message, cause)
