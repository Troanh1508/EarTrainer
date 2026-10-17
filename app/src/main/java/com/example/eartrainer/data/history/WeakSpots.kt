package com.example.eartrainer.data.history

import com.example.eartrainer.exercise.ChordAnswer
import com.example.eartrainer.exercise.ItemResult
import com.example.eartrainer.exercise.WeakSpotWeighting
import com.example.eartrainer.music.DiatonicChord
import com.example.eartrainer.music.Interval
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.music.ScaleDegree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Up-to-date selection weights per exercise, derived from recent history. They refresh by
 * themselves whenever a new answer is saved, and question generators read the latest value
 * each time they pick a question. Collection starts when this is created, so weights are ready
 * before the first question of a session.
 */
class WeakSpots(history: PracticeHistory, private val scope: CoroutineScope) {

    val intervals: StateFlow<Map<Interval, Double>> = weightsFrom(history.recentIntervalResults(RECENT_ANSWERS))
    val chords: StateFlow<Map<ChordAnswer, Double>> = weightsFrom(history.recentChordResults(RECENT_ANSWERS))
    val scaleDegrees: StateFlow<Map<ScaleDegree, Double>> = weightsFrom(history.recentScaleDegreeResults(RECENT_ANSWERS))
    val progressionChords: StateFlow<Map<DiatonicChord, Double>> = weightsFrom(history.recentProgressionResults(RECENT_ANSWERS))
    val dictationDegrees: StateFlow<Map<ScaleDegree, Double>> = weightsFrom(history.recentDictationResults(RECENT_ANSWERS))
    val rhythmCells: StateFlow<Map<RhythmCell, Double>> = weightsFrom(history.recentRhythmResults(RECENT_ANSWERS))

    private fun <T> weightsFrom(results: Flow<List<ItemResult<T>>>): StateFlow<Map<T, Double>> =
        results.map { WeakSpotWeighting.weights(it) }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    companion object {
        /**
         * Only recent answers count, so weights follow current ability: an item that used to be
         * hard but is now answered reliably stops being favoured.
         */
        const val RECENT_ANSWERS = 300

        /** A lookup for generators: the item's weight, or the unseen weight if it has no history yet. */
        fun <T> StateFlow<Map<T, Double>>.asWeightLookup(): (T) -> Double = { value[it] ?: WeakSpotWeighting.UNSEEN_WEIGHT }
    }
}
