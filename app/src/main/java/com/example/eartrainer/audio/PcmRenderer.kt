package com.example.eartrainer.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** Turns scheduled notes into mono 16-bit PCM. Implementations decide what the notes sound like. */
fun interface PcmRenderer {
    fun render(schedule: NoteSchedule): ShortArray
}

/** Smooth 0 → 1 ramp for x in [0, 1]; used for click-free fades. */
internal fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

/** Converts a mix in the -1..1 range to 16-bit PCM, clamping anything that overshoots. */
internal fun DoubleArray.toPcm16(): ShortArray = ShortArray(size) { i ->
    (this[i] * Short.MAX_VALUE).roundToInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
}
