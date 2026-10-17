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
 */
@Database(
    entities = [IntervalAttemptEntity::class, ChordAttemptEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class EarTrainerDatabase : RoomDatabase() {
    abstract fun intervalAttemptDao(): IntervalAttemptDao
    abstract fun chordAttemptDao(): ChordAttemptDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "ear_trainer.db"

        fun create(context: Context): EarTrainerDatabase =
            Room.databaseBuilder(context, EarTrainerDatabase::class.java, NAME).build()
    }
}
