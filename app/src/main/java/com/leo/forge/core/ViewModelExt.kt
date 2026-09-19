package com.leo.forge.core

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.leo.forge.ForgeApp
import com.leo.forge.data.AppContainer

val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForgeApp).container

/** Formats a kg value the way a gym floor reads it: no trailing ".0". */
fun Double.kgText(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else String.format("%.1f", this)

fun Double.weightText(isKg: Boolean): String {
    val v = if (isKg) this else this * 2.2046226218
    return v.kgText()
}
