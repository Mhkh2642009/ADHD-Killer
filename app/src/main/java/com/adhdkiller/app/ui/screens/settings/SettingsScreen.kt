package com.adhdkiller.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.enforcement.admin.LockdownDeviceAdminReceiver
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.DangerText
import com.adhdkiller.app.ui.components.GhostButton
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.PrimaryButton
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.ToggleRow
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.theme.Accent
import com.adhdkiller.app.ui.theme.AccentList
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.Ink
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.Success
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val xpEnabled by container.settings.xpEnabled.collectAsStateWithLifecycle(initialValue = true)
    val emergencyNumbers by container.settings.emergencyNumbers.collectAsStateWithLifecycle(initialValue = listOf("112", "911"))
    val accentIndex by container.settings.accentIndex.collectAsStateWithLifecycle(initialValue = 0)

    var showNumbers by remember { mutableStateOf(false) }
    var numbersText by remember { mutableStateOf(emergencyNumbers.joinToString(", ")) }

    val adminActive = container.admin.isAdminActive()
    val deviceOwner = container.admin.isDeviceOwner()
    val exactAlarms = container.lockdownScheduler.canScheduleExactAlarms()
    // The capability rows are driven by the status flow, so they refresh as soon as the guard
    // binds or the guardian launcher is granted — not only when the screen is composed.
    val status by container.lockdownState.status.collectAsStateWithLifecycle()
    val accessibility = status.accessibilityGuardActive
    val guardianActive = status.guardianLauncherActive
    val screenPinning = status.screenPinningEnabled
    val batteryExempt = isIgnoringBatteryOptimizations(context)

    AppScaffold(
        title = "Settings",
        subtitle = "SYSTEM · PERMISSIONS · RECOVERY",
        onBack = onBack,
    ) {
        SectionHeader("Permissions")
        Spacer(Modifier.height(10.dp))
        AppCard {
            StatusRow("Accessibility guard", accessibility)
            StatusRow("Guardian launcher", guardianActive)
            StatusRow("Screen pinning", screenPinning)
            StatusRow("Exact alarms", exactAlarms)
            StatusRow("Device administrator (optional)", adminActive)
            StatusRow("Ignore battery optimisation", batteryExempt)
            if (!accessibility) {
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    text = "Open accessibility settings",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!batteryExempt) {
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    text = "Allow background activity",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (deviceOwner) {
                Spacer(Modifier.height(12.dp))
                DangerText(
                    "This installation still holds the legacy Device Owner grant. It is not " +
                        "needed any more, and it stops the app from being uninstalled — revoke it " +
                        "with the command in the recovery card below.",
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Behaviour")
        Spacer(Modifier.height(10.dp))
        AppCard {
            ToggleRow(
                title = "XP and rewards",
                subtitle = "Turn off if you prefer a purely mechanical tool.",
                checked = xpEnabled,
                onCheckedChange = { value -> scope.launch { container.settings.setXpEnabled(value) } },
            )
            Spacer(Modifier.height(8.dp))
            Text("ACCENT", style = EyebrowStyle, color = TextTertiary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentList.forEachIndexed { index, option ->
                    val selected = index == accentIndex
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(option.color)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(15.dp),
                            )
                            .clickableNoRipple { scope.launch { container.settings.setAccentIndex(index) } },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Emergency")
        Spacer(Modifier.height(10.dp))
        AppCard {
            MutedText("These numbers can be dialled from the lockdown screen without unlocking the phone.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                emergencyNumbers.take(4).forEach { number ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(text = number, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = "Edit emergency numbers",
                onClick = { showNumbers = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Recovery")
        Spacer(Modifier.height(10.dp))
        AppCard {
            MutedText(
                "Every lockdown can be ended from inside the app: the lockdown screen always has " +
                    "an \"End early\" button, and it costs XP. If the phone is locked and you " +
                    "cannot reach that button, the escape hatch is on the computer:",
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF141414))
                    .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "adb shell dpm remove-active-admin " +
                        "${container.admin.admin.packageName}/${LockdownDeviceAdminReceiver::class.java.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "Copy escape hatch",
                    onClick = {
                        copyToClipboard(
                            context,
                            "adb shell dpm remove-active-admin " +
                                "${container.admin.admin.packageName}/${LockdownDeviceAdminReceiver::class.java.name}",
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = "Admin settings",
                    onClick = {
                        // Some OEM builds refuse `dpm remove-active-admin`; deactivating the
                        // administrator from the system settings always works.
                        runCatching {
                            context.startActivity(
                                // No public constant for this action, so the literal is used.
                                Intent("android.settings.DEVICE_ADMIN_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            MutedText(
                "Deactivating the administrator also ends a running lockdown. A factory reset " +
                    "always removes everything — the operating system gives no app the power to " +
                    "survive a reset.",
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("About")
        Spacer(Modifier.height(10.dp))
        AppCard {
            StatRow("Version", "1.0")
            StatRow("Package", container.admin.admin.packageName)
            StatRow("Today", Time.formatDate(System.currentTimeMillis()))
        }
    }

    if (showNumbers) {
        AlertDialog(
            onDismissRequest = { showNumbers = false },
            containerColor = Ink,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Emergency numbers") },
            text = {
                OutlinedTextField(
                    value = numbersText,
                    onValueChange = { numbersText = it },
                    label = { Text("Comma separated") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = numbersText.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    scope.launch {
                        container.settings.setEmergencyNumbers(parsed)
                    }
                    showNumbers = false
                }) { Text("Save", color = TextPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showNumbers = false }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun StatusRow(title: String, ready: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (ready) Success else Danger.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            if (ready) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            text = if (ready) "READY" else "MISSING",
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) Success else Danger,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return false
    return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("adb", text))
    }
}
