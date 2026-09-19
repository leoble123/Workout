package com.leo.forge.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.leo.forge.domain.model.Load

/** A stored kilogram value rendered in the active gym's unit, with no trailing ".0". */
@Composable
@ReadOnlyComposable
fun loadText(kg: Double): String = Load.format(Load.toDisplay(kg, LocalUnits.current))

/** "kg" or "lb". */
@Composable
@ReadOnlyComposable
fun unitLabel(): String = LocalUnits.current.display

@Composable
@ReadOnlyComposable
fun loadWithUnit(kg: Double): String = "${loadText(kg)} ${unitLabel()}"

/** Total volume in the unit a person would say out loud: tonnes, or thousands of pounds. */
@Composable
@ReadOnlyComposable
fun tonnageText(kg: Double): Pair<String, String> = Load.formatTonnage(kg, LocalUnits.current)
