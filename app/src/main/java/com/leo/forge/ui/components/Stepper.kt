package com.leo.forge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leo.forge.ui.theme.Forge
import com.leo.forge.ui.theme.LocalHapticsEnabled
import com.leo.forge.ui.theme.NumericStyle
import com.leo.forge.ui.theme.pressScale

/**
 * Weight / rep entry.
 *
 * Pre-filled from the prescription, so the common case is no interaction at all. The steppers
 * exist for the day it is not, and the field is still directly typeable - correcting a number
 * should never need three taps.
 */
@Composable
fun ValueStepper(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHapticsEnabled.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Forge.colors.textTertiary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Rounded.Remove) {
                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStep(-1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = NumericStyle.copy(
                    fontSize = 22.sp,
                    color = Forge.colors.textPrimary,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                cursorBrush = SolidColor(Forge.colors.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.width(72.dp).padding(horizontal = 2.dp),
            )
            StepButton(Icons.Rounded.Add) {
                if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onStep(1)
            }
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction, pressedScale = 0.88f)
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Forge.colors.surface3)
            .border(1.dp, Forge.colors.outline, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = ripple(bounded = true), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Forge.colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}
