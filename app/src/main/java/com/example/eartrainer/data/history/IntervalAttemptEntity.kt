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

data class IntervalAccuracyRow(val interval: String, val attempts: Int, val correct: Int)

data class SessionSummaryRow(
    val sessionId: String,
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
        SELECT interval, COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM interval_attempts
        WHERE :style IS NULL OR style = :style
        GROUP BY interval
        """,
    )
    fun accuracyByInterval(style: String?): Flow<List<IntervalAccuracyRow>>

    @Query(
        """
        SELECT session_id AS sessionId, MIN(answered_at) AS startedAtMillis,
               COUNT(*) AS attempts, SUM(is_correct) AS correct
        FROM interval_attempts
        GROUP BY session_id
        ORDER BY startedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun recentSessions(limit: Int): Flow<List<SessionSummaryRow>>
}
