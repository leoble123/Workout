package com.leo.forge.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.leo.forge.timer.RestState
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.LocalHapticsEnabled
import com.leo.forge.ui.theme.NumericStyle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil

/**
 * Whole seconds remaining, recomputed from the deadline.
 *
 * Two things make this cheap. It sleeps until the *next second boundary* rather than polling
 * on a fixed interval, so a 3-minute rest costs 180 wake-ups and not 10,800. And it is wrapped
 * in [repeatOnLifecycle], so the moment the screen is off or the app is backgrounded the loop
 * is cancelled outright - the OS alarm is what guarantees the buzz, not this.
 */
@Composable
fun rememberRestSeconds(running: RestState.Running): State<Int> {
    val seconds = remember(running) {
        mutableIntStateOf(ceil(running.remainingMillis() / 1000.0).toInt())
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(running, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (currentCoroutineContext().isActive) {
                val remaining = running.remainingMillis()
                seconds.intValue = ceil(remaining / 1000.0).toInt()
                if (remaining <= 0L) break
                val toNextSecond = remaining % 1000L
                delay(if (toNextSecond == 0L) 1000L else toNextSecond)
            }
        }
    }
    return seconds
}

/**
 * The countdown ring.
 *
 * One [Animatable] is driven linearly across the entire remaining rest, and its value is read
 * inside the draw lambda. That means the ring is re-drawn per frame but never re-composed and
 * never re-laid-out, which is the difference between a smooth arc and a warm phone.
 */
@Composable
fun RestRing(
    running: RestState.Running,
    modifier: Modifier = Modifier,
    color: Color = Forge.colors.accent,
    trackColor: Color = Forge.colors.surface3,
    strokeWidth: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val progress = remember(running) { Animatable(1f) }
    LaunchedEffect(running) {
        val remaining = running.remainingMillis()
        val total = (running.totalSeconds * 1000L).coerceAtLeast(1L)
        progress.snapTo((remaining.toFloat() / total).coerceIn(0f, 1f))
        if (remaining > 0) {
            progress.animateTo(0f, tween(durationMillis = remaining.toInt(), easing = LinearEasing))
        }
    }
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val inset = sw / 2f
        val arcSize = Size(size.width - sw, size.height - sw)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = -360f * progress.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
    }
}

fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Docked rest control. Deliberately reachable with a thumb and nothing else on it: add
 * time, cut it short, and the number. Everything else can wait until you are not breathing hard.
 */
@Composable
fun RestBar(
    running: RestState.Running,
    modifier: Modifier = Modifier,
    onNudge: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val seconds by rememberRestSeconds(running)
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    val done = seconds <= 0

    // Deliberately does NOT clear the timer: the finished bar is the "go" cue, and it
    // stays until it is dismissed or the next set replaces it.
    LaunchedEffect(done) {
        if (done && hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Forge.colors.surface2)
            .border(1.dp, if (done) Forge.colors.accent else Forge.colors.outline, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RestRing(running, Modifier.size(52.dp))
            Text(
                if (done) "GO" else "$seconds",
                style = NumericStyle.copy(fontSize = if (done) 15.sp else 17.sp),
                color = if (done) Forge.colors.accent else Forge.colors.textPrimary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (done) "Rest over" else "Resting",
                style = MaterialTheme.typography.labelSmall,
                color = Forge.colors.textTertiary,
            )
            Text(
                running.label.ifBlank { "Next set" },
                style = MaterialTheme.typography.titleMedium,
                color = Forge.colors.textPrimary,
                maxLines = 1,
            )
        }
        if (!done) {
            SecondaryButton("+15s", Modifier.padding(end = 8.dp)) { onNudge(15) }
        }
        SecondaryButton(
            if (done) "Dismiss" else "Skip",
            tint = if (done) Forge.colors.accent else Forge.colors.textSecondary,
        ) { onSkip() }
    }
}

/** Elapsed workout time. Same one-wake-per-second contract as the rest countdown. */
@Composable
fun rememberElapsedSeconds(startedAt: Long): State<Int> {
    val value = remember(startedAt) {
        mutableIntStateOf(((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(0))
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(startedAt, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (currentCoroutineContext().isActive) {
                val elapsedMs = System.currentTimeMillis() - startedAt
                value.intValue = (elapsedMs / 1000L).toInt().coerceAtLeast(0)
                delay(1000L - (elapsedMs % 1000L))
            }
        }
    }
    return value
}
