package com.example.eartrainer.data.backup

import com.example.eartrainer.data.history.ChordAttemptEntity
import com.example.eartrainer.data.history.DictationAttemptEntity
import com.example.eartrainer.data.history.IntervalAttemptEntity
import com.example.eartrainer.data.history.ProgressionAttemptEntity
import com.example.eartrainer.data.history.RhythmAttemptEntity
import com.example.eartrainer.data.history.ScaleDegreeAttemptEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val sample = BackupFile(
        exportedAtMillis = 1_700_000_000_000,
        settings = mapOf(
            "rhythm.bars" to SettingValue(int = 2),
            "rhythm.tempo" to SettingValue(string = "FAST"),
            "rhythm.focus_weak_spots" to SettingValue(boolean = false),
            "interval_training.styles" to SettingValue(stringSet = setOf("HARMONIC")),
        ),
        history = BackupHistory(
            intervals = listOf(IntervalAttemptEntity(7, "s1", 1_000, "PERFECT_FIFTH", "HARMONIC", 55, "PERFECT_FIFTH", true, 2, 1_800)),
            chords = listOf(ChordAttemptEntity(0, "s2", 2_000, "MINOR", "FIRST", "BLOCK", "QUALITY", 52, "MAJOR", false, 1, 2_500)),
            scaleDegrees = listOf(ScaleDegreeAttemptEntity(0, "s3", 3_000, 60, "MAJOR", "THIRD", "CADENCE", "THIRD", true, 1, 1_200)),
            progressions = listOf(ProgressionAttemptEntity(0, "s4", 4_000, 60, "MAJOR", "1,6,4,5", "1,6,4,5", true, 1, 3_000)),
            dictations = listOf(DictationAttemptEntity(0, "s5", 5_000, 62, "MINOR", "TONIC,SECOND,THIRD", "TONIC,SECOND,FOURTH", false, 2, 7_000)),
            rhythms = listOf(RhythmAttemptEntity(0, "s6", 6_000, "MEDIUM", "QUARTER,EIGHTHS,HALF", "QUARTER,EIGHTHS,HALF", true, 1, 4_000)),
        ),
    )

    @Test
    fun encodeThenDecodeRoundTripsWithoutDatabaseIds() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample))
        // Ids are left out, so imported rows get fresh ones.
        assertFalse(BackupCodec.encode(sample).contains("\"id\""))
        assertEquals(sample.copy(history = sample.history.copy(intervals = sample.history.intervals.map { it.copy(id = 0) })), decoded)
        assertEquals(6, decoded.history.size)
    }

    /** A version-1 file as written by the first release; later versions must keep reading it. */
    @Test
    fun readsTheFirstFormatVersion() {
        val text = """
            {
              "app": "com.example.eartrainer",
              "formatVersion": 1,
              "exportedAtMillis": 1700000000000,
              "settings": { "rhythm.bars": { "int": 2 }, "interval_training.styles": { "stringSet": ["HARMONIC"] } },
              "history": {
                "intervals": [
                  { "sessionId": "s1", "answeredAtMillis": 1000, "interval": "PERFECT_FIFTH", "style": "HARMONIC",
                    "lowerMidi": 55, "answer": "PERFECT_FIFTH", "isCorrect": true, "playCount": 2, "responseMillis": 1800 }
                ],
                "rhythms": [
                  { "sessionId": "s6", "answeredAtMillis": 6000, "tempo": "MEDIUM", "rhythm": "QUARTER,EIGHTHS,HALF",
                    "answer": "QUARTER,EIGHTHS,HALF", "isCorrect": true, "playCount": 1, "responseMillis": 4000 }
                ]
              }
            }
        """
        val file = BackupCodec.decode(text)
        assertEquals(listOf(sample.history.intervals.single().copy(id = 0)), file.history.intervals)
        assertEquals(sample.history.rhythms, file.history.rhythms)
        assertEquals(emptyList<ChordAttemptEntity>(), file.history.chords)
        assertEquals(SettingValue(int = 2), file.settings["rhythm.bars"])
    }

    @Test
    fun unknownFieldsAndValueTypesFromNewerVersionsAreIgnored() {
        val text = """
            { "app": "com.example.eartrainer", "formatVersion": 1, "exportedAtMillis": 1, "device": "tablet",
              "settings": { "future.volume": { "float": 0.5 } },
              "history": { "sightSinging": [ { "x": 1 } ] } }
        """
        val file = BackupCodec.decode(text)
        assertEquals(SettingValue(), file.settings["future.volume"])
        assertEquals(0, file.history.size)
    }

    @Test
    fun rejectsFilesThatAreNotBackupsOfThisApp() {
        listOf(
            "",
            "not json",
            "[1, 2, 3]",
            """{ "exportedAtMillis": 1 }""",
            """{ "app": "com.other.app", "formatVersion": 1, "exportedAtMillis": 1 }""",
            """{ "app": "com.example.eartrainer", "formatVersion": 1, "exportedAtMillis": 1, "history": { "intervals": [ { "sessionId": "s" } ] } }""",
        ).forEach { text ->
            val error = assertThrows(text, BackupException::class.java) { BackupCodec.decode(text) }
            assertEquals(text, BackupException.Reason.NOT_A_BACKUP, error.reason)
        }
    }

    @Test
    fun rejectsNewerFormatVersions() {
        val error = assertThrows(BackupException::class.java) {
            BackupCodec.decode("""{ "app": "com.example.eartrainer", "formatVersion": 2, "exportedAtMillis": 1 }""")
        }
        assertEquals(BackupException.Reason.NEWER_VERSION, error.reason)
    }
}
