package com.adhdkiller.app.enforcement.lockdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adhdkiller.app.ADHDKillerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires at the start and the end of every lockdown window and re-applies the policy.
 */
class LockdownAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (appContext as? ADHDKillerApp)?.container ?: return@launch
                container.lockdownEnforcer.reconcile()
                container.lockdownScheduler.schedule(container.lockdownEnforcer.activeSchedules().map {
                    LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict)
                })
            } catch (t: Throwable) {
                Log.e("LockdownAlarmReceiver", "Reconcile failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
