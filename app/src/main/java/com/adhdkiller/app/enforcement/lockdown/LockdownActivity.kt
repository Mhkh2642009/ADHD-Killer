package com.adhdkiller.app.enforcement.lockdown

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.adhdkiller.app.ADHDKillerApp
import com.adhdkiller.app.MainActivity
import com.adhdkiller.app.ui.screens.lockdown.LockdownPanel
import com.adhdkiller.app.ui.theme.ADHDKillerTheme
import kotlinx.coroutines.launch

/**
 * The lockdown screen.
 *
 * It is deliberately the most defensive screen in the app: it must always offer a way to place
 * an emergency call, and it must never be the reason a user cannot get help.
 *
 * When the guardian launcher is the device's HOME app, the same panel is shown by
 * [GuardianLauncherActivity] instead — this activity is then the manual entry point.
 */
class LockdownActivity : ComponentActivity() {

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                EmergencyCaller.place(this, pendingEmergencyNumber)
            } else {
                EmergencyCaller.openDialer(this, pendingEmergencyNumber)
            }
        }

    private var pendingEmergencyNumber: String = "112"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as ADHDKillerApp).container

        setContent {
            val status by container.lockdownState.status.collectAsStateWithLifecycle()
            val guard by container.guardState.snapshot.collectAsStateWithLifecycle()
            val numbers by container.settings.emergencyNumbers
                .collectAsStateWithLifecycle(initialValue = listOf("112", "911", "999"))
            val hardMode by container.settings.hardMode.collectAsStateWithLifecycle(initialValue = false)
            val accent by container.accentState.accent.collectAsStateWithLifecycle()

            ADHDKillerTheme(accent = accent) {
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
                            finish()
                        }
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (container.lockdownState.status.value.enforced) enterLockTask()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as ADHDKillerApp).container
        lifecycleScope.launch {
            runCatching { container.lockdownEnforcer.reconcile() }
            if (container.lockdownState.status.value.enforced) {
                enterLockTask()
            } else {
                runCatching { stopLockTask() }
            }
        }
    }

    /**
     * Screen pinning is the second enforcement layer. On an unmanaged device the platform asks
     * the user to confirm the pinning dialog, and the session can be left with back + recents —
     * the Settings screen says exactly that.
     */
    private fun enterLockTask() {
        val container = (application as ADHDKillerApp).container
        lifecycleScope.launch {
            if (!container.settings.screenPinningEnabled.first()) return@launch
            runCatching { startLockTask() }
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
