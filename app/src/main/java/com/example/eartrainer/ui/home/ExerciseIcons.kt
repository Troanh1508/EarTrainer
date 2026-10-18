package com.example.eartrainer.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.example.eartrainer.exercise.ExerciseKind

/**
 * Line icons for the exercises, after the redesign: two notes at different heights for an
 * interval, three notes on one stem for a chord, a staircase of scale steps, blocks of chords
 * in a row, a pair of notes for dictation, and bars of different lengths for rhythm. Drawn as
 * strokes, so [androidx.compose.material3.Icon] can tint them.
 */
private val PATHS = mapOf(
    ExerciseKind.INTERVALS to "M8.5 17a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM8.5 17V7M18.5 12a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM18.5 12V3",
    ExerciseKind.CHORDS to "M11.4 19a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0zM11.4 14a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0zM11.4 9a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0zM11.4 19V3",
    ExerciseKind.SCALE_DEGREES to "M3 20h4v-4h4v-4h4V8h4V4",
    ExerciseKind.PROGRESSIONS to "M4 7h4v10H4zM10 5h4v14h-4zM16 9h4v6h-4z",
    ExerciseKind.DICTATION to "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
    ExerciseKind.RHYTHM to "M5 20V8M12 20V4M19 20v-9M3 20h18",
)

private val icons = mutableMapOf<ExerciseKind, ImageVector>()

val ExerciseKind.icon: ImageVector
    get() = icons.getOrPut(this) {
        ImageVector.Builder(name = "Exercise.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(PATHS.getValue(this)),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
