package com.leo.forge.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.leo.forge.domain.model.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("forge_settings")

data class ForgeSettings(
    val units: Units = Units.KG,
    val autoStartRest: Boolean = true,
    val autoAdvance: Boolean = true,
    val oledBlack: Boolean = true,
    val haptics: Boolean = true,
    val soundOnRestEnd: Boolean = true,
    val keepScreenOn: Boolean = false,
    val restNudgeSeconds: Int = 15,
    val onboarded: Boolean = false,
    val gymSeedVersion: Int = 0,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val AUTO_REST = booleanPreferencesKey("auto_rest")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val OLED = booleanPreferencesKey("oled_black")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SOUND = booleanPreferencesKey("sound_rest_end")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val NUDGE = intPreferencesKey("rest_nudge")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val GYM_SEED = intPreferencesKey("gym_seed_version")
    }

    val settings: Flow<ForgeSettings> = context.dataStore.data.map { p ->
        ForgeSettings(
            units = p[Keys.UNITS]?.let { runCatching { Units.valueOf(it) }.getOrNull() } ?: Units.KG,
            autoStartRest = p[Keys.AUTO_REST] ?: true,
            autoAdvance = p[Keys.AUTO_ADVANCE] ?: true,
            oledBlack = p[Keys.OLED] ?: true,
            haptics = p[Keys.HAPTICS] ?: true,
            soundOnRestEnd = p[Keys.SOUND] ?: true,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            restNudgeSeconds = p[Keys.NUDGE] ?: 15,
            onboarded = p[Keys.ONBOARDED] ?: false,
            gymSeedVersion = p[Keys.GYM_SEED] ?: 0,
        )
    }

    suspend fun setUnits(u: Units) = edit { it[Keys.UNITS] = u.name }
    suspend fun setAutoStartRest(v: Boolean) = edit { it[Keys.AUTO_REST] = v }
    suspend fun setAutoAdvance(v: Boolean) = edit { it[Keys.AUTO_ADVANCE] = v }
    suspend fun setOled(v: Boolean) = edit { it[Keys.OLED] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.HAPTICS] = v }
    suspend fun setSound(v: Boolean) = edit { it[Keys.SOUND] = v }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setOnboarded(v: Boolean) = edit { it[Keys.ONBOARDED] = v }
    suspend fun setGymSeedVersion(v: Int) = edit { it[Keys.GYM_SEED] = v }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
