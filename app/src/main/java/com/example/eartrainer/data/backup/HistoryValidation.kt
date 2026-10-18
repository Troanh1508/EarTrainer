package com.example.eartrainer.data.backup

import com.example.eartrainer.data.history.ChordAttemptEntity
import com.example.eartrainer.data.history.DictationAttemptEntity
import com.example.eartrainer.data.history.IntervalAttemptEntity
import com.example.eartrainer.data.history.ProgressionAttemptEntity
import com.example.eartrainer.data.history.RhythmAttemptEntity
import com.example.eartrainer.data.history.ScaleDegreeAttemptEntity
import com.example.eartrainer.exercise.ChordStyle
import com.example.eartrainer.exercise.ChordTarget
import com.example.eartrainer.exercise.IntervalStyle
import com.example.eartrainer.exercise.KeyContext
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.music.ChordInversion
import com.example.eartrainer.music.ChordQuality
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.Mode
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.music.ScaleDegree

/*
 * Checks on rows read from a backup file before they are stored. Rows the app could not
 * interpret would otherwise count toward session totals while being ignored by the
 * statistics, so they are skipped instead.
 */

private val MIDI_RANGE = 0..127
private val STEP_RANGE = 1..7

private inline fun <reified E : Enum<E>> String.isNameOf() = enumValues<E>().any { it.name == this }

private inline fun <reified E : Enum<E>> String.isListOf() = isNotEmpty() && split(",").all { it.isNameOf<E>() }

private fun String.isStepList() = isNotEmpty() && split(",").all { it.toIntOrNull() in STEP_RANGE }

private fun isValidAnswer(sessionId: String, answeredAtMillis: Long, playCount: Int, responseMillis: Long) =
    sessionId.isNotBlank() && answeredAtMillis > 0 && playCount >= 0 && responseMillis >= 0

internal fun IntervalAttemptEntity.isValid() =
    isValidAnswer(sessionId, answeredAtMillis, playCount, responseMillis) &&
        interval.isNameOf<Interval>() && style.isNameOf<IntervalStyle>() && answer.isNameOf<Interval>() && lowerMidi in MIDI_RANGE

internal fun ChordAttemptEntity.isValid() =
    isValidAnswer(sessionId, answeredAtMillis, playCount, responseMillis) &&
        quality.isNameOf<ChordQuality>() && inversion.isNameOf<ChordInversion>() && style.isNameOf<ChordStyle>() &&
        bassMidi in MIDI_RANGE &&
        when (target) {
            ChordTarget.QUALITY.name -> answer.isNameOf<ChordQuality>()
            ChordTarget.INVERSION.name -> answer.isNameOf<ChordInversion>()
            else -> false
        }

internal fun ScaleDegreeAttemptEntity.isValid() =
    isValidAnswer(sessionId, answeredAtMillis, playCount, responseMillis) &&
        tonicMidi in MIDI_RANGE && mode.isNameOf<Mode>() && degree.isNameOf<ScaleDegree>() &&
        context.isNameOf<KeyContext>() && answer.isNameOf<ScaleDegree>()

internal fun ProgressionAttemptEntity.isValid() =
    isValidAnswer(sessionId, answeredAtMillis, playCount, responseMillis) &&
        tonicMidi in MIDI_RANGE && mode.isNameOf<Mode>() && progression.isStepList() && answer.isStepList()

internal fun DictationAttemptEntity.isValid() =
    isValidAnswer(sessionId, answeredAtMillis, playCount, responseMillis) &&
        tonicMidi in MIDI_RANGE && mode.isNameOf<Mode>() && melody.isListOf<ScaleDegree>() && answer.isListOf<ScaleDegree>()

internal fun RhythmAttemptEntity.isValid() =
    isValidAnswer(sessionId, answeredAtMillis, playCount, responseMillis) &&
        tempo.isNameOf<RhythmTempo>() && rhythm.isRhythm() && answer.isRhythm()

private fun String.isRhythm() = runCatching { Rhythm(split(",").map { RhythmCell.valueOf(it) }) }.isSuccess
