package com.leo.forge.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires once, at the end of a rest period. Nothing runs between sets. */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_DONE) return
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        RestNotifications.restOver(context, label)
    }

    companion object {
        const val ACTION_REST_DONE = "com.leo.forge.REST_DONE"
        const val EXTRA_LABEL = "label"
    }
}
