package com.leo.forge.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leo.forge.core.container
import com.leo.forge.data.importer.HevyCsvImporter
import com.leo.forge.data.prefs.ForgeSettings
import com.leo.forge.data.prefs.SettingsStore
import com.leo.forge.domain.model.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Running : ImportUiState
    data class Done(val report: HevyCsvImporter.Report) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

class SettingsViewModel(
    private val store: SettingsStore,
    private val importer: HevyCsvImporter,
) : ViewModel() {

    val settings: StateFlow<ForgeSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForgeSettings())

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    fun setUnits(u: Units) = viewModelScope.launch { store.setUnits(u) }
    fun setAutoStartRest(v: Boolean) = viewModelScope.launch { store.setAutoStartRest(v) }
    fun setAutoAdvance(v: Boolean) = viewModelScope.launch { store.setAutoAdvance(v) }
    fun setOled(v: Boolean) = viewModelScope.launch { store.setOled(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { store.setHaptics(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { store.setKeepScreenOn(v) }
    fun setShowRir(v: Boolean) = viewModelScope.launch { store.setShowRir(v) }

    fun importHevy(resolver: ContentResolver, uri: Uri) {
        if (_importState.value is ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("Could not open that file.")
                _importState.value = ImportUiState.Done(importer.import(text))
            } catch (t: Throwable) {
                _importState.value = ImportUiState.Failed(t.message ?: "Import failed.")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                SettingsViewModel(c.settings, c.importer)
            }
        }
    }
}
