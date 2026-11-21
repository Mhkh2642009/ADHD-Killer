package com.adhdkiller.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adhdkiller.app.ui.theme.Danger
import com.adhdkiller.app.ui.theme.EyebrowStyle
import com.adhdkiller.app.ui.theme.Hairline
import com.adhdkiller.app.ui.theme.HairlineStrong
import com.adhdkiller.app.ui.theme.Ink
import com.adhdkiller.app.ui.theme.InkHigh
import com.adhdkiller.app.ui.theme.TextPrimary
import com.adhdkiller.app.ui.theme.TextSecondary
import com.adhdkiller.app.ui.theme.TextTertiary

/** A bordered container used for every card in the app. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Ink)
        .border(1.dp, Hairline, RoundedCornerShape(16.dp))

    Box(modifier = if (onClick != null) base.clickableNoRipple(onClick = onClick) else base) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text.uppercase(), style = EyebrowStyle, color = TextTertiary)
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    caption: String? = null,
) {
    Column(modifier = modifier) {
        Text(text = label.uppercase(), style = EyebrowStyle, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = accent ?: TextPrimary,
        )
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = caption, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = TextPrimary,
    contentColor: Color = Color.Black,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color else InkHigh)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else TextTertiary,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, HairlineStrong, RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickableNoRipple(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = TextPrimary,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = HairlineStrong,
            ),
        )
    }
}

@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    height: Int = 4,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    borderColor: Color = Hairline,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun DangerText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = Danger, modifier = modifier)
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = TextTertiary, modifier = modifier)
}

/** Click without the ripple — the app uses hairline borders instead of Material's defaults. */
@Composable
fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}

@Composable
fun Dot(color: Color, size: Int = 6) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}
