package com.adhdkiller.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.adhdkiller.app.enforcement.lockdown.LockdownActivity
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.nav.AppNav
import com.adhdkiller.app.ui.theme.ADHDKillerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ADHDKillerApp).container

        setContent {
            val accent by container.accentState.accent.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                ADHDKillerTheme(accent = accent) {
                    AppNav()
                }
            }
        }

        // While a lockdown is running the app must not be a way out: redirect to the lock screen.
        lifecycleScope.launch {
            runCatching { container.lockdownEnforcer.reconcile() }
            if (container.lockdownState.status.value.enforced) {
                startActivity(
                    Intent(this@MainActivity, LockdownActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }
        }
    }
}
