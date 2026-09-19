package com.leo.forge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leo.forge.data.prefs.ForgeSettings
import com.leo.forge.domain.model.Units
import com.leo.forge.ui.nav.ForgeRoot
import com.leo.forge.ui.theme.ForgeTheme
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* rest alerts only */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val container = (application as ForgeApp).container
        val settingsFlow = container.settings.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, ForgeSettings())
        val unitsFlow = container.gyms.observeUnits()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, Units.KG)

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            val units by unitsFlow.collectAsStateWithLifecycle()
            ForgeTheme(oled = settings.oledBlack) {
                ForgeRoot(settings = settings, units = units)
            }
        }
    }
}
