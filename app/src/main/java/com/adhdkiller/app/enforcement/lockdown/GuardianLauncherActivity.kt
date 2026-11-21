package com.adhdkiller.app.enforcement.lockdown

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.adhdkiller.app.ADHDKillerApp
import com.adhdkiller.app.MainActivity
import com.adhdkiller.app.enforcement.app.AppInventory
import com.adhdkiller.app.ui.screens.launcher.LockedAppsStrip
import com.adhdkiller.app.ui.screens.launcher.LauncherScreen
import com.adhdkiller.app.ui.screens.lockdown.LockdownPanel
import com.adhdkiller.app.ui.theme.ADHDKillerTheme
import kotlinx.coroutines.launch

/**
 * The **guardian launcher**: this app acting as the device's HOME app.
 *
 * It replaces the Device Owner trick of making the lockdown screen the persistent HOME
 * preference. The user grants it once, through the system "choose home app" dialog, and can take
 * it away again just as easily.
 *
 * Two things make it worth having:
 *
 *  * **Reboot survival.** HOME is launched at boot, before the first unlock. The launcher reads
 *    the mirrored schedule from device-protected storage and puts the lockdown screen up instead
 *    of an app drawer.
 *  * **A real door.** While a lockdown is running, the drawer only offers the allowed apps.
 */
class GuardianLauncherActivity : ComponentActivity() {

    private var pendingEmergencyNumber: String = "112"

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                EmergencyCaller.place(this, pendingEmergencyNumber)
            } else {
                EmergencyCaller.openDialer(this, pendingEmergencyNumber)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as ADHDKillerApp).container

        setContent {
            val accent by container.accentState.accent.collectAsStateWithLifecycle()
            val status by container.lockdownState.status.collectAsStateWithLifecycle()
            val guard by container.guardState.snapshot.collectAsStateWithLifecycle()
            val numbers by container.settings.emergencyNumbers
                .collectAsStateWithLifecycle(initialValue = listOf("112", "911", "999"))
            val hardMode by container.settings.hardMode.collectAsStateWithLifecycle(initialValue = false)

            var apps by remember { mutableStateOf<List<AppInventory.AppEntry>>(emptyList()) }

            val locked = guard.lockdownActive || status.enforced

            LaunchedEffect(locked) {
                apps = loadApps(container, locked)
            }

            ADHDKillerTheme(accent = accent) {
                if (locked) {
                    // The lockdown panel on top, and below it the apps that are still allowed —
                    // the dialer, the keyboard, whatever is on the allowlist. The blocked apps
                    // are simply not here.
                    Column(modifier = Modifier.fillMaxSize()) {
                        LockdownPanel(
                            status = status,
                            windowLabel = guard.windowLabel.ifBlank { status.activeWindowLabel ?: "Focus session" },
                            endsAtMillis = guard.endsAtMillis ?: status.endsAtMillis,
                            emergencyNumbers = numbers,
                            hardMode = hardMode,
                            onEmergencyCall = { number -> requestEmergencyCall(number) },
                            onEndEarly = {
                                lifecycleScope.launch {
                                    container.endLockdownEarly()
                                }
                            },
                            onOpenApp = { openMainApp() },
                            modifier = Modifier.weight(1f),
                        )
                        LockedAppsStrip(apps = apps, onLaunch = { packageName -> launchApp(packageName) })
                    }
                } else {
                    LauncherScreen(
                        apps = apps,
                        locked = false,
                        onLaunch = { packageName -> launchApp(packageName) },
                        onOpenApp = { openMainApp() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as ADHDKillerApp).container
        lifecycleScope.launch {
            // Re-check the schedule: the window may have started or ended while the launcher was
            // in the background. This also runs before the first unlock after a reboot, where the
            // database is not readable yet — the failure is harmless.
            runCatching { container.lockdownEnforcer.reconcile() }
        }
    }

    /**
     * The app drawer. While a lockdown is running only the allowed packages are offered, so the
     * blocked apps are not one tap away.
     */
    private suspend fun loadApps(
        container: com.adhdkiller.app.AppContainer,
        locked: Boolean,
    ): List<AppInventory.AppEntry> {
        val all = container.inventory.installedApps(includeSystem = true)
        if (!locked) return all
        val allowed = container.lockdownEnforcer.allowedPackages()
        return all.filter { it.packageName in allowed }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun openMainApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun requestEmergencyCall(number: String) {
        pendingEmergencyNumber = number
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            EmergencyCaller.place(this, number)
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }
}
