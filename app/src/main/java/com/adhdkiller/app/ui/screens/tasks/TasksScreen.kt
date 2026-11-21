package com.adhdkiller.app.ui.screens.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.TaskEntity
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.DangerText
import com.adhdkiller.app.ui.components.EmptyState
import com.adhdkiller.app.ui.components.GhostButton
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.Pill
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.StatTile
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.HairlineStrong
import com.adhdkiller.app.ui.theme.InkHigh
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The four views the user can slice the task list by. */
private enum class TaskFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    DONE("Done"),
}

@Composable
fun TasksScreen() {
    val container = LocalAppContainer.current
    val repo = container.taskRepository
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val tasks by repo.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val completedCount by repo.completedCount.collectAsStateWithLifecycle(initialValue = 0)

    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val endOfToday = Time.today().plusDays(1).atStartOfDay(Time.zone).toInstant().toEpochMilli()

    val todayCount = tasks.count { it.dueAt != null && it.dueAt <= endOfToday }
    val visible = when (filter) {
        TaskFilter.ALL -> tasks
        TaskFilter.TODAY -> tasks.filter { it.dueAt != null && it.dueAt <= endOfToday }
        TaskFilter.UPCOMING -> tasks.filter { it.dueAt != null && it.dueAt > now }
        TaskFilter.DONE -> tasks.filter { it.completedAt != null }
    }

    AppScaffold(
        title = "Tasks",
        subtitle = "${visible.size} shown",
        actions = {
            HeaderAction(text = "Add", onClick = { editing = null; editorOpen = true })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Due today",
                    value = todayCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Completed",
                    value = completedCount.toString(),
                    modifier = Modifier.weight(1f),
                    accent = accent,
                )
            }

            Spacer(Modifier.height(20.dp))

            FilterRow(
                options = TaskFilter.entries.toList(),
                selected = filter,
                onSelect = { filter = it },
            )

            Spacer(Modifier.height(18.dp))

            SectionHeader(text = "Task list", trailing = "${visible.size}")

            Spacer(Modifier.height(10.dp))

            if (visible.isEmpty()) {
                EmptyState(
                    title = "Nothing here",
                    subtitle = when (filter) {
                        TaskFilter.ALL -> "Add your first task and it will show up here."
                        TaskFilter.TODAY -> "No task is due today."
                        TaskFilter.UPCOMING -> "Nothing is scheduled ahead."
                        TaskFilter.DONE -> "No task has been completed yet."
                    },
                    actionLabel = if (filter == TaskFilter.ALL) "Add task" else null,
                    onAction = if (filter == TaskFilter.ALL) {
                        { editing = null; editorOpen = true }
                    } else {
                        null
                    },
                )
            } else {
                visible.forEach { task ->
                    TaskRow(
                        task = task,
                        onToggle = {
                            scope.launch { repo.setCompleted(task, task.completedAt == null) }
                        },
                        onEdit = { editing = task; editorOpen = true },
                        onDelete = { scope.launch { repo.delete(task) } },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (editorOpen) {
        TaskEditorDialog(
            existing = editing,
            onDismiss = { editorOpen = false },
            onSave = { draft ->
                scope.launch {
                    if (editing == null) {
                        repo.add(draft)
                    } else {
                        repo.update(draft)
                    }
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
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
        )
    }
}

@Composable
private fun FilterRow(
    options: List<TaskFilter>,
    selected: TaskFilter,
    onSelect: (TaskFilter) -> Unit,
) {
    val accent = LocalAccent.current.color
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) InkHigh else Color.Transparent)
                    .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                    .clickableNoRipple { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) TextPrimary else TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current.color
    val done = task.completedAt != null

    AppCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (done) accent else Color.Transparent)
                    .border(1.dp, if (done) accent else HairlineStrong, RoundedCornerShape(6.dp))
                    .clickableNoRipple { onToggle() },
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) TextTertiary else TextPrimary,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (task.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    MutedText(task.notes)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        text = priorityLabel(task.priority),
                        color = priorityColor(task.priority),
                        borderColor = Hairline,
                    )
                    if (task.dueAt != null) {
                        Pill(text = Time.formatDateTime(task.dueAt), color = TextSecondary)
                    }
                    if (task.estimateMinutes > 0) {
                        Pill(text = "${task.estimateMinutes}m", color = TextSecondary)
                    }
                    if (!task.tag.isNullOrBlank()) {
                        Pill(text = task.tag, color = accent, borderColor = Hairline)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
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

private fun priorityLabel(priority: Int): String = when (priority) {
    0 -> "Low"
    2 -> "High"
    else -> "Normal"
}

@Composable
private fun priorityColor(priority: Int): Color = when (priority) {
    0 -> TextTertiary
    2 -> Danger
    else -> TextSecondary
}

@Composable
private fun TaskEditorDialog(
    existing: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalAccent.current.color

    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var priority by remember(existing?.id) { mutableStateOf(existing?.priority ?: 1) }
    var estimate by remember(existing?.id) { mutableStateOf(existing?.estimateMinutes?.toString().orEmpty()) }
    var tag by remember(existing?.id) { mutableStateOf(existing?.tag.orEmpty()) }
    var dueDate by remember(existing?.id) {
        mutableStateOf(existing?.dueAt?.let { Instant.ofEpochMilli(it).atZone(Time.zone).toLocalDate() })
    }
    var dueTime by remember(existing?.id) {
        mutableStateOf(existing?.dueAt?.let { Instant.ofEpochMilli(it).atZone(Time.zone).toLocalTime() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (existing == null) "New task" else "Edit task",
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
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "PRIORITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Low", 1 to "Normal", 2 to "High").forEach { (value, label) ->
                        val active = priority == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) InkHigh else Color.Transparent)
                                .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                                .clickableNoRipple { priority = value }
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

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "DUE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        text = dueDate?.let { Time.formatDate(it.atStartOfDay(Time.zone).toInstant().toEpochMilli()) }
                            ?: "Pick date",
                        onClick = {
                            val initial = dueDate ?: Time.today()
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> dueDate = LocalDate.of(year, month + 1, day) },
                                initial.year,
                                initial.monthValue - 1,
                                initial.dayOfMonth,
                            ).show()
                        },
                    )
                    GhostButton(
                        text = dueTime?.let { Time.formatMinute(it.hour * 60 + it.minute) } ?: "Pick time",
                        onClick = {
                            val initial = dueTime ?: LocalTime.of(9, 0)
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> dueTime = LocalTime.of(hour, minute) },
                                initial.hour,
                                initial.minute,
                                true,
                            ).show()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = estimate,
                        onValueChange = { input -> estimate = input.filter { it.isDigit() } },
                        label = { Text("Estimate (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("Tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dueAt = dueDate?.let { date ->
                        val time = dueTime ?: LocalTime.of(9, 0)
                        date.atTime(time).atZone(Time.zone).toInstant().toEpochMilli()
                    }
                    val base = existing
                    onSave(
                        if (base == null) {
                            TaskEntity(
                                title = title.trim(),
                                notes = notes.trim(),
                                priority = priority,
                                dueAt = dueAt,
                                estimateMinutes = estimate.toIntOrNull() ?: 0,
                                createdAt = System.currentTimeMillis(),
                                tag = tag.trim().ifBlank { null },
                            )
                        } else {
                            base.copy(
                                title = title.trim(),
                                notes = notes.trim(),
                                priority = priority,
                                dueAt = dueAt,
                                estimateMinutes = estimate.toIntOrNull() ?: 0,
                                tag = tag.trim().ifBlank { null },
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
