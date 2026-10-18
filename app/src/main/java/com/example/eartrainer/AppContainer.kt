package com.example.eartrainer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import com.example.eartrainer.audio.AssetSampleLoader
import com.example.eartrainer.audio.AudioPlayer
import com.example.eartrainer.audio.AudioTrackPlayer
import com.example.eartrainer.audio.Instrument
import com.example.eartrainer.audio.PcmRenderer
import com.example.eartrainer.audio.SampledInstrumentRenderer
import com.example.eartrainer.audio.ToneSynthesizer
import com.example.eartrainer.data.backup.AppDataBackup
import com.example.eartrainer.data.backup.ContentResolverDocuments
import com.example.eartrainer.data.backup.DocumentStreams
import com.example.eartrainer.data.backup.RoomDataBackup
import com.example.eartrainer.data.history.EarTrainerDatabase
import com.example.eartrainer.data.history.PracticeHistory
import com.example.eartrainer.data.history.RoomPracticeHistory
import com.example.eartrainer.data.history.WeakSpots
import com.example.eartrainer.data.settings.ChordSettingsStore
import com.example.eartrainer.data.settings.DictationSettingsStore
import com.example.eartrainer.data.settings.GoalSettingsStore
import com.example.eartrainer.data.settings.IntervalSettingsStore
import com.example.eartrainer.data.settings.ProgressionSettingsStore
import com.example.eartrainer.data.settings.RhythmSettingsStore
import com.example.eartrainer.data.settings.ScaleDegreeSettingsStore
import com.example.eartrainer.data.settings.SoundSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Hand-wired app-wide dependencies; small enough that a DI framework would add nothing. */
class AppContainer(private val context: Context) {
    /** Lives as long as the process, for work that must outlive a screen. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { EarTrainerDatabase.create(context) }

    val practiceHistory: PracticeHistory by lazy {
        RoomPracticeHistory(database, applicationScope)
    }

    /** Selection weights for weak-spot practice, kept current from history from app start. */
    val weakSpots = WeakSpots(practiceHistory, applicationScope)

    val intervalSettings by lazy { IntervalSettingsStore(context.settingsDataStore) }

    val chordSettings by lazy { ChordSettingsStore(context.settingsDataStore) }

    val scaleDegreeSettings by lazy { ScaleDegreeSettingsStore(context.settingsDataStore) }

    val progressionSettings by lazy { ProgressionSettingsStore(context.settingsDataStore) }

    val dictationSettings by lazy { DictationSettingsStore(context.settingsDataStore) }

    val rhythmSettings by lazy { RhythmSettingsStore(context.settingsDataStore) }

    /** Export and import of history and settings; Auto Backup needs no code, only the backup rules. */
    val dataBackup: AppDataBackup by lazy { RoomDataBackup(database, context.settingsDataStore) }

    val documents: DocumentStreams by lazy { ContentResolverDocuments(context.contentResolver) }

    val soundSettings by lazy { SoundSettingsStore(context.settingsDataStore) }

    val goalSettings by lazy { GoalSettingsStore(context.settingsDataStore) }

    /**
     * Each instrument is decoded at most once per process, in the background, and shared by
     * every player. Only instruments the user picks are decoded.
     */
    private val instruments = ConcurrentHashMap<Instrument, Deferred<PcmRenderer>>()

    private fun rendererFor(instrument: Instrument): Deferred<PcmRenderer> =
        instruments.computeIfAbsent(instrument) {
            applicationScope.async {
                try {
                    SampledInstrumentRenderer(AssetSampleLoader(context.assets).load(instrument))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The app still works, using the built-in synthesizer instead.
                    Log.w(TAG, "${instrument.displayName} samples could not be loaded; using the synthesizer", e)
                    ToneSynthesizer()
                }
            }
        }

    /**
     * Decodes the chosen instrument at launch, and a newly chosen one as soon as it is picked,
     * so it is ready before the next prompt plays.
     */
    fun preloadInstrument() {
        applicationScope.launch {
            soundSettings.settings.map { it.instrument }.distinctUntilChanged().collect { rendererFor(it) }
        }
    }

    /** Each exercise screen owns and releases its own player; every prompt uses the current instrument. */
    fun createAudioPlayer(): AudioPlayer =
        AudioTrackPlayer(context, renderer = { rendererFor(soundSettings.settings.first().instrument).await() })

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
