package com.example.eartrainer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.example.eartrainer.audio.AssetSampleLoader
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.audio.AudioTrackPlayer
import com.example.eartrainer.audio.PcmRenderer
import com.example.eartrainer.audio.SampledInstrumentRenderer
import com.example.eartrainer.audio.ToneSynthesizer
import com.example.eartrainer.data.history.EarTrainerDatabase
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.RoomPracticeHistory
import com.example.eartrainer.data.settings.ChordSettingsStore
import com.example.eartrainer.data.settings.IntervalSettingsStore
import com.example.eartrainer.data.settings.ScaleDegreeSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Hand-wired app-wide dependencies; small enough that a DI framework would add nothing. */
class AppContainer(private val context: Context) {
    /** Lives as long as the process, for work that must outlive a screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { EarTrainerDatabase.create(context) }

    val practiceHistory: PracticeHistory by lazy {
        RoomPracticeHistory(database, applicationScope)
    }

    val intervalSettings by lazy { IntervalSettingsStore(context.settingsDataStore) }

    val chordSettings by lazy { ChordSettingsStore(context.settingsDataStore) }

    val scaleDegreeSettings by lazy { ScaleDegreeSettingsStore(context.settingsDataStore) }

    /**
     * The piano is decoded once per process, in the background, and shared by every player.
     * If decoding fails the app still works, using the built-in synthesizer instead.
     */
    private val instrument: Deferred<PcmRenderer> by lazy {
        applicationScope.async {
            try {
                SampledInstrumentRenderer(AssetSampleLoader(context.assets).loadUprightPiano())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Piano samples could not be loaded; using the synthesizer", e)
                ToneSynthesizer()
            }
        }
    }

    /** Starts decoding the piano so it is ready before the first exercise needs it. */
    fun preloadInstrument() {
        instrument.start()
    }

    /** Each exercise screen owns and releases its own player. */
    fun createAudioPlayer(): AudioPlayer {
        val instrument = instrument
        return AudioTrackPlayer(context, renderer = { instrument.await() })
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}

class EarTrainerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.preloadInstrument() }
    }
}
