package com.example.eartrainer.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.example.eartrainer.data.history.EarTrainerDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** What importing a backup changed. */
data class ImportResult(
    /** Answers added to history. */
    val added: Int,
    /** Answers skipped because history already had them, e.g. when importing the same file twice. */
    val alreadyPresent: Int,
    /** Answers skipped because the app could not read them. */
    val unreadable: Int,
    /** Whether the backup's settings replaced the current ones. */
    val settingsRestored: Boolean,
)

/** Saves practice history and settings to a file, and brings them back. */
interface AppDataBackup {
    /** Writes everything to [output] and closes it; returns the number of answers written. */
    suspend fun export(output: OutputStream): Int

    /**
     * Reads a backup from [input] and closes it. History is merged: answers already present are
     * not added again. Settings are replaced by the backup's.
     *
     * @throws BackupException if the file is not a backup this version can read; nothing changes.
     */
    suspend fun import(input: InputStream): ImportResult
}

class RoomDataBackup(
    private val database: EarTrainerDatabase,
    private val settings: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppDataBackup {

    override suspend fun export(output: OutputStream): Int = withContext(ioDispatcher) {
        val history = database.withTransaction {
            BackupHistory(
                intervals = database.intervalAttemptDao().all(),
                chords = database.chordAttemptDao().all(),
                scaleDegrees = database.scaleDegreeAttemptDao().all(),
                progressions = database.progressionAttemptDao().all(),
                dictations = database.dictationAttemptDao().all(),
                rhythms = database.rhythmAttemptDao().all(),
            )
        }
        val currentSettings = settings.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()
        val file = BackupFile(exportedAtMillis = clock(), settings = SettingsBackup.export(currentSettings), history = history)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(BackupCodec.encode(file)) }
        history.size
    }

    override suspend fun import(input: InputStream): ImportResult = withContext(ioDispatcher) {
        // Parsed and checked in full before anything is written, so a bad file changes nothing.
        val file = BackupCodec.decode(input.use { it.readLimited() }.toString(Charsets.UTF_8))
        val history = file.history
        val counts = MergeCounts()
        database.withTransaction {
            with(database.intervalAttemptDao()) { counts.merge(history.intervals, all(), { it.isValid() }, { (it.sessionId to it.answeredAtMillis) }) { insertAll(it) } }
            with(database.chordAttemptDao()) { counts.merge(history.chords, all(), { it.isValid() }, { (it.sessionId to it.answeredAtMillis) }) { insertAll(it) } }
            with(database.scaleDegreeAttemptDao()) { counts.merge(history.scaleDegrees, all(), { it.isValid() }, { (it.sessionId to it.answeredAtMillis) }) { insertAll(it) } }
            with(database.progressionAttemptDao()) { counts.merge(history.progressions, all(), { it.isValid() }, { (it.sessionId to it.answeredAtMillis) }) { insertAll(it) } }
            with(database.dictationAttemptDao()) { counts.merge(history.dictations, all(), { it.isValid() }, { (it.sessionId to it.answeredAtMillis) }) { insertAll(it) } }
            with(database.rhythmAttemptDao()) { counts.merge(history.rhythms, all(), { it.isValid() }, { (it.sessionId to it.answeredAtMillis) }) { insertAll(it) } }
        }
        // A backup without settings (none were saved, or the file was edited) leaves them alone.
        val restoreSettings = file.settings.isNotEmpty()
        if (restoreSettings) {
            val imported = SettingsBackup.import(file.settings)
            settings.edit { current ->
                current.clear()
                current += imported
            }
        }
        ImportResult(counts.added, counts.alreadyPresent, counts.unreadable, restoreSettings)
    }

    private class MergeCounts {
        var added = 0
        var alreadyPresent = 0
        var unreadable = 0

        /**
         * Inserts the rows of [incoming] that are valid and not yet stored. An answer is
         * identified by its session and the moment it was given, which no two answers share.
         */
        suspend fun <T> merge(
            incoming: List<T>,
            existing: List<T>,
            isValid: (T) -> Boolean,
            identity: (T) -> Pair<String, Long>,
            insert: suspend (List<T>) -> Unit,
        ) {
            val seen = existing.mapTo(HashSet(), identity)
            val fresh = incoming.filter { row ->
                when {
                    !isValid(row) -> false.also { unreadable++ }
                    !seen.add(identity(row)) -> false.also { alreadyPresent++ }
                    else -> true
                }
            }
            if (fresh.isNotEmpty()) insert(fresh)
            added += fresh.size
        }
    }

    private companion object {
        /** Far beyond a lifetime of practice (roughly 300 bytes per answer). */
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

        fun InputStream.readLimited(): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                out.write(buffer, 0, read)
                if (out.size() > MAX_BACKUP_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
            }
        }
    }
}
