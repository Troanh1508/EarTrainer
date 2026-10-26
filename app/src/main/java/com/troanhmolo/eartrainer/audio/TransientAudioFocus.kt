package com.troanhmolo.eartrainer.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler

/**
 * Short-lived audio focus for a single prompt. Other apps pause while a prompt plays, and
 * [onLost] fires if a call or another player takes focus away.
 *
 * "Can duck" losses (e.g. a notification sound) are ignored: on API 26+ the system lowers our
 * volume automatically, and stopping the prompt for every notification would be more disruptive.
 */
internal class TransientAudioFocus(
    private val audioManager: AudioManager,
    attributes: AudioAttributes,
    handler: Handler,
    private val onLost: () -> Unit,
) {
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLost()
    }

    private val request: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(listener, handler)
                .build()
        } else {
            null
        }

    /** Returns false when focus is refused, for example during a phone call. */
    fun request(): Boolean {
        val result = if (request != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (request != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
    }
}
