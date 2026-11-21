package com.adhdkiller.app.ui.screens.blocker

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.AdvancedProtection
import com.adhdkiller.app.data.db.entity.BlockMode
import com.adhdkiller.app.data.db.entity.BlockRuleEntity
import com.adhdkiller.app.enforcement.guard.AppGuardService
import com.adhdkiller.app.enforcement.shortvideo.ShortFormMatcher
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

/**
 * Controls the short-form video blocker. This feature is completely independent of the phone
 * lockdown: it has its own toggle, its own rules and its own enforcement layers.
 */
@Composable
fun BlockerScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val blockerEnabled by container.settings.blockerEnabled.collectAsStateWithLifecycle(initialValue = true)
    val rules by container.blockRuleRepository.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val distractions by container.distractionRepository.countSince(
        System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    ).collectAsStateWithLifecycle(initialValue = 0)

    var showAppPicker by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<BlockRuleEntity?>(null) }
    var showRuleEditor by remember { mutableStateOf(false) }

    val serviceEnabled = AppGuardService.instance != null
    val advancedProtection = remember { AdvancedProtection.isEnabled(context) }
    val guardianActive = container.launcher.isGuardianActive()

    AppScaffold(
        title = "Short-form Blocker",
        subtitle = "REELS · SHORTS · TIKTOK",
        onBack = onBack,
    ) {
        SectionHeader("Blocker")
        Spacer(Modifier.height(10.dp))
        AppCard {
            ToggleRow(
                title = "Block short-form video",
                subtitle = "Independent of the phone lockdown.",
                checked = blockerEnabled,
                onCheckedChange = { value ->
                    scope.launch {
                        container.settings.setBlockerEnabled(value)
                        container.shortFormEnforcer.reconcile()
                        AppGuardService.instance?.refreshRules()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatTile(label = "Apps", value = rules.size.toString(), caption = "in the rule set")
                StatTile(
                    label = "Blocked",
                    value = distractions.toString(),
                    caption = "last 7 days",
                    accent = if (distractions > 0) accent else TextPrimary,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Capability")
        Spacer(Modifier.height(10.dp))
        AppCard {
            CapabilityRow(
                title = "Accessibility guard",
                ready = serviceEnabled,
                detail = "Detects the exact short-form surface and navigates away from it.",
            )
            CapabilityRow(
                title = "Strict mode",
                ready = serviceEnabled,
                detail = "A strict rule blocks the whole app, not just the feed. It rides on " +
                    "the same guard, so no extra permission is needed.",
            )
            if (!serviceEnabled) {
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Enable the accessibility service",
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
                Spacer(Modifier.height(10.dp))
                MutedText(
                    "In the system settings choose “ADHD Killer” and switch it on. Android asks " +
                        "for a confirmation — we only read which app is in the foreground and its " +
                        "view identifiers, and we never read what is typed.",
                )
            }
            if (advancedProtection) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Danger.copy(alpha = 0.10f))
                        .border(1.dp, Danger.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Danger,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Advanced Protection is on",
                            style = MaterialTheme.typography.titleSmall,
                            color = Danger,
                        )
                        Spacer(Modifier.height(4.dp))
                        MutedText(
                            "Android 17's Advanced Protection limits accessibility services to " +
                                "verified tools, so precise detection is disabled. Strict mode still " +
                                "works: apps marked strict are suspended by the system.",
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Rules", trailing = "${rules.count { it.enabled }} active")
        Spacer(Modifier.height(10.dp))

        if (rules.isEmpty()) {
            AppCard {
                EmptyState(
                    title = "No rules yet",
                    subtitle = "Add an app and ADHD Killer will learn what to block in it.",
                    actionLabel = "Add an app",
                    onAction = { showAppPicker = true },
                )
            }
        } else {
            rules.forEach { rule ->
                AppCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.appLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = modeLabel(rule),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = rule.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(
                                text = if (rule.strict) "Strict" else "Surface only",
                                color = if (rule.strict) accent else TextSecondary,
                                borderColor = if (rule.strict) accent.copy(alpha = 0.4f) else Hairline,
                            )
                            Pill(
                                text = if (rule.enabled) "On" else "Off",
                                color = if (rule.enabled) Success else TextTertiary,
                                borderColor = if (rule.enabled) Success.copy(alpha = 0.4f) else Hairline,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ToggleRow(
                            title = "Enabled",
                            checked = rule.enabled,
                            onCheckedChange = { value ->
                                scope.launch {
                                    container.blockRuleRepository.upsert(rule.copy(enabled = value))
                                    container.shortFormEnforcer.reconcile()
                                    AppGuardService.instance?.refreshRules()
                                }
                            },
                        )
                        ToggleRow(
                            title = "Strict mode",
                            subtitle = "Block the whole app instead of only the short-form parts.",
                            checked = rule.strict,
                            enabled = serviceEnabled,
                            onCheckedChange = { value ->
                                scope.launch {
                                    container.blockRuleRepository.upsert(rule.copy(strict = value))
                                    container.shortFormEnforcer.reconcile()
                                }
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                text = "Edit rule",
                                onClick = { editingRule = rule; showRuleEditor = true },
                                modifier = Modifier.weight(1f),
                            )
                            GhostButton(
                                text = "Remove",
                                color = Danger,
                                onClick = {
                                    scope.launch {
                                        container.blockRuleRepository.delete(rule)
                                        container.shortFormEnforcer.reconcile()
                                        AppGuardService.instance?.refreshRules()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            GhostButton(
                text = "Add an app",
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { packageName, label ->
                scope.launch {
                    val existing = container.blockRuleRepository.findByPackage(packageName)
                    if (existing == null) {
                        container.blockRuleRepository.upsert(
                            BlockRuleEntity(
                                packageName = packageName,
                                appLabel = label,
                                enabled = true,
                                mode = BlockMode.ACTIVITY,
                            )
                        )
                    }
                    container.shortFormEnforcer.reconcile()
                    AppGuardService.instance?.refreshRules()
                }
                showAppPicker = false
            },
        )
    }

    if (showRuleEditor && editingRule != null) {
        RuleEditorDialog(
            rule = editingRule!!,
            onDismiss = { showRuleEditor = false },
            onSave = { updated ->
                scope.launch {
                    container.blockRuleRepository.upsert(updated)
                    container.shortFormEnforcer.reconcile()
                    AppGuardService.instance?.refreshRules()
                }
                showRuleEditor = false
            },
        )
    }
}

private fun modeLabel(rule: BlockRuleEntity): String = when (rule.mode) {
    BlockMode.WHOLE_APP -> "Whole app is blocked"
    BlockMode.ACTIVITY -> "Blocked on specific screens"
    BlockMode.KEYWORD -> "Blocked by on-screen markers"
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
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(11.dp),
                )
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
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    val container = LocalAppContainer.current
    var query by remember { mutableStateOf("") }
    val apps = remember { container.inventory.installedApps(includeSystem = false) }
        .filter { it.packageName != container.admin.admin.packageName }
    val filtered = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Pick an app") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(app.packageName, app.label) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                )
                            }
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add ${app.label}",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) }
        },
    )
}

@Composable
private fun RuleEditorDialog(
    rule: BlockRuleEntity,
    onDismiss: () -> Unit,
    onSave: (BlockRuleEntity) -> Unit,
) {
    var mode by remember { mutableStateOf(rule.mode) }
    var activities by remember { mutableStateOf(rule.activityPatterns) }
    var keywords by remember { mutableStateOf(rule.keywords) }
    var excludes by remember { mutableStateOf(rule.excludeKeywords) }
    var strict by remember { mutableStateOf(rule.strict) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(rule.appLabel) },
        text = {
            Column {
                Text("MODE", style = EyebrowStyle, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        BlockMode.ACTIVITY to "Screens",
                        BlockMode.KEYWORD to "Markers",
                        BlockMode.WHOLE_APP to "Whole app",
                    ).forEach { (option, label) ->
                        val selected = mode == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color.White else Color(0xFF141414))
                                .clickable { mode = option }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.Black else TextSecondary,
                            )
                        }
                    }
                }
                if (mode != BlockMode.WHOLE_APP) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = activities,
                        onValueChange = { activities = it },
                        label = { Text("Activity class names (comma separated)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("View id / label keywords") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = excludes,
                        onValueChange = { excludes = it },
                        label = { Text("Never match these (navigation tabs)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Strict mode",
                    subtitle = "Suspend the whole app (needs Device Owner).",
                    checked = strict,
                    onCheckedChange = { strict = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    rule.copy(
                        mode = mode,
                        activityPatterns = activities,
                        keywords = keywords,
                        excludeKeywords = excludes,
                        strict = strict,
                    )
                )
            }) { Text("Save", color = TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}
