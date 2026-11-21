package com.adhdkiller.app.ui.screens.schedule

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.db.entity.BlockKind
import com.adhdkiller.app.data.db.entity.ScheduleBlockEntity
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
import java.time.LocalTime

@Composable
fun ScheduleScreen() {
    val container = LocalAppContainer.current
    val repo = container.scheduleRepository
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val todayIso = Time.isoDayOf(Time.today())
    var selectedDay by remember { mutableStateOf(todayIso) }

    val blockFlow = remember(selectedDay) { repo.blocksForDay(selectedDay) }
    val blocks by blockFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editing by remember { mutableStateOf<ScheduleBlockEntity?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    val ordered = blocks.sortedBy { it.startMinute }
    val plannedMinutes = ordered.sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }
    val focusMinutes = ordered.filter { it.kind == BlockKind.FOCUS || it.kind == BlockKind.DEEP_WORK }
        .sumOf { (it.endMinute - it.startMinute).coerceAtLeast(0) }

    AppScaffold(
        title = "Schedule",
        subtitle = Time.dayName(DayOfWeek.of(selectedDay)),
        actions = {
            HeaderAction(text = "Add", onClick = { editing = null; editorOpen = true })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            DaySelector(selectedDay = selectedDay, onSelect = { selectedDay = it })

            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Planned",
                    value = formatMinutes(plannedMinutes),
                    modifier = Modifier.weight(1f),
                    accent = accent,
                )
                StatTile(
                    label = "Focus",
                    value = formatMinutes(focusMinutes),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(text = "Day plan", trailing = "${ordered.size}")
            Spacer(Modifier.height(10.dp))

            if (ordered.isEmpty()) {
                EmptyState(
                    title = "Nothing planned",
                    subtitle = "Block out the work before the day decides for you.",
                    actionLabel = "Add block",
                    onAction = { editing = null; editorOpen = true },
                )
            } else {
                ordered.forEach { block ->
                    BlockRow(
                        block = block,
                        onEdit = { editing = block; editorOpen = true },
                        onDelete = { scope.launch { repo.delete(block) } },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (editorOpen) {
        BlockEditorDialog(
            existing = editing,
            defaultDay = selectedDay,
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
private fun DaySelector(selectedDay: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current.color
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..7).forEach { day ->
            val active = day == selectedDay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) InkHigh else Color.Transparent)
                    .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                    .clickableNoRipple { onSelect(day) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Time.dayName(DayOfWeek.of(day)).take(3),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) TextPrimary else TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: ScheduleBlockEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current.color

    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = Time.formatMinute(block.startMinute),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
            MutedText(Time.formatMinute(block.endMinute))
        }

        Spacer(Modifier.width(10.dp))

        AppCard(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    if (block.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        MutedText(block.note)
                    }
                    Spacer(Modifier.height(8.dp))
                    Pill(
                        text = kindLabel(block.kind),
                        color = accent,
                        borderColor = Hairline,
                    )
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
}

private fun kindLabel(kind: BlockKind): String = when (kind) {
    BlockKind.FOCUS -> "Focus"
    BlockKind.DEEP_WORK -> "Deep work"
    BlockKind.BREAK -> "Break"
    BlockKind.PERSONAL -> "Personal"
    BlockKind.SOCIAL -> "Social"
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}h ${rest}m"
        hours > 0 -> "${hours}h"
        else -> "${rest}m"
    }
}

@Composable
private fun BlockEditorDialog(
    existing: ScheduleBlockEntity?,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (ScheduleBlockEntity) -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalAccent.current.color

    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: BlockKind.FOCUS) }
    var day by remember(existing?.id) { mutableStateOf(existing?.dayOfWeek ?: defaultDay) }
    var startMinute by remember(existing?.id) { mutableStateOf(existing?.startMinute ?: 9 * 60) }
    var endMinute by remember(existing?.id) { mutableStateOf(existing?.endMinute ?: 10 * 60) }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (existing == null) "New block" else "Edit block",
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
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "KIND",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlockKind.entries.forEach { value ->
                        val active = kind == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) InkHigh else Color.Transparent)
                                .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                                .clickableNoRipple { kind = value }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = kindLabel(value),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) TextPrimary else TextTertiary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "TIME",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton(
                        text = "Start ${Time.formatMinute(startMinute)}",
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> startMinute = hour * 60 + minute },
                                startMinute / 60,
                                startMinute % 60,
                                true,
                            ).show()
                        },
                    )
                    TimeButton(
                        text = "End ${Time.formatMinute(endMinute)}",
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> endMinute = hour * 60 + minute },
                                endMinute / 60,
                                endMinute % 60,
                                true,
                            ).show()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "DAY",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..7).forEach { value ->
                        val active = value == day
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) InkHigh else Color.Transparent)
                                .border(1.dp, if (active) accent else Hairline, RoundedCornerShape(10.dp))
                                .clickableNoRipple { day = value }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = Time.dayName(DayOfWeek.of(value)).take(2),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) TextPrimary else TextTertiary,
                            )
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
                            ScheduleBlockEntity(
                                title = title.trim(),
                                dayOfWeek = day,
                                startMinute = startMinute,
                                endMinute = endMinute,
                                kind = kind,
                                note = note.trim(),
                            )
                        } else {
                            base.copy(
                                title = title.trim(),
                                dayOfWeek = day,
                                startMinute = startMinute,
                                endMinute = endMinute,
                                kind = kind,
                                note = note.trim(),
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
private fun TimeButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, HairlineStrong, RoundedCornerShape(12.dp))
            .clickableNoRipple { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    }
}
