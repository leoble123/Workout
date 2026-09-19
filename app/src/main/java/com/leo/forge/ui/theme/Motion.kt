package com.leo.forge.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

/**
 * One motion vocabulary for the whole app.
 *
 * Springs rather than durations, so an interrupted animation retargets from its current
 * velocity instead of snapping - that continuity is most of what "buttery" actually means.
 */
object Motion {

    /** Buttons, toggles, anything under a fingertip. Fast, barely any overshoot. */
    fun <T> snappy(): AnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Cards moving, lists reordering. A touch of overshoot reads as physical. */
    fun <T> spatial(): AnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = 420f)

    /** Large surfaces: sheets, screen transitions. */
    fun <T> expressive(): AnimationSpec<T> = spring(dampingRatio = 0.68f, stiffness = 280f)

    /** Colour and alpha: never bouncy, that reads as a glitch. */
    fun <T> fade(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 500f)

    val offsetSpatial: AnimationSpec<IntOffset> =
        spring(dampingRatio = 0.78f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)

    val floatSnappy: AnimationSpec<Float> =
        spring(dampingRatio = 0.9f, stiffness = 1400f, visibilityThreshold = Spring.DefaultDisplacementThreshold)
}

/**
 * Press feedback that costs nothing.
 *
 * The scale is read inside a [graphicsLayer] lambda, so a press re-runs the draw phase only -
 * no recomposition and no relayout. Doing this the obvious way (a `scale()` modifier reading
 * state directly) invalidates composition on every animation frame.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(if (pressed) pressedScale else 1f, Motion.floatSnappy)
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
