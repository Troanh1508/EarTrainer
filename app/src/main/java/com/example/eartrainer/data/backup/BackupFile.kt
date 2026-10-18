package com.example.eartrainer.data.backup

import com.example.eartrainer.data.history.ChordAttemptEntity
import com.example.eartrainer.data.history.DictationAttemptEntity
import com.example.eartrainer.data.history.IntervalAttemptEntity
import com.example.eartrainer.data.history.ProgressionAttemptEntity
import com.example.eartrainer.data.history.RhythmAttemptEntity
import com.example.eartrainer.data.history.ScaleDegreeAttemptEntity
import kotlinx.serialization.Required
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything a user can export: practice history and settings, as one JSON document.
 *
 * Readers ignore unknown fields and fill in missing ones with defaults, so a newer app can add
 * fields without breaking older backups. A change that older readers cannot handle must raise
 * [FORMAT_VERSION].
 */
@Serializable
data class BackupFile(
    // Required in the file, so that unrelated JSON is not mistaken for a backup.
    @Required val app: String = APP,
    @Required val formatVersion: Int = FORMAT_VERSION,
    val exportedAtMillis: Long,
    /** Setting values keyed by their DataStore name, e.g. "rhythm.bars". */
    val settings: Map<String, SettingValue> = emptyMap(),
    val history: BackupHistory = BackupHistory(),
) {
    companion object {
        const val APP = "com.example.eartrainer"
        const val FORMAT_VERSION = 1
    }
}

/** Every stored answer, one list per exercise. */
@Serializable
data class BackupHistory(
    val intervals: List<IntervalAttemptEntity> = emptyList(),
    val chords: List<ChordAttemptEntity> = emptyList(),
    val scaleDegrees: List<ScaleDegreeAttemptEntity> = emptyList(),
    val progressions: List<ProgressionAttemptEntity> = emptyList(),
    val dictations: List<DictationAttemptEntity> = emptyList(),
    val rhythms: List<RhythmAttemptEntity> = emptyList(),
) {
    val size: Int get() = intervals.size + chords.size + scaleDegrees.size + progressions.size + dictations.size + rhythms.size
}

/**
 * One setting value; exactly one field is set. A flat object rather than a polymorphic type, so
 * a value type added later is skipped by older readers instead of failing the whole file.
 */
@Serializable
data class SettingValue(
    val string: String? = null,
    val int: Int? = null,
    val boolean: Boolean? = null,
    val stringSet: Set<String>? = null,
)

/** Why a backup could not be read. */
class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Not JSON, or JSON from something other than this app. */
        NOT_A_BACKUP,

        /** Written by a newer version of the app in a format this version cannot read. */
        NEWER_VERSION,

        /** Far larger than any real backup; refused rather than read into memory. */
        TOO_LARGE,
    }
}

internal object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Parses and checks a backup; throws [BackupException] if it is not one this app can read. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        }
        if (file.app != BackupFile.APP) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (file.formatVersion > BackupFile.FORMAT_VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return file
    }
}
