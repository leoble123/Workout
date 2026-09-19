package com.leo.forge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Core palette. Dark-first: this is an app used under gym lighting, at arm's length,
// with one hand, and on an OLED panel where true black costs no power to display.
val Volt = Color(0xFFD7FF3E)
val VoltPressed = Color(0xFFBEE62F)
val VoltInk = Color(0xFF0A0D06)

val Ink = Color(0xFF06070A)
val InkOled = Color(0xFF000000)
val Surface1 = Color(0xFF101319)
val Surface2 = Color(0xFF171B23)
val Surface3 = Color(0xFF212633)
val Outline = Color(0xFF2A3040)

val TextPrimary = Color(0xFFF3F5F8)
val TextSecondary = Color(0xFF99A2B2)
val TextTertiary = Color(0xFF5F6879)

val Danger = Color(0xFFFF6B6B)
val Warn = Color(0xFFFFB454)
val Good = Color(0xFF3DDC97)
val PrGold = Color(0xFFFFD166)
val Cool = Color(0xFF6E9BFF)

@Immutable
data class ForgeColors(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val danger: Color,
    val warn: Color,
    val good: Color,
    val pr: Color,
    val cool: Color,
)

fun forgeDark(oled: Boolean) = ForgeColors(
    background = if (oled) InkOled else Ink,
    surface1 = if (oled) Color(0xFF0B0D12) else Surface1,
    surface2 = if (oled) Color(0xFF13161D) else Surface2,
    surface3 = Surface3,
    outline = Outline,
    accent = Volt,
    accentPressed = VoltPressed,
    onAccent = VoltInk,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    danger = Danger,
    warn = Warn,
    good = Good,
    pr = PrGold,
    cool = Cool,
)
