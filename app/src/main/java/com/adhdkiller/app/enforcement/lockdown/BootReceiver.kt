package com.adhdkiller.app.enforcement.lockdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.adhdkiller.app.ADHDKillerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the lockdown across reboots.
 *
 * `LOCKED_BOOT_COMPLETED` arrives *before* the user unlocks the device. Because the receiver is
 * declared `directBootAware`, it can run at that point and restore enforcement from the
 * device-protected schedule mirror — the Room database is not readable yet at that stage.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = appContext as? ADHDKillerApp ?: return@launch
                val container = app.container

                when (action) {
                    Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                        Log.i(TAG, "Locked boot — restoring enforcement before unlock")
                        container.lockdownEnforcer.reconcilePreUnlock()
                        container.lockdownScheduler.schedule(
                            container.lockdownEnforcer.activeSchedules().map {
                                LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict)
                            }
                        )
                    }

                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_USER_UNLOCKED -> {
                        Log.i(TAG, "Reconciling after $action")
                        container.lockdownEnforcer.reconcile()
                        container.lockdownScheduler.schedule(
                            container.lockdownEnforcer.activeSchedules().map {
                                LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict)
                            }
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Boot reconcile failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "LockdownBootReceiver"
    }
}
