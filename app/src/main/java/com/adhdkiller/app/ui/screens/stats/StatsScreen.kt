package com.adhdkiller.app.ui.screens.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adhdkiller.app.core.Time
import com.adhdkiller.app.data.repo.Xp
import com.adhdkiller.app.ui.LocalAppContainer
import com.adhdkiller.app.ui.components.AppCard
import com.adhdkiller.app.ui.components.AppScaffold
import com.adhdkiller.app.ui.components.MutedText
import com.adhdkiller.app.ui.components.Pill
import com.adhdkiller.app.ui.components.SectionHeader
import com.adhdkiller.app.ui.components.StatTile
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.LocalAccent
import com.adhdkiller.app.ui.theme.Success
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import java.time.LocalDate

/**
 * Productivity statistics and the rewards gallery. Everything is computed on-device from the
 * local database — no telemetry, no network.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val accent = LocalAccent.current.color

    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000
    val todayStart = Time.startOfTodayMillis()

    val xp by container.gamification.totalXp.collectAsStateWithLifecycle(initialValue = 0)
    val xpWeek by container.gamification.xpSince(weekAgo).collectAsStateWithLifecycle(initialValue = 0)
    val tasksDoneToday by container.taskRepository.completedSince(todayStart).collectAsStateWithLifecycle(initialValue = 0)
    val tasksDoneWeek by container.taskRepository.completedSince(weekAgo).collectAsStateWithLifecycle(initialValue = 0)
    val focusWeek by container.focusRepository.minutesSince(weekAgo).collectAsStateWithLifecycle(initialValue = 0)
    val distractions by container.distractionRepository.countSince(weekAgo).collectAsStateWithLifecycle(initialValue = 0)
    val focusSessions by container.focusRepository.sessionsSince(weekAgo).collectAsStateWithLifecycle(initialValue = emptyList())

    val level = Xp.levelFor(xp)

    val dailyMinutes = remember(focusSessions) {
        val perDay = HashMap<Long, Int>()
        focusSessions.forEach { session ->
            val date = java.time.Instant.ofEpochMilli(session.startedAt).atZone(Time.zone).toLocalDate()
            val key = Time.startOfDayMillis(date)
            perDay[key] = (perDay[key] ?: 0) + session.minutes
        }
        (6 downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong())
            perDay[Time.startOfDayMillis(date)] ?: 0
        }
    }

    AppScaffold(
        title = "Statistics",
        subtitle = "LAST 7 DAYS",
        onBack = onBack,
    ) {
        SectionHeader("This week")
        Spacer(Modifier.height(10.dp))
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                StatTile(label = "Focus", value = "${focusWeek}m", caption = "minutes", accent = accent)
                StatTile(label = "Tasks", value = tasksDoneWeek.toString(), caption = "completed")
                StatTile(label = "Blocked", value = distractions.toString(), caption = "feeds", accent = accent)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                StatTile(label = "Today", value = tasksDoneToday.toString(), caption = "tasks done")
                StatTile(label = "XP", value = "+$xpWeek", caption = "this week", accent = accent)
            }
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Focus minutes")
        Spacer(Modifier.height(10.dp))
        AppCard {
            WeeklyChart(
                values = dailyMinutes,
                color = accent,
            )
            Spacer(Modifier.height(10.dp))
            MutedText("${focusSessions.size} focus session(s) recorded.")
        }

        Spacer(Modifier.height(22.dp))

        SectionHeader("Rewards")
        Spacer(Modifier.height(10.dp))
        AppCard {
            Text(
                text = "Level $level",
                style = MaterialTheme.typography.displaySmall,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            MutedText("${Xp.xpForLevel(level + 1) - xp} XP until level ${level + 1}")
            Spacer(Modifier.height(16.dp))
            Rewards.forEach { reward ->
                val unlocked = level >= reward.level
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (unlocked) accent.copy(alpha = 0.18f) else Color(0xFF141414))
                            .border(
                                1.dp,
                                if (unlocked) accent.copy(alpha = 0.5f) else Hairline,
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = reward.emoji,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unlocked) accent else TextTertiary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reward.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (unlocked) TextPrimary else TextTertiary,
                        )
                        Text(
                            text = reward.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                    Pill(
                        text = "LV ${reward.level}",
                        color = if (unlocked) Success else TextTertiary,
                        borderColor = if (unlocked) Success.copy(alpha = 0.4f) else Hairline,
                    )
                }
            }
        }
    }
}

private data class Reward(val level: Int, val emoji: String, val title: String, val description: String)

private val Rewards = listOf(
    Reward(1, "🌱", "First steps", "Complete your first task."),
    Reward(2, "🔥", "Week one", "One week of discipline."),
    Reward(5, "⚡", "Momentum", "Five levels of focus."),
    Reward(10, "🧠", "Deep work", "Ten levels — the long game."),
    Reward(15, "🛡️", "Fortress", "Fifteen levels of earned focus."),
    Reward(20, "👑", "Untouchable", "Twenty levels. You are the system."),
)

@Composable
private fun WeeklyChart(values: List<Int>, color: Color) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = remember7()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fraction = value.toFloat() / max
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((96 * fraction).dp.coerceAtLeast(3.dp)),
                    ) {
                        drawRoundRect(
                            color = if (value > 0) color else Hairline,
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun remember7(): List<String> = (6 downTo 0).map { offset ->
    Time.dayName(LocalDate.now().minusDays(offset.toLong()).dayOfWeek).take(1)
}
