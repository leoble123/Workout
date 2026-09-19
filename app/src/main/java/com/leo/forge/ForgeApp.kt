package com.leo.forge

import android.app.Application
import com.leo.forge.data.AppContainer
import com.leo.forge.timer.RestNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ForgeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RestNotifications.ensureChannel(this)
        // Seeding touches disk, so it stays off the main thread and off the startup path.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.exercises.seedIfNeeded()
        }
    }
}
