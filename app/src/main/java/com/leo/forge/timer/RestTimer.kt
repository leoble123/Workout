package com.leo.forge.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
sealed interface RestState {
    data object Idle : RestState
    @Immutable
    data class Running(
        /** Absolute deadline on the monotonic clock; immune to wall-clock changes. */
        val endsAtElapsed: Long,
        val totalSeconds: Int,
        val label: String,
    ) : RestState {
        fun remainingMillis(now: Long = SystemClock.elapsedRealtime()): Long = (endsAtElapsed - now).coerceAtLeast(0L)
        val isFinished: Boolean get() = remainingMillis() <= 0L
    }
}

/**
 * The rest timer.
 *
 * It deliberately holds *no* ticking coroutine, wake lock or foreground service. The only
 * state is an absolute deadline, plus one OS alarm to make a noise when it is reached. The
 * UI derives the number on screen from that deadline, which means a backgrounded phone does
 * exactly nothing until the alarm fires - this is the whole reason the app does not cook in
 * your pocket between sets.
 */
class RestTimer(private val context: Context) {

    private val _state = MutableStateFlow<RestState>(RestState.Idle)
    val state: StateFlow<RestState> = _state.asStateFlow()

    fun start(durationSeconds: Int, label: String) {
        if (durationSeconds <= 0) return
        val endsAt = SystemClock.elapsedRealtime() + durationSeconds * 1000L
        _state.value = RestState.Running(endsAt, durationSeconds, label)
        scheduleAlarm(durationSeconds * 1000L, label)
    }

    /** Adds or removes time, keeping the alarm in step. */
    fun nudge(deltaSeconds: Int) {
        val running = _state.value as? RestState.Running ?: return
        val newEnd = running.endsAtElapsed + deltaSeconds * 1000L
        val remaining = newEnd - SystemClock.elapsedRealtime()
        if (remaining <= 0) { stop(); return }
        _state.value = running.copy(
            endsAtElapsed = newEnd,
            totalSeconds = (running.totalSeconds + deltaSeconds).coerceAtLeast(1),
        )
        scheduleAlarm(remaining, running.label)
    }

    fun stop() {
        _state.value = RestState.Idle
        cancelAlarm()
    }

    private fun alarmIntent(label: String): PendingIntent {
        val intent = Intent(context, RestAlarmReceiver::class.java).apply {
            action = RestAlarmReceiver.ACTION_REST_DONE
            putExtra(RestAlarmReceiver.EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * setAlarmClock's show-intent is what the system opens if the user taps the alarm
     * indicator, so it has to be an Activity - and it needs its own request code, or it
     * collides with the broadcast above.
     */
    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context, SHOW_REQUEST_CODE,
        Intent(context, com.leo.forge.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(inMillis: Long, label: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(label)
        val triggerElapsed = SystemClock.elapsedRealtime() + inMillis
        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                // Exact and Doze-exempt, but still just one scheduled wake-up.
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
            } else {
                // No exact-alarm grant: the alarm-clock API needs no permission and is
                // never deferred, at the cost of an alarm indicator while resting.
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(System.currentTimeMillis() + inMillis, showIntent()),
                    pi,
                )
            }
        }.onFailure {
            // Last resort: inexact. A late buzz beats a crash.
            runCatching { am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi) }
        }
    }

    private fun cancelAlarm() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // NO_CREATE: look the alarm up to cancel it rather than creating one as a side effect.
        val existing = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, RestAlarmReceiver::class.java).apply { action = RestAlarmReceiver.ACTION_REST_DONE },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) {
            runCatching { am.cancel(existing) }
            existing.cancel()
        }
        RestNotifications.clear(context)
    }

    private companion object {
        const val REQUEST_CODE = 0x5E7
        const val SHOW_REQUEST_CODE = 0x5E8
    }
}
