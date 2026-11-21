package com.adhdkiller.app.ui.screens.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.ALL_DAYS
import com.adhdkiller.app.data.db.entity.HabitCadence
import com.adhdkiller.app.data.db.entity.HabitEntity
import com.adhdkiller.app.data.repo.streakOf
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.DangerText
import com.adhdkiller.app.ui.components.EmptyState
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.Pill
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.StatTile
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.HairlineStrong
import com.adhdkiller.app.ui.theme.InkHigh
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun HabitsScreen() {
    val container = LocalAppContainer.current
    val repo = container.habitRepository
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val habits by repo.habits.collectAsStateWithLifecycle(initialValue = emptyList())

    val today = Time.today()
    val todayEpoch = Time.epochDay(today)
    // A year of logs is enough to compute streaks and the seven-day strip.
    val logFlow = remember(todayEpoch) { repo.logsSince(todayEpoch - 365) }
    val logs by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    val loggedToday = habits.count { habit ->
        logs.any { it.habitId == habit.id && it.epochDay == todayEpoch }
    }
    val bestStreak = habits.maxOfOrNull { streakOf(it.id, logs) } ?: 0

    AppScaffold(
        title = "Habits",
        subtitle = "${habits.size} tracked",
        actions = {
            HeaderAction(text = "Add", onClick = { editing = null; editorOpen = true })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Done today",
                    value = "$loggedToday/${habits.size}",
                    modifier = Modifier.weight(1f),
                    accent = accent,
                )
                StatTile(
                    label = "Best streak",
                    value = "$bestStreak d",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(text = "Habits", trailing = "${habits.size}")
            Spacer(Modifier.height(10.dp))

            if (habits.isEmpty()) {
                EmptyState(
                    title = "No habits yet",
                    subtitle = "Start with one small daily action you can keep.",
                    actionLabel = "Add habit",
                    onAction = { editing = null; editorOpen = true },
                )
            } else {
                habits.forEach { habit ->
                    val habitLogs = logs.filter { it.habitId == habit.id }
                    HabitRow(
                        habit = habit,
                        streak = streakOf(habit.id, logs),
                        week = (6 downTo 0).map { offset ->
                            todayEpoch - offset to habitLogs.any { it.epochDay == todayEpoch - offset }
                        },
                        loggedToday = habitLogs.any { it.epochDay == todayEpoch },
                        onLog = {
                            scope.launch {
                                if (habitLogs.any { it.epochDay == todayEpoch }) {
                                    repo.unlog(habit.id, todayEpoch)
                                } else {
                                    repo.log(habit, todayEpoch)
                                }
                            }
                        },
                        onEdit = { editing = habit; editorOpen = true },
                        onDelete = { scope.launch { repo.delete(habit) } },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (editorOpen) {
        HabitEditorDialog(
            existing = editing,
            onDismiss = { editorOpen = false },
            onSave = { draft ->
                scope.launch {
                    if (editing == null) repo.add(draft) else repo.update(draft)
                }
                editorOpen = false
            },
        )
    }
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, HairlineStrong, RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

@Composable
private fun HabitRow(
    habit: HabitEntity,
    streak: Int,
    week: List<Pair<Long, Boolean>>,
    loggedToday: Boolean,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current.color

    AppCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = habit.emoji,
                fontSize = 22.sp,
                color = TextPrimary,
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                MutedText(cadenceLabel(habit))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        text = if (streak > 0) "$streak day streak" else "No streak",
                        color = if (streak > 0) accent else TextTertiary,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { (_, logged) ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (logged) accent else Hairline),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                MutedText("Last 7 days")
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (loggedToday) InkHigh else Color.Transparent)
                        .border(1.dp, if (loggedToday) accent else HairlineStrong, RoundedCornerShape(10.dp))
                        .clickableNoRipple { onLog() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (loggedToday) "Logged" else "Log",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (loggedToday) accent else TextSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableNoRipple { onEdit() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
                DangerText(
                    text = "Delete",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickableNoRipple { onDelete() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun cadenceLabel(habit: HabitEntity): String = when (habit.cadence) {
    HabitCadence.DAILY -> "Every day"
    HabitCadence.TIMES_PER_WEEK -> "${habit.timesPerWeek}× per week"
    HabitCadence.SPECIFIC_DAYS -> {
        val labels = Time.maskLabels(habit.daysMask)
        if (labels.isEmpty()) "No days selected" else labels.joinToString(", ")
    }
}

@Composable
private fun HabitEditorDialog(
    existing: HabitEntity?,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
) {
    val accent = LocalAccent.current.color

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var emoji by remember(existing?.id) { mutableStateOf(existing?.emoji ?: "•") }
    var cadence by remember(existing?.id) { mutableStateOf(existing?.cadence ?: HabitCadence.DAILY) }
    var timesPerWeek by remember(existing?.id) {
        mutableStateOf((existing?.timesPerWeek ?: 7).toString())
    }
    var daysMask by remember(existing?.id) { mutableStateOf(existing?.daysMask ?: ALL_DAYS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (existing == null) "New habit" else "Edit habit",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { input -> emoji = input.take(2) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "CADENCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        HabitCadence.DAILY to "Daily",
                        HabitCadence.TIMES_PER_WEEK to "Weekly",
                        HabitCadence.SPECIFIC_DAYS to "Days",
                    ).forEach { (value, label) ->
                        val active = cadence == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) InkHigh else Color.Transparent)
                                .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                                .clickableNoRipple { cadence = value }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) TextPrimary else TextTertiary,
                            )
                        }
                    }
                }

                if (cadence == HabitCadence.TIMES_PER_WEEK) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = timesPerWeek,
                        onValueChange = { input -> timesPerWeek = input.filter { it.isDigit() } },
                        label = { Text("Times per week") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (cadence == HabitCadence.SPECIFIC_DAYS) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "DAYS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = Time.bit(day)
                            val active = (daysMask and bit) != 0
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) InkHigh else Color.Transparent)
                                    .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                                    .clickableNoRipple { daysMask = daysMask xor bit },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = Time.dayName(day).take(2),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) TextPrimary else TextTertiary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = existing
                    onSave(
                        if (base == null) {
                            HabitEntity(
                                name = name.trim(),
                                emoji = emoji.trim().ifBlank { "•" },
                                cadence = cadence,
                                timesPerWeek = timesPerWeek.toIntOrNull() ?: 7,
                                daysMask = daysMask,
                                createdAt = System.currentTimeMillis(),
                            )
                        } else {
                            base.copy(
                                name = name.trim(),
                                emoji = emoji.trim().ifBlank { "•" },
                                cadence = cadence,
                                timesPerWeek = timesPerWeek.toIntOrNull() ?: 7,
                                daysMask = daysMask,
                            )
                        },
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save", color = if (name.isNotBlank()) accent else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}
