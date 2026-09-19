package com.leo.forge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.forge.domain.volume.Landmarks
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.NumericStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where a muscle's weekly volume sits against its landmarks.
 *
 * The state is carried by a word as well as a colour ("under" / "on track" / "over"),
 * because a bar that means something only if you can tell teal from coral is a bar that
 * fails for roughly one man in twelve.
 */
@Composable
fun VolumeMeter(
    label: String,
    sets: Int,
    landmarks: Landmarks,
    modifier: Modifier = Modifier,
) {
    val ceiling = (landmarks.mrv.coerceAtLeast(1) * 1.15f)
    val status: Triple<Color, String, Float> = when {
        sets < landmarks.mev -> Triple(Forge.colors.cool, "under", sets / ceiling)
        sets > landmarks.mrv -> Triple(Forge.colors.danger, "over", sets / ceiling)
        else -> Triple(Forge.colors.good, "on track", sets / ceiling)
    }
    val (color, word, fraction) = status
    val trackColor = Forge.colors.surface3
    val tickColor = Forge.colors.textTertiary

    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Forge.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.width(88.dp),
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(12.dp)
                .padding(horizontal = 6.dp)
        ) {
            val h = 9.dp.toPx()
            val r = 4.dp.toPx() // 4px rounded data-ends
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            val w = (size.width * fraction.coerceIn(0f, 1f))
            if (w > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(r * 2), h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                )
            }
            // Recessive landmark ticks: MEV is the floor a block starts on, MRV the ceiling.
            listOf(landmarks.mev, landmarks.mrv).forEach { mark ->
                if (mark <= 0) return@forEach
                val x = size.width * (mark / ceiling).coerceIn(0f, 1f)
                drawLine(
                    color = tickColor,
                    start = Offset(x, top - 2.dp.toPx()),
                    end = Offset(x, top + h + 2.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(76.dp)) {
            Text("$sets", style = NumericStyle.copy(fontSize = 14.sp), color = Forge.colors.textPrimary)
            Text(word, style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
        }
    }
}

@Immutable
data class ChartPoint(val x: Long, val y: Double)

/**
 * Estimated 1RM over time. One series, so no legend - the title names it - and only the
 * first, last and best points are labelled rather than every one. Drag to scrub: on a
 * touch screen that is what hover is.
 */
@Composable
fun E1rmChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = Forge.colors.accent,
    valueSuffix: String = "kg",
) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text(
                "Two sessions needed before there is a trend to draw.",
                style = MaterialTheme.typography.bodySmall,
                color = Forge.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    var scrubIndex by remember(points) { mutableIntStateOf(-1) }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val span = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
    // Pad the range so a flat-ish series does not look like a cliff.
    val lo = minY - span * 0.15
    val hi = maxY + span * 0.15
    val gridColor = Forge.colors.outline
    val surface = Forge.colors.surface1

    Column(modifier) {
        Box {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .pointerInput(points) {
                        detectHorizontalDragGestures(
                            onDragEnd = { scrubIndex = -1 },
                            onDragCancel = { scrubIndex = -1 },
                        ) { change, _ ->
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            scrubIndex = (f * (points.size - 1)).roundToInt()
                        }
                    }
                    .pointerInput(points) {
                        detectTapGestures { pos ->
                            val f = (pos.x / size.width).coerceIn(0f, 1f)
                            scrubIndex = (f * (points.size - 1)).roundToInt()
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                fun px(i: Int) = w * (i.toFloat() / (points.size - 1))
                fun py(v: Double) = (h - ((v - lo) / (hi - lo) * h)).toFloat()

                // Recessive gridlines only - no axis box, no ticks.
                repeat(3) { i ->
                    val y = h * (i + 1) / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }

                val line = Path().apply {
                    points.forEachIndexed { i, p ->
                        if (i == 0) moveTo(px(i), py(p.y)) else lineTo(px(i), py(p.y))
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(px(points.lastIndex), h)
                    lineTo(px(0), h)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), Color.Transparent)),
                )
                drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

                // Selective markers: best and latest only.
                val bestIndex = points.indexOfFirst { it.y == maxY }
                setOf(bestIndex, points.lastIndex).forEach { i ->
                    drawCircle(surface, radius = 6.dp.toPx(), center = Offset(px(i), py(points[i].y)))
                    drawCircle(color, radius = 4.5f.dp.toPx(), center = Offset(px(i), py(points[i].y)))
                }

                val si = scrubIndex
                if (si in points.indices) {
                    val x = px(si)
                    drawLine(color.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                    drawCircle(surface, radius = 7.dp.toPx(), center = Offset(x, py(points[si].y)))
                    drawCircle(color, radius = 5.dp.toPx(), center = Offset(x, py(points[si].y)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val shown = points.getOrNull(scrubIndex) ?: points.last()
            Text(
                if (scrubIndex in points.indices) "Selected" else "Latest",
                style = MaterialTheme.typography.labelSmall,
                color = Forge.colors.textTertiary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${shown.y.roundToInt()} $valueSuffix",
                style = NumericStyle.copy(fontSize = 16.sp),
                color = Forge.colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            val delta = points.last().y - points.first().y
            val sign = if (delta >= 0) "+" else ""
            Text(
                "$sign${delta.roundToInt()} $valueSuffix over ${points.size} sessions",
                style = MaterialTheme.typography.labelSmall,
                color = if (delta >= 0) Forge.colors.good else Forge.colors.textTertiary,
            )
        }
    }
}

/** Compact sparkline for list rows. Single series, no axes, no labels. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = Forge.colors.accent) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    val lo = values.min()
    val hi = values.max()
    val span = (hi - lo).takeIf { abs(it) > 0.0001 } ?: 1.0
    Canvas(modifier) {
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * (i.toFloat() / (values.size - 1))
            val y = (size.height - ((v - lo) / span * size.height)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Change in estimated 1RM per lift, gains right of the line and losses left.
 *
 * Diverging around a neutral zero: two hues with a grey midpoint, never a rainbow. Every bar
 * carries its own name and signed value, so the direction is readable without relying on
 * telling teal from coral.
 */
@Composable
fun MoversChart(
    rows: List<Triple<String, Double, Double>>,
    modifier: Modifier = Modifier,
    unitLabel: String = "kg",
) {
    if (rows.isEmpty()) {
        Box(modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
            Text(
                "Two sessions on a lift before its trend means anything.",
                style = MaterialTheme.typography.bodySmall,
                color = Forge.colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val maxAbs = rows.maxOf { abs(it.second) }.coerceAtLeast(0.001)
    val gain = Forge.colors.good
    val loss = Forge.colors.danger
    val zeroLine = Forge.colors.outline
    val track = Forge.colors.surface3

    Column(modifier.fillMaxWidth()) {
        rows.forEach { (name, delta, pct) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Forge.colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.width(104.dp),
                )
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .padding(horizontal = 6.dp)
                ) {
                    val h = 10.dp.toPx()
                    val r = 4.dp.toPx()
                    val top = (size.height - h) / 2f
                    val mid = size.width / 2f
                    drawRoundRect(
                        color = track,
                        topLeft = Offset(0f, top + h / 2f - 1.dp.toPx()),
                        size = Size(size.width, 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                    )
                    val extent = (abs(delta) / maxAbs).toFloat() * (mid - 2.dp.toPx())
                    if (extent > 0.5f) {
                        val left = if (delta >= 0) mid else mid - extent
                        drawRoundRect(
                            color = if (delta >= 0) gain else loss,
                            topLeft = Offset(left, top),
                            size = Size(extent, h),
                            cornerRadius = CornerRadius(r, r),
                        )
                    }
                    // Neutral midpoint, drawn last so it always reads.
                    drawLine(
                        color = zeroLine,
                        start = Offset(mid, top - 2.dp.toPx()),
                        end = Offset(mid, top + h + 2.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                Text(
                    (if (delta >= 0) "+" else "") + "${pct.roundToInt()}%",
                    style = NumericStyle.copy(fontSize = 12.sp),
                    color = if (delta >= 0) Forge.colors.good else Forge.colors.danger,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Change in estimated 1RM, first logged session to most recent.",
            style = MaterialTheme.typography.labelSmall,
            color = Forge.colors.textTertiary,
        )
    }
}
