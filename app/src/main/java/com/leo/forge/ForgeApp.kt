package com.leo.forge

import android.app.Application
import com.leo.forge.data.AppContainer
import com.leo.forge.data.seed.GymSeed
import com.leo.forge.domain.model.Units
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
            // A gym must exist before the generator runs, or it assumes a fully-stocked
            // commercial gym and prescribes machines that are not there.
            container.gyms.ensureDefault(GymSeed.cableLedGym(Units.KG))
        }
    }
}
