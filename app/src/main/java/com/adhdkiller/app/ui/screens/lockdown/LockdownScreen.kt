package com.adhdkiller.app.ui.screens.lockdown

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.LockdownScheduleEntity

import com.adhdkiller.app.enforcement.lockdown.LockdownActivity
import com.adhdkiller.app.enforcement.lockdown.LockdownWindow
import com.adhdkiller.app.enforcement.lockdown.LockdownWindows
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.DangerText
import com.adhdkiller.app.ui.components.EmptyState
import com.adhdkiller.app.ui.components.GhostButton
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.Pill
import com.adhdkiller.app.ui.components.PrimaryButton
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.StatTile
import com.adhdkiller.app.ui.components.ThinProgressBar
import com.adhdkiller.app.ui.components.ToggleRow
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.HairlineStrong
import com.adhdkiller.app.ui.theme.Ink
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.Success
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * The lockdown control centre: status, setup, and the schedule of restricted hours.
 */
@Composable
fun LockdownScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val status by container.lockdownState.status.collectAsStateWithLifecycle()
    val schedules by container.lockdownScheduleRepository.schedules.collectAsStateWithLifecycle(initialValue = emptyList())
    val lockdownEnabled by container.settings.lockdownEnabled.collectAsStateWithLifecycle(initialValue = false)
    val hardMode by container.settings.hardMode.collectAsStateWithLifecycle(initialValue = false)
    val allowLauncher by container.settings.allowLauncherInLockdown.collectAsStateWithLifecycle(initialValue = false)
    val screenPinning by container.settings.screenPinningEnabled.collectAsStateWithLifecycle(initialValue = false)

    var editing by remember { mutableStateOf<LockdownScheduleEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmTest by remember { mutableStateOf(false) }

    val adminActive = container.admin.isAdminActive()
    val exactAlarms = container.lockdownScheduler.canScheduleExactAlarms()
    // Driven by the status flow so the rows refresh when the guard binds or the home role is
    // granted, instead of only when the screen is composed.
    val guardianActive = status.guardianLauncherActive
    val guardActive = status.accessibilityGuardActive

    LaunchedEffect(Unit) {
        container.lockdownEnforcer.refreshCapabilities()
    }

    AppScaffold(
        title = "Phone Lockdown",
        subtitle = "SCHEDULED RESTRICTION",
        onBack = onBack,
    ) {
        // ---------------------------------------------------------------- current state
        SectionHeader("Current state")
        Spacer(Modifier.height(10.dp))
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatTile(
                    label = "Status",
                    value = if (status.enforced) "LOCKED" else "FREE",
                    accent = if (status.enforced) Danger else Success,
                )
                StatTile(
                    label = "Apps blocked",
                    value = status.blockedPackages.toString(),
                    caption = if (status.enforced) "blocked" else "nothing blocked",
                )
            }
            Spacer(Modifier.height(14.dp))
            if (status.activeWindowLabel != null) {
                MutedText("Active window: ${status.activeWindowLabel}")
                status.endsAtMillis?.let {
                    val remaining = it - System.currentTimeMillis()
                    if (remaining > 0) {
                        Spacer(Modifier.height(6.dp))
                        ThinProgressBar(progress = 1f, color = Danger)
                        Spacer(Modifier.height(6.dp))
                        MutedText("Ends in ${Time.formatDuration(remaining)}")
                    }
                }
            } else {
                MutedText("No lockdown window is active right now.")
            }
            status.message?.let {
                Spacer(Modifier.height(10.dp))
                DangerText(it)
            }
        }

        Spacer(Modifier.height(22.dp))

        // ------------------------------------------------------------------- capability
        SectionHeader("Enforcement layers")
        Spacer(Modifier.height(10.dp))
        MutedText(
            "Three layers can hold a lockdown. Switch them on in any combination — the app " +
                "never needs Device Owner, a factory reset or an adb command.",
        )
        Spacer(Modifier.height(12.dp))
        AppCard {
            CapabilityRow(
                title = "Accessibility guard",
                ready = guardActive,
                detail = "Closes any blocked app that comes to the front. This is the layer " +
                    "that does the actual work.",
            )
            CapabilityRow(
                title = "Guardian launcher",
                ready = guardianActive,
                detail = "Makes this app your home app: only allowed apps are listed, and the " +
                    "lockdown comes back after a reboot.",
            )
            CapabilityRow(
                title = "Screen pinning",
                ready = screenPinning,
                detail = "The platform's own pinning mode blocks Home and the app switcher. " +
                    "You confirm the pin, and back + recents still leaves it.",
            )
            CapabilityRow(
                title = "Exact alarms",
                ready = exactAlarms,
                detail = "Without them a window can start a few minutes late.",
            )
            CapabilityRow(
                title = "Device administrator (optional)",
                ready = adminActive,
                detail = "Only adds uninstall protection. Everything else works without it.",
            )

            if (!guardActive) {
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Enable the accessibility guard",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                )
            }

            if (!guardianActive) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = "Set as home app",
                        onClick = {
                            val intent = container.launcher.requestIntent()
                            if (intent != null) {
                                runCatching {
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(
                        text = "Home settings",
                        onClick = {
                            runCatching {
                                context.startActivity(container.launcher.homeSettingsIntent())
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!exactAlarms) {
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    text = "Allow exact alarms",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // -------------------------------------------------------------------- behaviour
        SectionHeader("Behaviour")
        Spacer(Modifier.height(10.dp))
        AppCard {
            ToggleRow(
                title = "Lockdown schedule active",
                subtitle = "Windows are enforced automatically.",
                checked = lockdownEnabled,
                onCheckedChange = { value ->
                    scope.launch {
                        container.settings.setLockdownEnabled(value)
                        container.mirror.write(container.lockdownScheduleRepository.enabled())
                        container.lockdownScheduler.schedule(currentWindows(schedules))
                        container.lockdownEnforcer.reconcile()
                    }
                },
            )
            ToggleRow(
                title = "Screen pinning",
                subtitle = "Adds the platform's pinning mode on top of the guard. You confirm " +
                    "each pin, and back + recents still leaves it.",
                checked = screenPinning,
                onCheckedChange = { value ->
                    scope.launch {
                        container.settings.setScreenPinningEnabled(value)
                        container.lockdownEnforcer.refreshCapabilities()
                    }
                },
            )
            ToggleRow(
                title = "Allow the stock launcher",
                subtitle = "Off = the stock launcher is blocked as well: only the allowed apps remain.",
                checked = allowLauncher,
                onCheckedChange = { value ->
                    scope.launch {
                        container.settings.setAllowLauncherInLockdown(value)
                        container.lockdownEnforcer.reconcile()
                    }
                },
            )
            ToggleRow(
                title = "Hard mode",
                subtitle = "Ending a lockdown early then needs the word END, and the shortcut " +
                    "back into the app disappears.",
                checked = hardMode,
                onCheckedChange = { value ->
                    scope.launch {
                        container.settings.setHardMode(value)
                        container.lockdownEnforcer.reconcile()
                    }
                },
            )
        }

        Spacer(Modifier.height(22.dp))

        // --------------------------------------------------------------------- schedule
        SectionHeader("Schedule", trailing = "${schedules.size} window(s)")
        Spacer(Modifier.height(10.dp))

        if (schedules.isEmpty()) {
            AppCard {
                EmptyState(
                    title = "No windows yet",
                    subtitle = "Add a window such as 09:00–12:00 on weekdays.",
                    actionLabel = "Add window",
                    onAction = { editing = null; showEditor = true },
                )
            }
        } else {
            schedules.forEach { schedule ->
                AppCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = schedule.label.ifBlank { "Focus" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = describe(schedule),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill(
                                    text = if (schedule.enabled) "Enabled" else "Paused",
                                    color = if (schedule.enabled) Success else TextTertiary,
                                    borderColor = if (schedule.enabled) Success.copy(alpha = 0.4f) else Hairline,
                                )
                                Pill(
                                    text = if (schedule.strict) "Enforced" else "Advisory",
                                    color = if (schedule.strict) accent else TextTertiary,
                                    borderColor = if (schedule.strict) accent.copy(alpha = 0.4f) else Hairline,
                                )
                            }
                        }
                        IconAction(Icons.Default.Edit, "Edit") {
                            editing = schedule; showEditor = true
                        }
                        IconAction(Icons.Default.Delete, "Delete", danger = true) {
                            scope.launch {
                                container.lockdownScheduleRepository.delete(schedule)
                                val remaining = container.lockdownScheduleRepository.enabled()
                                container.mirror.write(remaining)
                                container.lockdownScheduler.schedule(currentWindows(remaining))
                                container.lockdownEnforcer.reconcile()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            GhostButton(
                text = "Add window",
                onClick = { editing = null; showEditor = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(22.dp))

        // ------------------------------------------------------------------- test drive
        SectionHeader("Test")
        Spacer(Modifier.height(10.dp))
        AppCard {
            MutedText(
                "Start a five minute lockdown right now to see the enforcement in action. " +
                    "Your real schedule is not affected.",
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Start a 5 minute test lockdown",
                onClick = { confirmTest = true },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
            )
        }
    }

    if (showEditor) {
        ScheduleEditorDialog(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { schedule ->
                scope.launch {
                    if (schedule.id == 0L) {
                        container.lockdownScheduleRepository.add(schedule)
                    } else {
                        container.lockdownScheduleRepository.update(schedule)
                    }
                    val remaining = container.lockdownScheduleRepository.enabled()
                    container.mirror.write(remaining)
                    container.lockdownScheduler.schedule(currentWindows(remaining))
                    container.lockdownEnforcer.reconcile()
                }
                showEditor = false
            },
        )
    }

    if (confirmTest) {
        AlertDialog(
            onDismissRequest = { confirmTest = false },
            containerColor = Ink,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Start the test lockdown?") },
            text = {
                Text(
                    "Apps will be blocked for five minutes and the phone will be locked to " +
                        "this app. Emergency calls stay available. The test ends automatically.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmTest = false
                    scope.launch {
                        container.lockdownEnforcer.startTestLockdown(5)
                        context.startActivity(
                            Intent(context, LockdownActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                    }
                }) { Text("Start", color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTest = false }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
}

@Composable
private fun CapabilityRow(title: String, ready: Boolean, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
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
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            MutedText(detail)
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (danger) Danger else TextSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun currentWindows(schedules: List<LockdownScheduleEntity>): List<LockdownWindow> =
    schedules.filter { it.enabled }.map {
        LockdownWindow(it.id, it.label, it.daysMask, it.startMinute, it.endMinute, it.strict)
    }

private fun describe(schedule: LockdownScheduleEntity): String =
    LockdownWindows.describe(
        LockdownWindow(schedule.id, schedule.label, schedule.daysMask, schedule.startMinute, schedule.endMinute, schedule.strict)
    )

/**
 * Editor for a lockdown window: label, weekdays and start/end time.
 */
@Composable
private fun ScheduleEditorDialog(
    existing: LockdownScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (LockdownScheduleEntity) -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label ?: "Deep work") }
    var daysMask by remember { mutableStateOf(existing?.daysMask ?: 0b0011111) }
    var startMinute by remember { mutableStateOf(existing?.startMinute ?: (9 * 60)) }
    var endMinute by remember { mutableStateOf(existing?.endMinute ?: (12 * 60)) }
    var strict by remember { mutableStateOf(existing?.strict ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(if (existing == null) "New window" else "Edit window") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("DAYS", style = EyebrowStyle, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DayOfWeek.entries.toList()) { day ->
                        val bit = Time.bit(day)
                        val selected = (daysMask and bit) != 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color.White else Color(0xFF141414))
                                .clickable { daysMask = daysMask xor bit }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = Time.dayName(day).take(3),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.Black else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TimeField(label = "Start", minute = startMinute, onChange = { startMinute = it })
                Spacer(Modifier.height(10.dp))
                TimeField(label = "End", minute = endMinute, onChange = { endMinute = it })
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    title = "Enforced",
                    subtitle = "Off = advisory only, apps are not blocked.",
                    checked = strict,
                    onCheckedChange = { strict = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        existing?.copy(
                            label = label,
                            daysMask = daysMask,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            strict = strict,
                        ) ?: LockdownScheduleEntity(
                            label = label,
                            daysMask = daysMask,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            strict = strict,
                        )
                    )
                },
                enabled = daysMask != 0,
            ) { Text("Save", color = TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

@Composable
private fun TimeField(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .clickable {
                val hour = minute / 60
                val min = minute % 60
                android.app.TimePickerDialog(
                    context,
                    { _, h, m -> onChange(h * 60 + m) },
                    hour,
                    min,
                    true,
                ).show()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = Time.formatMinute(minute), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}
