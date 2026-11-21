package com.adhdkiller.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.repo.Xp
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.GhostButton
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.Pill
import com.adhdkiller.app.ui.components.PrimaryButton
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.StatTile
import com.adhdkiller.app.ui.components.ThinProgressBar
import com.adhdkiller.app.ui.components.clickableNoRipple
import com.adhdkiller.app.ui.nav.Routes
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.Success
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Today" — the single screen that answers: what is locked, what am I level, what should I do
 * next. Everything else is one tap away.
 */
@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current.color

    val xp by container.gamification.totalXp.collectAsStateWithLifecycle(initialValue = 0)
    val level = Xp.levelFor(xp)
    val progress = Xp.levelProgress(xp)

    val status by container.lockdownState.status.collectAsStateWithLifecycle()
    val tasks by container.taskRepository.openTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by container.habitRepository.habits.collectAsStateWithLifecycle(initialValue = emptyList())
    val logs by container.habitRepository.logsSince(Time.epochDay(Time.today()) - 6)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val blocks by container.scheduleRepository.blocksForDay(Time.isoDayOf(Time.today()))
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var focusStartedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(focusStartedAt) {
        while (focusStartedAt > 0) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val todayEpoch = Time.epochDay(Time.today())
    val loggedToday = logs.filter { it.epochDay == todayEpoch }.map { it.habitId }.toSet()

    AppScaffold(
        title = "ADHD Killer",
        subtitle = "LEVEL $level · ${xp} XP",
    ) {
        // ------------------------------------------------------------------ level strip
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LEVEL $level",
                        style = EyebrowStyle,
                        color = accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${Xp.xpForLevel(level + 1) - xp} XP to level ${level + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
                Pill(text = "${xp} XP", color = accent, borderColor = accent.copy(alpha = 0.4f))
            }
            Spacer(Modifier.height(14.dp))
            ThinProgressBar(progress = progress, color = accent, height = 5)
        }

        Spacer(Modifier.height(18.dp))

        // ------------------------------------------------------------------- enforcement
        SectionHeader("Enforcement")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EnforcementCard(
                modifier = Modifier.weight(1f),
                title = "Lockdown",
                value = if (status.enforced) "LOCKED" else "Free",
                detail = status.activeWindowLabel ?: "No active window",
                accent = if (status.enforced) Danger else Success,
                onClick = { onNavigate(Routes.LOCKDOWN) },
            )
            EnforcementCard(
                modifier = Modifier.weight(1f),
                title = "Blocker",
                value = if (com.adhdkiller.app.enforcement.guard.AppGuardService.instance != null) "ON" else "OFF",
                detail = "Short-form feeds",
                accent = accent,
                onClick = { onNavigate(Routes.BLOCKER) },
            )
        }

        Spacer(Modifier.height(22.dp))

        // ------------------------------------------------------------------ focus timer
        SectionHeader("Focus session")
        Spacer(Modifier.height(10.dp))
        AppCard {
            if (focusStartedAt == 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start a timer",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        MutedText("Every focused minute earns XP.")
                    }
                    PrimaryButton(text = "Start", onClick = {
                        focusStartedAt = System.currentTimeMillis()
                        now = focusStartedAt
                    })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Time.formatDuration(now - focusStartedAt),
                            style = MaterialTheme.typography.displaySmall,
                            color = accent,
                        )
                        Spacer(Modifier.height(3.dp))
                        MutedText("In progress")
                    }
                    GhostButton(
                        text = "Stop",
                        color = TextPrimary,
                        onClick = {
                            val started = focusStartedAt
                            focusStartedAt = 0L
                            scope.launch {
                                container.focusRepository.record(started, System.currentTimeMillis(), "Focus")
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------------------------------------------------------------------- tasks
        SectionHeader("Tasks", trailing = "${tasks.size} open")
        Spacer(Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            AppCard {
                MutedText("No open tasks. Add one from the Tasks tab and start earning XP.")
            }
        } else {
            AppCard {
                tasks.take(5).forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Hairline, RoundedCornerShape(6.dp))
                                .clickableNoRipple { scope.launch { container.taskRepository.setCompleted(task, true) } },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (task.dueAt != null) {
                            Text(
                                text = Time.formatMinute(
                                    Time.minuteOfDay(java.time.Instant.ofEpochMilli(task.dueAt!!)
                                        .atZone(Time.zone).toLocalTime())
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------------------------------------------------------------------- habits
        SectionHeader("Habits", trailing = "${habits.size} tracked")
        Spacer(Modifier.height(10.dp))
        if (habits.isEmpty()) {
            AppCard {
                MutedText("No habits yet. Add one in the Habits tab — streaks are where the XP is.")
            }
        } else {
            AppCard {
                habits.take(5).forEach { habit ->
                    val done = habit.id in loggedToday
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (done) accent else Color(0xFF141414))
                                .border(1.dp, if (done) accent else Hairline, RoundedCornerShape(8.dp))
                                .clickableNoRipple {
                                    scope.launch {
                                        if (done) {
                                            container.habitRepository.unlog(habit.id, todayEpoch)
                                        } else {
                                            container.habitRepository.log(habit, todayEpoch)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Text(
                                    text = habit.emoji,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // -------------------------------------------------------------------- schedule
        SectionHeader("Today's plan")
        Spacer(Modifier.height(10.dp))
        if (blocks.isEmpty()) {
            AppCard {
                MutedText("Nothing scheduled for today. A plan beats willpower.")
            }
        } else {
            AppCard {
                blocks.forEach { block ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${Time.formatMinute(block.startMinute)}–${Time.formatMinute(block.endMinute)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.width(108.dp),
                        )
                        Text(
                            text = block.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(
                text = "Statistics",
                onClick = { onNavigate(Routes.STATS) },
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = "Settings",
                onClick = { onNavigate(Routes.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EnforcementCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickableNoRipple(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(text = title.uppercase(), style = EyebrowStyle, color = TextTertiary)
        Spacer(Modifier.height(10.dp))
        Text(text = value, style = MaterialTheme.typography.displaySmall, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
