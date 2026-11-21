package com.adhdkiller.app.ui.screens.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adhdkiller.app.enforcement.app.AppInventory
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The guardian launcher's app drawer.
 *
 * While a lockdown is running, [apps] only contains the packages that are still allowed, so the
 * blocked apps are not merely refused in the foreground — they are not even offered here.
 */
@Composable
fun LauncherScreen(
    apps: List<AppInventory.AppEntry>,
    onLaunch: (String) -> Unit,
    onOpenApp: () -> Unit,
    locked: Boolean,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 40.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatClock(now),
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (locked) "LOCKDOWN — only the allowed apps are here" else "ADHD Killer",
                    style = EyebrowStyle,
                    color = TextTertiary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenApp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = "Killer", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(28.dp))

        if (apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No apps are allowed right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(apps, key = { it.packageName }) { entry ->
                    AppTile(entry = entry, onClick = { onLaunch(entry.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AppTile(entry: AppInventory.AppEntry, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(entry.packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(entry.packageName)
            drawable.toBitmap().asImageBitmap()
        }.getOrNull()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Text(
                    text = entry.label.take(1).uppercase(Locale.US),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The strip of apps that stay reachable during a lockdown.
 *
 * The guardian launcher is the HOME app, so it is the one place where the allowed apps can be
 * offered while everything else is blocked: the dialer for an emergency call, the keyboard, the
 * settings — whatever the user put on the allowlist.
 */
@Composable
fun LockedAppsStrip(
    apps: List<AppInventory.AppEntry>,
    onLaunch: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070707))
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        Text(
            text = "ALLOWED APPS",
            style = EyebrowStyle,
            color = TextTertiary,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(apps, key = { it.packageName }) { entry ->
                SmallAppTile(entry = entry, onClick = { onLaunch(entry.packageName) })
            }
        }
    }
}

@Composable
private fun SmallAppTile(entry: AppInventory.AppEntry, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(entry.packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(entry.packageName)
            drawable.toBitmap().asImageBitmap()
        }.getOrNull()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Text(
                    text = entry.label.take(1).uppercase(Locale.US),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatClock(millis: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = millis
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}
