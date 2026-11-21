package com.adhdkiller.app.ui.screens.lockdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adhdkiller.app.data.repo.Xp
import com.adhdkiller.app.enforcement.lockdown.LockdownStatus
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The lockdown screen, shared by the lockdown activity and the guardian launcher.
 *
 * It is deliberately the most defensive surface in the app: it must always offer a way to place
 * an emergency call, and it must never be the reason a user cannot get help.
 */
@Composable
fun LockdownPanel(
    status: LockdownStatus,
    windowLabel: String,
    endsAtMillis: Long?,
    emergencyNumbers: List<String>,
    hardMode: Boolean,
    onEmergencyCall: (String) -> Unit,
    onEndEarly: () -> Unit,
    onOpenApp: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remaining = endsAtMillis?.let { it - now }?.coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "LOCKDOWN ACTIVE", style = EyebrowStyle, color = Danger)
            Spacer(Modifier.height(18.dp))
            Text(
                text = windowLabel,
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Other apps are blocked. Incoming calls still come through.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            if (remaining != null) {
                Spacer(Modifier.height(36.dp))
                Text(
                    text = formatRemaining(remaining),
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = "remaining", style = EyebrowStyle, color = TextTertiary)
            }

            Spacer(Modifier.height(40.dp))

            Text(text = "EMERGENCY", style = EyebrowStyle, color = TextTertiary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                emergencyNumbers.take(3).forEach { number ->
                    EmergencyButton(number = number, onClick = { onEmergencyCall(number) })
                }
            }

            Spacer(Modifier.height(36.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // In hard mode there is no shortcut back into the app — the only way out is the
                // deliberate, typed confirmation below.
                if (onOpenApp != null && !hardMode) {
                    GhostButton(text = "Open app", onClick = onOpenApp)
                }
                GhostButton(text = "End early", onClick = { showConfirm = true }, danger = true)
            }

            Spacer(Modifier.height(28.dp))

            if (status.blockedPackages > 0) {
                Text(
                    text = "${status.blockedPackages} apps blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
            status.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showConfirm) {
        EndEarlyDialog(
            hardMode = hardMode,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onEndEarly()
            },
        )
    }
}

/**
 * The single exit door of a lockdown. In hard mode the user has to type the word out, which
 * turns an impulsive tap into a deliberate act.
 */
@Composable
private fun EndEarlyDialog(
    hardMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by remember { mutableStateOf(TextFieldValue("")) }
    val canConfirm = !hardMode || typed.text.trim().equals("END", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("End the lockdown early?") },
        text = {
            Column {
                Text(
                    "The apps you blocked come back immediately and you lose ${-Xp.LOCKDOWN_EARLY_EXIT} XP. " +
                        "Your streak resets. This is the only exit door — use it honestly.",
                )
                if (hardMode) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Hard mode: type END to confirm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text("END") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) { Text("End it", color = Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay locked", color = TextPrimary) }
        },
    )
}

private fun formatRemaining(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun EmergencyButton(number: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Danger.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .background(Color(0xFF140505))
            .clickableNoRipple(onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(text = number, style = MaterialTheme.typography.titleMedium, color = Danger)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (danger) Danger.copy(alpha = 0.5f) else Hairline, RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (danger) Danger else TextSecondary,
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
