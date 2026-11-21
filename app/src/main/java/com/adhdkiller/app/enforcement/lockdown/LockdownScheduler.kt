package com.adhdkiller.app.enforcement.lockdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.adhdkiller.app.core.Time

/**
 * Arms the alarms that drive the lockdown.
 *
 * Two boundaries matter: the moment a window opens and the moment it closes. Both are armed as
 * exact alarms when the user has granted the "alarms & reminders" special permission; otherwise
 * they degrade to inexact alarms and the UI says so.
 */
class LockdownScheduler(
    private val context: Context,
    private val state: LockdownStateHolder,
) {

    companion object {
        const val TAG = "LockdownScheduler"
        const val ACTION_RECONCILE = "com.adhdkiller.app.action.RECONCILE_LOCKDOWN"
        private const val REQUEST_START = 4101
        private const val REQUEST_END = 4102
        private const val REQUEST_BACKSTOP = 4103
        private const val BACKSTOP_INTERVAL_MS = 30 * 60 * 1000L
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** Re-arms the boundary alarms for the given windows. */
    fun schedule(windows: List<LockdownWindow>) {
        cancel()
        val now = Time.now()

        LockdownWindows.nextStart(windows, now)?.let { millis ->
            setAlarm(millis, REQUEST_START, ACTION_RECONCILE)
        }
        LockdownWindows.nextEnd(windows, now)?.let { millis ->
            setAlarm(millis, REQUEST_END, ACTION_RECONCILE)
        }
        // Backstop: catches drift from inexact alarms, doze or missed broadcasts.
        setBackstop()

        state.update { it.copy(exactAlarmsAllowed = canScheduleExactAlarms()) }
    }

    fun cancel() {
        alarmManager?.cancel(pendingIntent(REQUEST_START, ACTION_RECONCILE))
        alarmManager?.cancel(pendingIntent(REQUEST_END, ACTION_RECONCILE))
        alarmManager?.cancel(pendingIntent(REQUEST_BACKSTOP, ACTION_RECONCILE))
    }

    private fun setBackstop() {
        val manager = alarmManager ?: return
        val triggerAt = System.currentTimeMillis() + BACKSTOP_INTERVAL_MS
        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent(REQUEST_BACKSTOP, ACTION_RECONCILE),
            )
        }.onFailure { Log.w(TAG, "Backstop alarm failed: ${it.message}") }
    }

    private fun setAlarm(triggerAtMillis: Long, requestCode: Int, action: String) {
        val manager = alarmManager ?: return
        val operation = pendingIntent(requestCode, action)
        runCatching {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                // Inexact fallback: the window may start a few minutes late, but it will start.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        }.onFailure { Log.w(TAG, "Alarm scheduling failed: ${it.message}") }
    }

    private fun pendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, LockdownAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
