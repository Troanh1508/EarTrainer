package com.example.eartrainer.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IntervalAttemptEntity::class], version = 1, exportSchema = true)
abstract class EarTrainerDatabase : RoomDatabase() {
    abstract fun intervalAttemptDao(): IntervalAttemptDao

    companion object {
        fun create(context: Context): EarTrainerDatabase =
            Room.databaseBuilder(context, EarTrainerDatabase::class.java, "ear_trainer.db").build()
    }
}
