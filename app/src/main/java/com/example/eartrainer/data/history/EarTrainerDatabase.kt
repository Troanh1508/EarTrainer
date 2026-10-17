package com.example.eartrainer.data.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version history:
 * 1. interval_attempts
 * 2. chord_attempts (added table, so Room migrates automatically from the exported schemas)
 * 3. scale_degree_attempts (added table, automatic migration)
 * 4. progression_attempts (added table, automatic migration)
 * 5. dictation_attempts (added table, automatic migration)
 * 6. rhythm_attempts (added table, automatic migration)
 */
@Database(
    entities = [
        IntervalAttemptEntity::class,
        ChordAttemptEntity::class,
        ScaleDegreeAttemptEntity::class,
        ProgressionAttemptEntity::class,
        DictationAttemptEntity::class,
        RhythmAttemptEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class EarTrainerDatabase : RoomDatabase() {
    abstract fun intervalAttemptDao(): IntervalAttemptDao
    abstract fun chordAttemptDao(): ChordAttemptDao
    abstract fun scaleDegreeAttemptDao(): ScaleDegreeAttemptDao
    abstract fun progressionAttemptDao(): ProgressionAttemptDao
    abstract fun dictationAttemptDao(): DictationAttemptDao
    abstract fun rhythmAttemptDao(): RhythmAttemptDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "ear_trainer.db"

        fun create(context: Context): EarTrainerDatabase =
            Room.databaseBuilder(context, EarTrainerDatabase::class.java, NAME).build()
    }
}
