package com.example.eartrainer.ui.rhythm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.eartrainer.music.Rhythm
import com.example.eartrainer.music.RhythmCell
import com.example.eartrainer.ui.theme.EarTrainerTheme

/**
 * Draws a rhythm on a single-line percussion staff: noteheads with stems, notes shorter than a
 * beat beamed together within their beat, single eighths flagged, plus dots, rests and
 * barlines. Horizontal position follows time, so longer notes take more room. Each bar gets its
 * own line, which keeps sixteenths legible on a phone-width screen.
 *
 * Purely visual: callers provide the spoken description for accessibility.
 */
@Composable
fun RhythmNotation(rhythm: Rhythm, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rhythm.cellsByBar.forEach { bar ->
            Canvas(Modifier.fillMaxWidth().height(64.dp)) { drawBar(bar, color) }
        }
    }
}

/** Draws one 4/4 bar of [cells] across the full width, ending with a barline. */
private fun DrawScope.drawBar(cells: List<RhythmCell>, color: Color) {
    val staffY = size.height * 0.72f
    val padStart = 8.dp.toPx()
    val padEnd = 4.dp.toPx()
    // Room after the last beat keeps a final note or flag clear of the barline.
    val endGap = 14.dp.toPx()
    val beatWidth = (size.width - padStart - padEnd - endGap) / Rhythm.BEATS_PER_BAR
    val line = 1.dp.toPx()

    // Staff line and closing barline.
    drawLine(color.copy(alpha = color.alpha * 0.45f), Offset(0f, staffY), Offset(size.width, staffY), line)
    val barX = size.width - padEnd / 2
    drawLine(color, Offset(barX, staffY - 12.dp.toPx()), Offset(barX, staffY + 12.dp.toPx()), line * 1.5f)

    val glyphs = NotationGlyphs(this, color, staffY)
    // Each event sits a little right of where its time starts, leaving room for the barline.
    fun xOf(beat: Double) = padStart + (beat * beatWidth).toFloat() + beatWidth * 0.28f

    var cellStart = 0.0
    cells.forEach { cell ->
        var beat = cellStart
        val stemTops = mutableListOf<Pair<Float, Double>>() // stem x and note length, for beaming
        cell.events.forEach { event ->
            val x = xOf(beat)
            when {
                event.isRest && event.beats >= 1.0 -> glyphs.quarterRest(x)
                event.isRest -> glyphs.eighthRest(x)
                else -> {
                    val stemX = glyphs.note(x, hollow = event.beats >= 2.0, dotted = event.beats == 1.5)
                    if (event.beats < 1.0) stemTops += stemX to event.beats
                }
            }
            beat += event.beats
        }
        when {
            stemTops.size >= 2 -> glyphs.beams(stemTops)
            stemTops.size == 1 -> glyphs.flag(stemTops.single().first)
        }
        cellStart += cell.beats
    }
}

/** Glyph drawing in dp-based proportions, anchored on the staff line. */
private class NotationGlyphs(private val scope: DrawScope, private val color: Color, private val staffY: Float) {
    private val headWidth = with(scope) { 11.dp.toPx() }
    private val headHeight = with(scope) { 8.dp.toPx() }
    private val stemHeight = with(scope) { 31.dp.toPx() }
    private val stemWidth = with(scope) { 1.6.dp.toPx() }
    private val beamThickness = with(scope) { 4.dp.toPx() }
    private val beamGap = with(scope) { 2.6.dp.toPx() }
    private val strokeWidth = with(scope) { 2.dp.toPx() }
    private val unit = with(scope) { 1.dp.toPx() }

    val stemTop: Float get() = staffY - stemHeight

    /** Draws a notehead (filled, or hollow for halves) with an up-stem; returns the stem's x. */
    fun note(x: Float, hollow: Boolean, dotted: Boolean): Float = with(scope) {
        rotate(-20f, pivot = Offset(x, staffY)) {
            val topLeft = Offset(x - headWidth / 2, staffY - headHeight / 2)
            if (hollow) {
                drawOval(color, topLeft, Size(headWidth, headHeight), style = Stroke(width = 1.6f * unit))
            } else {
                drawOval(color, topLeft, Size(headWidth, headHeight))
            }
        }
        val stemX = x + headWidth / 2 - stemWidth / 2
        drawLine(color, Offset(stemX, staffY - unit), Offset(stemX, stemTop), stemWidth)
        if (dotted) drawCircle(color, radius = 1.7f * unit, center = Offset(x + headWidth / 2 + 4.5f * unit, staffY - 2 * unit))
        stemX
    }

    /** One beam across all stems; a second beam joins neighbouring sixteenths. */
    fun beams(stems: List<Pair<Float, Double>>) = with(scope) {
        val left = stems.first().first - stemWidth / 2
        val right = stems.last().first + stemWidth / 2
        drawRect(color, Offset(left, stemTop), Size(right - left, beamThickness))
        stems.zipWithNext().forEach { (a, b) ->
            if (a.second <= 0.25 && b.second <= 0.25) {
                val y = stemTop + beamThickness + beamGap
                drawRect(color, Offset(a.first - stemWidth / 2, y), Size(b.first - a.first + stemWidth, beamThickness))
            }
        }
    }

    /** The curved flag of a lone eighth note. */
    fun flag(stemX: Float) = with(scope) {
        val path = Path().apply {
            moveTo(stemX, stemTop)
            cubicTo(stemX + 2 * unit, stemTop + 6 * unit, stemX + 9 * unit, stemTop + 9 * unit, stemX + 6 * unit, stemTop + 17 * unit)
        }
        drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }

    /** A stylised quarter rest: a zig-zag with a hooked tail, centred on the staff line. */
    fun quarterRest(x: Float) = with(scope) {
        val path = Path().apply {
            moveTo(x - 2 * unit, staffY - 13 * unit)
            lineTo(x + 3 * unit, staffY - 6 * unit)
            lineTo(x - 2 * unit, staffY - 1 * unit)
            lineTo(x + 3 * unit, staffY + 5 * unit)
            quadraticTo(x - 5 * unit, staffY + 3 * unit, x - 1 * unit, staffY + 11 * unit)
        }
        drawPath(path, color, style = Stroke(width = 2.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** A stylised eighth rest: a dot with a slanted stroke. */
    fun eighthRest(x: Float) = with(scope) {
        drawCircle(color, radius = 2.2f * unit, center = Offset(x - 2.5f * unit, staffY - 6 * unit))
        val path = Path().apply {
            moveTo(x - 2.5f * unit, staffY - 4 * unit)
            quadraticTo(x + 1 * unit, staffY - 3.5f * unit, x + 3.5f * unit, staffY - 7.5f * unit)
            lineTo(x - 0.5f * unit, staffY + 7 * unit)
        }
        drawPath(path, color, style = Stroke(width = 1.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun RhythmNotationPreview() {
    EarTrainerTheme {
        RhythmNotation(
            Rhythm(
                listOf(
                    RhythmCell.QUARTER, RhythmCell.EIGHTHS, RhythmCell.QUARTER_REST, RhythmCell.EIGHTH_REST_EIGHTH,
                    RhythmCell.DOTTED_QUARTER_EIGHTH, RhythmCell.SIXTEENTHS, RhythmCell.EIGHTH_TWO_SIXTEENTHS,
                ),
            ),
        )
    }
}
