package com.adhdkiller.app.ui.screens.goals

import android.app.DatePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.GoalEntity
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.DangerText
import com.adhdkiller.app.ui.components.EmptyState
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.StatTile
import com.adhdkiller.app.ui.components.ThinProgressBar
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.HairlineStrong
import com.adhdkiller.app.ui.theme.InkHigh
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.Success
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@Composable
fun GoalsScreen() {
    val container = LocalAppContainer.current
    val repo = container.goalRepository
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val goals by repo.goals.collectAsStateWithLifecycle(initialValue = emptyList())
    val completedCount by repo.completedCount.collectAsStateWithLifecycle(initialValue = 0)

    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    val active = goals.filter { it.completedAt == null }
    val completed = goals.filter { it.completedAt != null }

    AppScaffold(
        title = "Goals",
        subtitle = "${active.size} in progress",
        actions = {
            HeaderAction(text = "Add", onClick = { editing = null; editorOpen = true })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Active",
                    value = active.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Reached",
                    value = completedCount.toString(),
                    modifier = Modifier.weight(1f),
                    accent = accent,
                )
            }

            Spacer(Modifier.height(20.dp))

            if (goals.isEmpty()) {
                EmptyState(
                    title = "No goals yet",
                    subtitle = "Pick one outcome that matters and give it a number.",
                    actionLabel = "Add goal",
                    onAction = { editing = null; editorOpen = true },
                )
            } else {
                SectionHeader(text = "In progress", trailing = "${active.size}")
                Spacer(Modifier.height(10.dp))
                if (active.isEmpty()) {
                    MutedText("Every goal is done. Add another one when you are ready.")
                } else {
                    active.forEach { goal ->
                        GoalCard(
                            goal = goal,
                            onIncrement = { delta -> scope.launch { repo.increment(goal, delta) } },
                            onEdit = { editing = goal; editorOpen = true },
                            onDelete = { scope.launch { repo.delete(goal) } },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (completed.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    SectionHeader(text = "Reached", trailing = "${completed.size}")
                    Spacer(Modifier.height(10.dp))
                    completed.forEach { goal ->
                        GoalCard(
                            goal = goal,
                            onIncrement = { delta -> scope.launch { repo.increment(goal, delta) } },
                            onEdit = { editing = goal; editorOpen = true },
                            onDelete = { scope.launch { repo.delete(goal) } },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    if (editorOpen) {
        GoalEditorDialog(
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
private fun GoalCard(
    goal: GoalEntity,
    onIncrement: (Double) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current.color
    val done = goal.completedAt != null
    val progress = if (goal.targetValue <= 0.0) 0f else (goal.currentValue / goal.targetValue).toFloat()

    AppCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    if (done) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Success)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "DONE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black,
                            )
                        }
                    }
                }
                if (goal.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MutedText(goal.description)
                }

                Spacer(Modifier.height(12.dp))
                ThinProgressBar(progress = progress, color = if (done) Success else accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${formatNumber(goal.currentValue)} / ${formatNumber(goal.targetValue)}" +
                        if (goal.unit.isNotBlank()) " ${goal.unit}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )

                if (goal.deadline != null) {
                    Spacer(Modifier.height(4.dp))
                    val overdue = goal.deadline < System.currentTimeMillis()
                    Text(
                        text = deadlineLabel(goal.deadline),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) Danger else TextTertiary,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepButton(text = "+", onClick = { onIncrement(1.0) })
                    StepButton(text = "−", onClick = { onIncrement(-1.0) })
                }
                Spacer(Modifier.height(10.dp))
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

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(InkHigh)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

/** "in 3 days", "due today", "overdue". */
private fun deadlineLabel(deadline: Long): String {
    val diff = deadline - System.currentTimeMillis()
    val days = Math.ceil(diff / 86_400_000.0).toInt()
    return when {
        diff < 0 -> "overdue"
        days <= 0 -> "due today"
        days == 1 -> "due tomorrow"
        else -> "in $days days"
    }
}

@Composable
private fun GoalEditorDialog(
    existing: GoalEntity?,
    onDismiss: () -> Unit,
    onSave: (GoalEntity) -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalAccent.current.color

    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var unit by remember(existing?.id) { mutableStateOf(existing?.unit.orEmpty()) }
    var target by remember(existing?.id) {
        mutableStateOf((existing?.targetValue ?: 1.0).let { formatNumber(it) })
    }
    var deadline by remember(existing?.id) {
        mutableStateOf(existing?.deadline?.let { Instant.ofEpochMilli(it).atZone(Time.zone).toLocalDate() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (existing == null) "New goal" else "Edit goal",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { input ->
                            target = input.filter { it.isDigit() || it == '.' }
                        },
                        label = { Text("Target") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "DEADLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeadlineButton(
                        text = deadline?.let {
                            Time.formatDate(it.atStartOfDay(Time.zone).toInstant().toEpochMilli())
                        } ?: "Pick date",
                        onClick = {
                            val initial = deadline ?: Time.today()
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> deadline = LocalDate.of(year, month + 1, day) },
                                initial.year,
                                initial.monthValue - 1,
                                initial.dayOfMonth,
                            ).show()
                        },
                    )
                    DeadlineButton(
                        text = "Clear",
                        onClick = { deadline = null },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val deadlineMillis = deadline?.let {
                        it.atTime(23, 59).atZone(Time.zone).toInstant().toEpochMilli()
                    }
                    val base = existing
                    onSave(
                        if (base == null) {
                            GoalEntity(
                                title = title.trim(),
                                description = description.trim(),
                                unit = unit.trim(),
                                targetValue = target.toDoubleOrNull() ?: 1.0,
                                deadline = deadlineMillis,
                                createdAt = System.currentTimeMillis(),
                            )
                        } else {
                            base.copy(
                                title = title.trim(),
                                description = description.trim(),
                                unit = unit.trim(),
                                targetValue = target.toDoubleOrNull() ?: 1.0,
                                deadline = deadlineMillis,
                            )
                        },
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text("Save", color = if (title.isNotBlank()) accent else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun DeadlineButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, HairlineStrong, RoundedCornerShape(12.dp))
            .clickableNoRipple { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    }
}
