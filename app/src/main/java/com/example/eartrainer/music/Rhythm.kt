package com.example.eartrainer.music

/** One sounded note or rest, with its length in beats (a quarter note is one beat). */
data class RhythmEvent(val beats: Double, val isRest: Boolean = false)

private fun note(beats: Double) = RhythmEvent(beats)
private fun rest(beats: Double) = RhythmEvent(beats, isRest = true)

/**
 * A building block of rhythms, one or two beats long. Rhythms are written as sequences of these
 * so they always notate cleanly: beams stay within a beat, and two-beat figures start on beat 1
 * or 3 of a 4/4 bar.
 */
enum class RhythmCell(val events: List<RhythmEvent>, val displayName: String) {
    QUARTER(listOf(note(1.0)), "quarter"),
    EIGHTHS(listOf(note(0.5), note(0.5)), "two eighths"),
    QUARTER_REST(listOf(rest(1.0)), "quarter rest"),
    HALF(listOf(note(2.0)), "half"),
    DOTTED_QUARTER_EIGHTH(listOf(note(1.5), note(0.5)), "dotted quarter and eighth"),
    EIGHTH_REST_EIGHTH(listOf(rest(0.5), note(0.5)), "eighth rest and eighth"),
    SIXTEENTHS(listOf(note(0.25), note(0.25), note(0.25), note(0.25)), "four sixteenths"),
    EIGHTH_TWO_SIXTEENTHS(listOf(note(0.5), note(0.25), note(0.25)), "eighth and two sixteenths"),
    TWO_SIXTEENTHS_EIGHTH(listOf(note(0.25), note(0.25), note(0.5)), "two sixteenths and eighth");

    /** Length in whole beats: 1 or 2. */
    val beats: Int get() = events.sumOf { it.beats }.toInt()

    val noteCount: Int get() = events.count { !it.isRest }
}

/** A rhythm in 4/4, as a sequence of cells filling whole bars. */
data class Rhythm(val cells: List<RhythmCell>) {

    init {
        require(cells.isNotEmpty()) { "A rhythm needs at least one cell" }
        require(totalBeats % BEATS_PER_BAR == 0) { "A rhythm must fill whole bars, was $totalBeats beats" }
        require(startBeats.zip(cells).all { (start, cell) -> cell.beats == 1 || start % 2 == 0 }) {
            "Two-beat cells must start on beat 1 or 3"
        }
    }

    val totalBeats: Int get() = cells.sumOf { it.beats }
    val bars: Int get() = totalBeats / BEATS_PER_BAR

    /** Beat (from 0) on which each cell starts. */
    val startBeats: List<Int> get() = cells.runningFold(0) { beat, cell -> beat + cell.beats }.dropLast(1)

    /** Every note and rest with the beat it starts on. */
    val timeline: List<Pair<Double, RhythmEvent>>
        get() = buildList {
            var beat = 0.0
            cells.flatMap { it.events }.forEach { event ->
                add(beat to event)
                beat += event.beats
            }
        }

    /** The cells of each bar in turn; cells never cross a barline, since bars are filled exactly. */
    val cellsByBar: List<List<RhythmCell>>
        get() = startBeats.zip(cells).groupBy({ (start, _) -> start / BEATS_PER_BAR }, { (_, cell) -> cell }).values.toList()

    /** Spoken description, e.g. "quarter, two eighths, quarter rest, quarter". */
    val description: String get() = cells.joinToString(", ") { it.displayName }

    companion object {
        const val BEATS_PER_BAR = 4
    }
}
