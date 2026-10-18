package com.example.eartrainer.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.eartrainer.data.history.ChordAttemptEntity
import com.example.eartrainer.data.history.DictationAttemptEntity
import com.example.eartrainer.data.history.EarTrainerDatabase
import com.example.eartrainer.data.history.IntervalAttemptEntity
import com.example.eartrainer.data.history.ProgressionAttemptEntity
import com.example.eartrainer.data.history.RhythmAttemptEntity
import com.example.eartrainer.data.history.ScaleDegreeAttemptEntity
import com.example.eartrainer.data.settings.RhythmSettingsStore
import com.example.eartrainer.exercise.RhythmSettings
import com.example.eartrainer.exercise.RhythmTempo
import com.example.eartrainer.exercise.RhythmVocabulary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Exports from one "device" and imports into another, through real Room and DataStore storage. */
@RunWith(AndroidJUnit4::class)
class RoomDataBackupTest {

    private class Device(context: Context, name: String) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "backup-test-$name.preferences_pb").also { it.delete() }
        val database: EarTrainerDatabase = Room.inMemoryDatabaseBuilder(context, EarTrainerDatabase::class.java).build()
        val settings: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        val backup = RoomDataBackup(database, settings, clock = { 1_700_000_000_000 })

        fun close() {
            database.close()
            scope.cancel()
            file.delete()
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var phone: Device
    private lateinit var tablet: Device

    private val custom = RhythmSettings(RhythmVocabulary.SYNCOPATION, bars = 2, tempo = RhythmTempo.SLOW, focusOnWeakSpots = false)

    @Before
    fun setUp() {
        phone = Device(context, "phone")
        tablet = Device(context, "tablet")
    }

    @After
    fun tearDown() {
        phone.close()
        tablet.close()
    }

    private fun Device.practiceEveryExercise(session: String, at: Long) = runBlocking {
        database.intervalAttemptDao().insert(IntervalAttemptEntity(0, session, at, "PERFECT_FIFTH", "HARMONIC", 55, "PERFECT_FIFTH", true, 2, 1_800))
        database.chordAttemptDao().insert(ChordAttemptEntity(0, session, at + 1, "MINOR", "FIRST", "BLOCK", "QUALITY", 52, "MAJOR", false, 1, 2_500))
        database.scaleDegreeAttemptDao().insert(ScaleDegreeAttemptEntity(0, session, at + 2, 60, "MAJOR", "THIRD", "CADENCE", "THIRD", true, 1, 1_200))
        database.progressionAttemptDao().insert(ProgressionAttemptEntity(0, session, at + 3, 60, "MAJOR", "1,6,4,5", "1,6,4,5", true, 1, 3_000))
        database.dictationAttemptDao().insert(DictationAttemptEntity(0, session, at + 4, 62, "MINOR", "TONIC,SECOND,THIRD", "TONIC,SECOND,THIRD", true, 2, 7_000))
        database.rhythmAttemptDao().insert(RhythmAttemptEntity(0, session, at + 5, "MEDIUM", "QUARTER,EIGHTHS,HALF", "QUARTER,EIGHTHS,HALF", true, 1, 4_000))
    }

    private fun Device.history() = runBlocking {
        listOf(
            database.intervalAttemptDao().all().map { it.copy(id = 0) },
            database.chordAttemptDao().all().map { it.copy(id = 0) },
            database.scaleDegreeAttemptDao().all().map { it.copy(id = 0) },
            database.progressionAttemptDao().all().map { it.copy(id = 0) },
            database.dictationAttemptDao().all().map { it.copy(id = 0) },
            database.rhythmAttemptDao().all().map { it.copy(id = 0) },
        )
    }

    private fun Device.export(): ByteArray = ByteArrayOutputStream().also { out -> runBlocking { backup.export(out) } }.toByteArray()

    private fun Device.import(bytes: ByteArray) = runBlocking { backup.import(ByteArrayInputStream(bytes)) }

    private fun Device.rhythmSettings() = runBlocking { RhythmSettingsStore(settings).settings.first() }

    @Test
    fun exportThenImportOnAnotherDeviceRestoresHistoryAndSettings() {
        phone.practiceEveryExercise("a", 1_000)
        phone.practiceEveryExercise("b", 2_000)
        runBlocking { RhythmSettingsStore(phone.settings).update { custom } }

        val bytes = phone.export()
        val result = tablet.import(bytes)

        assertEquals(ImportResult(added = 12, alreadyPresent = 0, unreadable = 0, settingsRestored = true), result)
        assertEquals(phone.history(), tablet.history())
        assertEquals(custom, tablet.rhythmSettings())
        assertEquals(2, runBlocking { tablet.database.sessionDao().recentSessions(10).first() }.size)
    }

    @Test
    fun importingMergesWithExistingHistoryWithoutDuplicates() {
        phone.practiceEveryExercise("a", 1_000)
        tablet.practiceEveryExercise("t", 5_000)
        val bytes = phone.export()

        assertEquals(6, tablet.import(bytes).added)
        // The same file again adds nothing.
        assertEquals(ImportResult(added = 0, alreadyPresent = 6, unreadable = 0, settingsRestored = true), tablet.import(bytes))
        assertEquals(12, tablet.history().sumOf { it.size })
        // Exporting the merged history and importing it back into the phone adds only the tablet's answers.
        assertEquals(6, phone.import(tablet.export()).added)
        assertEquals(phone.history(), tablet.history())
    }

    @Test
    fun unreadableRowsAreSkippedAndCounted() {
        val file = BackupFile(
            exportedAtMillis = 1,
            history = BackupHistory(
                intervals = listOf(
                    IntervalAttemptEntity(0, "s", 1_000, "PERFECT_FIFTH", "HARMONIC", 55, "PERFECT_FIFTH", true, 2, 1_800),
                    IntervalAttemptEntity(0, "s", 1_001, "NOT_AN_INTERVAL", "HARMONIC", 55, "PERFECT_FIFTH", true, 2, 1_800),
                ),
            ),
        )
        val result = tablet.import(BackupCodec.encode(file).toByteArray())

        // No settings in the file, so the current ones are kept.
        assertEquals(ImportResult(added = 1, alreadyPresent = 0, unreadable = 1, settingsRestored = false), result)
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() {
        tablet.practiceEveryExercise("t", 5_000)
        runBlocking { RhythmSettingsStore(tablet.settings).update { custom } }
        val before = tablet.history()

        val error = assertThrows(BackupException::class.java) { tablet.import("""{ "app": "com.example.eartrainer", "formatVersion": 1 }""".toByteArray()) }

        assertEquals(BackupException.Reason.NOT_A_BACKUP, error.reason)
        assertEquals(before, tablet.history())
        assertEquals(custom, tablet.rhythmSettings())
    }

    @Test
    fun importReplacesSettingsOnlyWithValuesTheAppCanRead() {
        runBlocking { tablet.settings.edit { it.clear() } }
        val file = BackupFile(
            exportedAtMillis = 1,
            settings = mapOf("rhythm.bars" to SettingValue(string = "two"), "rhythm.tempo" to SettingValue(string = "FAST")),
        )
        tablet.import(BackupCodec.encode(file).toByteArray())
        assertEquals(RhythmSettings(tempo = RhythmTempo.FAST), tablet.rhythmSettings())
    }
}
