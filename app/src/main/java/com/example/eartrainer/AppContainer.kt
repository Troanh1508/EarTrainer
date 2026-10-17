package com.example.eartrainer

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.audio.AudioTrackPlayer
import com.example.eartrainer.data.history.EarTrainerDatabase
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.RoomPracticeHistory
import com.example.eartrainer.data.settings.IntervalSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Hand-wired app-wide dependencies; small enough that a DI framework would add nothing. */
class AppContainer(private val context: Context) {
    /** Lives as long as the process, for work that must outlive a screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { EarTrainerDatabase.create(context) }

    val practiceHistory: PracticeHistory by lazy {
        RoomPracticeHistory(database.intervalAttemptDao(), applicationScope)
    }

    val intervalSettings by lazy { IntervalSettingsStore(context.settingsDataStore) }

    /** Each exercise screen owns and releases its own player. */
    fun createAudioPlayer(): AudioPlayer = AudioTrackPlayer(context)
}

class EarTrainerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
