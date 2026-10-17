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
        )
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun recentSessions(limit: Int): Flow<List<SessionSummaryRow>>
}
