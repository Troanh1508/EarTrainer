package com.troanhmolo.eartrainer.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.troanhmolo.eartrainer.R
import com.troanhmolo.eartrainer.data.history.SessionSummary
import com.troanhmolo.eartrainer.ui.theme.weakSpot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal fun percent(fraction: Float) = "${(fraction * 100).roundToInt()}%"

/** A ring filled to [accuracy], with the percentage in the middle; an empty ring when null. */
@Composable
internal fun AccuracyDonut(
    accuracy: Float?,
    /** What the percentage counts, shown under it, e.g. "of chords right". */
    caption: String,
    description: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 136.dp,
    thickness: Dp = 14.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(diameter).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = thickness.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (accuracy != null && accuracy > 0f) {
                drawArc(
                    fill, startAngle = -90f, sweepAngle = 360f * accuracy, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                accuracy?.let(::percent) ?: "—",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            // Narrower than the hole, so a long caption wraps instead of touching the ring.
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = diameter - thickness * 2 - 24.dp),
            )
        }
    }
}

/**
 * Accuracy per session as a line over the last [sessions] (oldest first). The scale runs from
 * 50% to 100% while every session stays above half, so typical changes are visible, and from
 * 0% otherwise.
 */
@Composable
internal fun AccuracyTrendChart(sessions: List<SessionSummary>, modifier: Modifier = Modifier) {
    val values = sessions.map { it.itemsCorrect.toFloat() / it.items }
    val low = if (values.min() >= 0.5f) 0.5f else 0f
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dateFormat = remember { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"), Locale.getDefault()) }
    val description = pluralStringResource(R.plurals.progress_trend_description, sessions.size, sessions.size, values.joinToString(", ") { percent(it) })

    Column(modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(36.dp).height(CHART_HEIGHT),
            ) {
                listOf(1f, (1f + low) / 2, low).forEach { Text(percent(it), style = labelStyle, color = labelColor) }
            }
            Canvas(Modifier.weight(1f).height(CHART_HEIGHT)) {
                // Gridlines line up with the centers of the scale labels beside the chart.
                val top = LABEL_INSET.toPx()
                val bottom = size.height - LABEL_INSET.toPx()
                val edge = 8.dp.toPx()
                fun y(value: Float) = bottom - (value - low) / (1f - low) * (bottom - top)
                listOf(1f, (1f + low) / 2, low).forEach { level ->
                    drawLine(grid, Offset(0f, y(level)), Offset(size.width, y(level)), strokeWidth = 1.dp.toPx())
                }
                val step = (size.width - 2 * edge) / (values.size - 1)
                val points = values.mapIndexed { index, value -> Offset(edge + index * step, y(value)) }
                val outline = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = Path().apply {
                    addPath(outline)
                    lineTo(points.last().x, bottom)
                    lineTo(points.first().x, bottom)
                    close()
                }
                drawPath(area, line.copy(alpha = 0.12f))
                drawPath(outline, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(surface, radius = 6.dp.toPx(), center = points.last())
                drawCircle(line, radius = 6.dp.toPx(), center = points.last(), style = Stroke(3.dp.toPx()))
            }
        }
        Row(Modifier.padding(start = 44.dp)) {
            Text(dateFormat.format(Date(sessions.first().startedAtMillis)), style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.progress_trend_latest), style = labelStyle, color = labelColor)
        }
    }
}

/**
 * One item's accuracy as a bar, orange when it needs work; unpracticed items get no bar. With
 * [onClick], a practiced row opens more detail and shows a chevron.
 */
@Composable
internal fun AccuracyBarRow(item: AccuracyItem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val accuracy = item.accuracy
    val barColor = if (item.needsWork) MaterialTheme.colorScheme.weakSpot else MaterialTheme.colorScheme.primary
    val clickLabel = stringResource(R.string.progress_show_mistakes)
    val clickable = if (onClick != null && accuracy != null) {
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier.then(clickable).semantics(mergeDescendants = true) {}.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(132.dp).padding(end = 12.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (accuracy != null) {
                Text(
                    stringResource(R.string.progress_item_score, item.correct, item.attempts),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (accuracy == null) {
            Text(
                stringResource(R.string.progress_not_practiced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(
                Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(Modifier.fillMaxWidth(accuracy).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(barColor))
            }
            Text(
                percent(accuracy),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.needsWork) barColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
        }
        if (onClick != null) {
            // Unpracticed rows get the same space, so every bar ends at the same place.
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (accuracy != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Explains the two bar colors. */
@Composable
internal fun AccuracyLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendEntry(
            MaterialTheme.colorScheme.weakSpot,
            stringResource(R.string.progress_legend_needs_work, (AccuracyItem.WEAK_THRESHOLD * 100).roundToInt()),
        )
        LegendEntry(MaterialTheme.colorScheme.primary, stringResource(R.string.progress_legend_on_track))
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CHART_HEIGHT = 132.dp

/** Half the height of a scale label, so gridlines meet the labels' centers. */
private val LABEL_INSET = 8.dp
