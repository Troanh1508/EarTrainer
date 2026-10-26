package com.troanhmolo.eartrainer.exercise

import com.troanhmolo.eartrainer.music.ChordInversion
import com.troanhmolo.eartrainer.music.ChordQuality
import com.troanhmolo.eartrainer.music.ListeningPrompt
import com.troanhmolo.eartrainer.music.Pitch

/** How a chord question's notes are played. */
enum class ChordStyle(val displayName: String) {
    BLOCK("Block chord"),
    ARPEGGIATED("Arpeggio"),
}

/** What the listener is asked to name. */
enum class ChordTarget(val displayName: String) {
    QUALITY("Chord quality"),
    INVERSION("Inversion"),
}

/** An answer to a chord question: either a quality or an inversion, depending on the target. */
sealed interface ChordAnswer {
    val displayName: String

    data class Quality(val quality: ChordQuality) : ChordAnswer {
        override val displayName: String get() = quality.displayName
    }

    data class Inversion(val inversion: ChordInversion) : ChordAnswer {
        override val displayName: String get() = inversion.displayName
    }
}

data class ChordQuestion(
    /** Lowest sounding note. */
    val bass: Pitch,
    val quality: ChordQuality,
    val inversion: ChordInversion,
    val style: ChordStyle,
    val target: ChordTarget,
    override val choices: List<ChordAnswer>,
) : ExerciseQuestion<ChordAnswer> {

    init {
        require(inversion in quality.inversions) { "${quality.displayName} chords have no ${inversion.displayName}" }
        require(bass.midi + quality.offsetsAboveBass(inversion).last() in Pitch.MIDI_RANGE) { "Chord exceeds the MIDI range" }
        requireValidChoices(choices, correctAnswer)
    }

    /** Sounding notes from the bass up, in close position. */
    val pitches: List<Pitch> get() = quality.offsetsAboveBass(inversion).map(bass::transpose)

    /** Name of the chord root, spelled with sharps (e.g. "D♯"). */
    val rootName: String get() = bass.transpose(quality.rootOffsetAboveBass(inversion)).pitchClassName

    override val correctAnswer: ChordAnswer
        get() = when (target) {
            ChordTarget.QUALITY -> ChordAnswer.Quality(quality)
            ChordTarget.INVERSION -> ChordAnswer.Inversion(inversion)
        }

    override val prompt: ListeningPrompt
        get() = when (style) {
            ChordStyle.BLOCK -> ListeningPrompt.simultaneous(pitches)
            ChordStyle.ARPEGGIATED -> ListeningPrompt.arpeggiated(pitches)
        }
}
