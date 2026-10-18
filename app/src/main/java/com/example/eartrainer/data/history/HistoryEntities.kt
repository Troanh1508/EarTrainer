package com.example.eartrainer.data.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Enum values are stored by name so the database stays readable and reorder-safe.
 *
 * The attempt entities double as the backup file format (see BackupFile): renaming or
 * removing a property breaks importing older backups, so add properties with defaults instead.
 */
@Entity(
    tableName = "interval_attempts",
    indices = [Index("session_id"), Index("interval")],
)
@Serializable
data class IntervalAttemptEntity(
    // Left out of backups: imported rows get fresh ids.
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "interval") val interval: String,
    @ColumnInfo(name = "style") val style: String,
    @ColumnInfo(name = "lower_midi") val lowerMidi: Int,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "response_ms") val responseMillis: Long,
)

/** Added in schema version 2. [answer] holds a quality or inversion name, depending on [target]. */
@Entity(
    tableName = "chord_attempts",
    indices = [Index("session_id"), Index("target")],
)
@Serializable
data class ChordAttemptEntity(
    // Left out of backups: imported rows get fresh ids.
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "quality") val quality: String,
    @ColumnInfo(name = "inversion") val inversion: String,
    @ColumnInfo(name = "style") val style: String,
    @ColumnInfo(name = "target") val target: String,
    @ColumnInfo(name = "bass_midi") val bassMidi: Int,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "response_ms") val responseMillis: Long,
)

/** Added in schema version 3. Enum values are stored by name. */
@Entity(
    tableName = "scale_degree_attempts",
    indices = [Index("session_id"), Index("degree")],
)
@Serializable
data class ScaleDegreeAttemptEntity(
    // Left out of backups: imported rows get fresh ids.
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "tonic_midi") val tonicMidi: Int,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "degree") val degree: String,
    @ColumnInfo(name = "context") val context: String,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "response_ms") val responseMillis: Long,
)

/** Added in schema version 4. Progressions are stored as comma-separated scale steps, e.g. "1,6,4,5". */
@Entity(
    tableName = "progression_attempts",
    indices = [Index("session_id"), Index("mode")],
)
@Serializable
data class ProgressionAttemptEntity(
    // Left out of backups: imported rows get fresh ids.
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "tonic_midi") val tonicMidi: Int,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "progression") val progression: String,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "response_ms") val responseMillis: Long,
)

/** A stored progression question and the progression chosen, as scale-step lists. */
data class ProgressionAnswerRow(val progression: String, val answer: String)

/** Added in schema version 5. Melodies are stored as comma-separated degree names. */
@Entity(
    tableName = "dictation_attempts",
    indices = [Index("session_id"), Index("mode")],
)
@Serializable
data class DictationAttemptEntity(
    // Left out of backups: imported rows get fresh ids.
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "tonic_midi") val tonicMidi: Int,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "melody") val melody: String,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "response_ms") val responseMillis: Long,
)

/** A stored melody and the one written down, as degree-name lists. */
data class MelodyAnswerRow(val melody: String, val answer: String)

/** Added in schema version 6. Rhythms are stored as comma-separated cell names. */
@Entity(
    tableName = "rhythm_attempts",
    indices = [Index("session_id")],
)
@Serializable
data class RhythmAttemptEntity(
    // Left out of backups: imported rows get fresh ids.
    @Transient @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "tempo") val tempo: String,
    @ColumnInfo(name = "rhythm") val rhythm: String,
    @ColumnInfo(name = "answer") val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "response_ms") val responseMillis: Long,
)

/** A stored rhythm and the one chosen, as cell-name lists. */
data class RhythmAnswerRow(val rhythm: String, val answer: String)

/** One recent answer, for weighting practice toward weak spots. */
data class ItemResultRow(val item: String, val isCorrect: Boolean)

data class ChordResultRow(val target: String, val quality: String, val inversion: String, val isCorrect: Boolean)

data class ProgressionResultRow(val mode: String, val progression: String, val answer: String)

/** Answer totals for one session. */
data class SessionTotalsRow(val sessionId: String, val startedAtMillis: Long, val attempts: Int, val correct: Int)

/**
 * One stored answer with its session, for scoring item by item: [asked] and [chosen] hold the
 * progression, melody or rhythm as stored; [mode] is null for rhythms.
 */
data class SessionAnswerRow(
    val sessionId: String,
    val answeredAtMillis: Long,
    val mode: String?,
    val asked: String,
    val chosen: String,
    val isCorrect: Boolean,
)

/** Totals for one answer value, e.g. an interval or a chord quality, stored by name. */
data class AccuracyRow(val item: String, val attempts: Int, val correct: Int)

data class SessionSummaryRow(
    val sessionId: String,
    val exercise: String,
    val startedAtMillis: Long,
    val attempts: Int,
    val correct: Int,
)

@Dao
interface IntervalAttemptDao {
    @Insert
    suspend fun insert(attempt: IntervalAttemptEntity)

    @Insert
    suspend fun insertAll(attempts: List<IntervalAttemptEntity>)

    /** Every stored answer, oldest first, for backups. */
    @Query("SELECT * FROM interval_attempts ORDER BY answered_at, id")
    suspend fun all(): List<IntervalAttemptEntity>

    /** Per-session totals of the answers in [style] (null means every style), newest session first. */
    @Query(
        """
        SELECT session_id AS sessionId, MIN(answered_at) AS startedAtMillis, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM interval_attempts
        WHERE :style IS NULL OR style = :style
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun sessionTotals(style: String?, limit: Int): Flow<List<SessionTotalsRow>>

    /** Totals per interval, optionally limited to one style (null means all styles). */
    @Query(
        """
        SELECT interval AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM interval_attempts
        WHERE :style IS NULL OR style = :style
        GROUP BY interval
        """,
    )
    fun accuracyByInterval(style: String?): Flow<List<AccuracyRow>>

    @Query("SELECT interval AS item, is_correct AS isCorrect FROM interval_attempts ORDER BY answered_at DESC, id DESC LIMIT :limit")
    fun recentResults(limit: Int): Flow<List<ItemResultRow>>
}

@Dao
interface ChordAttemptDao {
    @Insert
    suspend fun insert(attempt: ChordAttemptEntity)

    @Insert
    suspend fun insertAll(attempts: List<ChordAttemptEntity>)

    /** Every stored answer, oldest first, for backups. */
    @Query("SELECT * FROM chord_attempts ORDER BY answered_at, id")
    suspend fun all(): List<ChordAttemptEntity>

    /** Per-session totals of the questions that asked for [target], newest session first. */
    @Query(
        """
        SELECT session_id AS sessionId, MIN(answered_at) AS startedAtMillis, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM chord_attempts
        WHERE target = :target
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun sessionTotals(target: String, limit: Int): Flow<List<SessionTotalsRow>>

    /** Totals per chord quality, for questions that asked for the quality. */
    @Query(
        """
        SELECT quality AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM chord_attempts
        WHERE target = 'QUALITY'
        GROUP BY quality
        """,
    )
    fun accuracyByQuality(): Flow<List<AccuracyRow>>

    /** Totals per inversion, for questions that asked for the inversion. */
    @Query(
        """
        SELECT inversion AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM chord_attempts
        WHERE target = 'INVERSION'
        GROUP BY inversion
        """,
    )
    fun accuracyByInversion(): Flow<List<AccuracyRow>>

    @Query(
        """
        SELECT target, quality, inversion, is_correct AS isCorrect FROM chord_attempts
        ORDER BY answered_at DESC, id DESC LIMIT :limit
        """,
    )
    fun recentResults(limit: Int): Flow<List<ChordResultRow>>
}

@Dao
interface ScaleDegreeAttemptDao {
    @Insert
    suspend fun insert(attempt: ScaleDegreeAttemptEntity)

    @Insert
    suspend fun insertAll(attempts: List<ScaleDegreeAttemptEntity>)

    /** Every stored answer, oldest first, for backups. */
    @Query("SELECT * FROM scale_degree_attempts ORDER BY answered_at, id")
    suspend fun all(): List<ScaleDegreeAttemptEntity>

    /** Per-session totals of the answers in [mode] (null means every mode), newest session first. */
    @Query(
        """
        SELECT session_id AS sessionId, MIN(answered_at) AS startedAtMillis, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM scale_degree_attempts
        WHERE :mode IS NULL OR mode = :mode
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun sessionTotals(mode: String?, limit: Int): Flow<List<SessionTotalsRow>>

    /** Totals per scale degree, optionally limited to one mode (null means all modes). */
    @Query(
        """
        SELECT degree AS item, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM scale_degree_attempts
        WHERE :mode IS NULL OR mode = :mode
        GROUP BY degree
        """,
    )
    fun accuracyByDegree(mode: String?): Flow<List<AccuracyRow>>

    @Query("SELECT degree AS item, is_correct AS isCorrect FROM scale_degree_attempts ORDER BY answered_at DESC, id DESC LIMIT :limit")
    fun recentResults(limit: Int): Flow<List<ItemResultRow>>
}

@Dao
interface ProgressionAttemptDao {
    @Insert
    suspend fun insert(attempt: ProgressionAttemptEntity)

    @Insert
    suspend fun insertAll(attempts: List<ProgressionAttemptEntity>)

    /** Every stored answer, oldest first, for backups. */
    @Query("SELECT * FROM progression_attempts ORDER BY answered_at, id")
    suspend fun all(): List<ProgressionAttemptEntity>

    /** Every answer in [mode] from the latest [limit] sessions that have answers in it. */
    @Query(
        """
        SELECT session_id AS sessionId, answered_at AS answeredAtMillis, mode, progression AS asked, answer AS chosen, is_correct AS isCorrect
        FROM progression_attempts
        WHERE mode = :mode AND session_id IN (
            SELECT session_id FROM progression_attempts WHERE mode = :mode
            GROUP BY session_id ORDER BY MIN(answered_at) DESC LIMIT :limit
        )
        """,
    )
    fun sessionAnswers(mode: String, limit: Int): Flow<List<SessionAnswerRow>>

    @Query(
        """
        SELECT session_id AS sessionId, answered_at AS answeredAtMillis, mode, progression AS asked, answer AS chosen, is_correct AS isCorrect
        FROM progression_attempts WHERE session_id IN (:sessionIds)
        """,
    )
    fun answersInSessions(sessionIds: List<String>): Flow<List<SessionAnswerRow>>

    /** Every answered progression in [mode]; per-chord totals are worked out from these. */
    @Query("SELECT progression, answer FROM progression_attempts WHERE mode = :mode")
    fun answersInMode(mode: String): Flow<List<ProgressionAnswerRow>>

    @Query("SELECT mode, progression, answer FROM progression_attempts ORDER BY answered_at DESC, id DESC LIMIT :limit")
    fun recentResults(limit: Int): Flow<List<ProgressionResultRow>>
}

@Dao
interface DictationAttemptDao {
    @Insert
    suspend fun insert(attempt: DictationAttemptEntity)

    @Insert
    suspend fun insertAll(attempts: List<DictationAttemptEntity>)

    /** Every stored answer, oldest first, for backups. */
    @Query("SELECT * FROM dictation_attempts ORDER BY answered_at, id")
    suspend fun all(): List<DictationAttemptEntity>

    /** Every answer in [mode] from the latest [limit] sessions that have answers in it. */
    @Query(
        """
        SELECT session_id AS sessionId, answered_at AS answeredAtMillis, mode, melody AS asked, answer AS chosen, is_correct AS isCorrect
        FROM dictation_attempts
        WHERE mode = :mode AND session_id IN (
            SELECT session_id FROM dictation_attempts WHERE mode = :mode
            GROUP BY session_id ORDER BY MIN(answered_at) DESC LIMIT :limit
        )
        """,
    )
    fun sessionAnswers(mode: String, limit: Int): Flow<List<SessionAnswerRow>>

    @Query(
        """
        SELECT session_id AS sessionId, answered_at AS answeredAtMillis, mode, melody AS asked, answer AS chosen, is_correct AS isCorrect
        FROM dictation_attempts WHERE session_id IN (:sessionIds)
        """,
    )
    fun answersInSessions(sessionIds: List<String>): Flow<List<SessionAnswerRow>>

    /** Every dictation in [mode]; per-degree totals are worked out from these. */
    @Query("SELECT melody, answer FROM dictation_attempts WHERE mode = :mode")
    fun answersInMode(mode: String): Flow<List<MelodyAnswerRow>>

    @Query("SELECT melody, answer FROM dictation_attempts ORDER BY answered_at DESC, id DESC LIMIT :limit")
    fun recentAnswers(limit: Int): Flow<List<MelodyAnswerRow>>
}

@Dao
interface RhythmAttemptDao {
    @Insert
    suspend fun insert(attempt: RhythmAttemptEntity)

    @Insert
    suspend fun insertAll(attempts: List<RhythmAttemptEntity>)

    /** Every stored answer, oldest first, for backups. */
    @Query("SELECT * FROM rhythm_attempts ORDER BY answered_at, id")
    suspend fun all(): List<RhythmAttemptEntity>

    /** Every answer from the latest [limit] rhythm sessions. */
    @Query(
        """
        SELECT session_id AS sessionId, answered_at AS answeredAtMillis, NULL AS mode, rhythm AS asked, answer AS chosen, is_correct AS isCorrect
        FROM rhythm_attempts
        WHERE session_id IN (
            SELECT session_id FROM rhythm_attempts GROUP BY session_id ORDER BY MIN(answered_at) DESC LIMIT :limit
        )
        """,
    )
    fun sessionAnswers(limit: Int): Flow<List<SessionAnswerRow>>

    @Query(
        """
        SELECT session_id AS sessionId, answered_at AS answeredAtMillis, NULL AS mode, rhythm AS asked, answer AS chosen, is_correct AS isCorrect
        FROM rhythm_attempts WHERE session_id IN (:sessionIds)
        """,
    )
    fun answersInSessions(sessionIds: List<String>): Flow<List<SessionAnswerRow>>

    /** Every rhythm answer; per-cell totals are worked out from these. */
    @Query("SELECT rhythm, answer FROM rhythm_attempts")
    fun allAnswers(): Flow<List<RhythmAnswerRow>>

    @Query("SELECT rhythm, answer FROM rhythm_attempts ORDER BY answered_at DESC, id DESC LIMIT :limit")
    fun recentAnswers(limit: Int): Flow<List<RhythmAnswerRow>>
}

@Dao
interface SessionDao {
    /**
     * Sessions newest first. A session belongs to one exercise only; [exercise] (an exercise
     * kind's name) limits them to that exercise, and null includes every exercise.
     */
    @Query(
        """
        SELECT session_id AS sessionId, exercise, MIN(answered_at) AS startedAtMillis,
               COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM (
            SELECT session_id, answered_at, is_correct, 'INTERVALS' AS exercise FROM interval_attempts
            UNION ALL
            SELECT session_id, answered_at, is_correct, 'CHORDS' AS exercise FROM chord_attempts
            UNION ALL
            SELECT session_id, answered_at, is_correct, 'SCALE_DEGREES' AS exercise FROM scale_degree_attempts
            UNION ALL
            SELECT session_id, answered_at, is_correct, 'PROGRESSIONS' AS exercise FROM progression_attempts
            UNION ALL
            SELECT session_id, answered_at, is_correct, 'DICTATION' AS exercise FROM dictation_attempts
            UNION ALL
            SELECT session_id, answered_at, is_correct, 'RHYTHM' AS exercise FROM rhythm_attempts
        )
        WHERE :exercise IS NULL OR exercise = :exercise
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun recentSessions(limit: Int, exercise: String? = null): Flow<List<SessionSummaryRow>>
}
