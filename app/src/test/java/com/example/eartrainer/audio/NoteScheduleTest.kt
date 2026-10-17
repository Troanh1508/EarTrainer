package com.example.eartrainer.audio

import com.example.eartrainer.music.ListeningPrompt
import com.example.eartrainer.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteScheduleTest {

    // 1 kHz makes one frame equal one millisecond, so expectations read directly as timings.
    private val sampleRate = 1_000
    private val timing = PlaybackTiming(noteSeconds = 0.9, chordSeconds = 1.6, gapSeconds = 0.15, leadInSeconds = 0.03, tailSeconds = 0.1)

    @Test
    fun sequentialNotesAreSeparatedByTheGap() {
        val schedule = NoteSchedule.from(ListeningPrompt.sequential(listOf(Pitch(60), Pitch(64))), sampleRate, timing)

        assertEquals(
            listOf(ScheduledNote(Pitch(60), startFrame = 30, frameCount = 900), ScheduledNote(Pitch(64), startFrame = 1_080, frameCount = 900)),
            schedule.notes,
        )
        assertEquals(150, schedule.notes[1].startFrame - schedule.notes[0].endFrame)
        assertEquals(1_980, schedule.contentEndFrame)
        assertEquals(2_080, schedule.totalFrames)
        assertEquals(1, schedule.maxPolyphony)
    }

    @Test
    fun simultaneousNotesShareStartAndUseChordDuration() {
        val schedule = NoteSchedule.from(ListeningPrompt.simultaneous(listOf(Pitch(60), Pitch(67))), sampleRate, timing)

        assertEquals(listOf(30, 30), schedule.notes.map { it.startFrame })
        assertEquals(listOf(1_600, 1_600), schedule.notes.map { it.frameCount })
        assertEquals(2, schedule.maxPolyphony)
        assertEquals(1_630, schedule.contentEndFrame)
        assertEquals(1_730, schedule.totalFrames)
    }

    @Test
    fun timingScalesWithSampleRate() {
        val schedule = NoteSchedule.from(ListeningPrompt.sequential(listOf(Pitch(60), Pitch(64))), 44_100, timing)
        assertEquals(1_323 + 39_690 + 6_615, schedule.notes[1].startFrame)
        assertEquals(2.08, schedule.durationSeconds, 1e-3)
    }
}
