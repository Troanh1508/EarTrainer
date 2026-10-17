package com.example.eartrainer.data.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Enum values are stored by name so the database stays readable and reorder-safe. */
@Entity(
    tableName = "interval_attempts",
    indices = [Index("session_id"), Index("interval")],
)
data class IntervalAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
data class ChordAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
data class ScaleDegreeAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
data class ProgressionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
data class DictationAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

/** One recent answer, for weighting practice towards weak spots. */
data class ItemResultRow(val item: String, val isCorrect: Boolean)

data class ChordResultRow(val target: String, val quality: String, val inversion: String, val isCorrect: Boolean)

data class ProgressionResultRow(val mode: String, val progression: String, val answer: String)

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

    /** Every dictation in [mode]; per-degree totals are worked out from these. */
    @Query("SELECT melody, answer FROM dictation_attempts WHERE mode = :mode")
    fun answersInMode(mode: String): Flow<List<MelodyAnswerRow>>

    @Query("SELECT melody, answer FROM dictation_attempts ORDER BY answered_at DESC, id DESC LIMIT :limit")
    fun recentAnswers(limit: Int): Flow<List<MelodyAnswerRow>>
}

@Dao
interface SessionDao {
    /** Sessions from every exercise type, newest first. A session belongs to one exercise only. */
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
        )
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun recentSessions(limit: Int): Flow<List<SessionSummaryRow>>
}
