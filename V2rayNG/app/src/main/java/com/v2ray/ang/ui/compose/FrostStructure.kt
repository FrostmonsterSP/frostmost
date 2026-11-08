package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import androidx.compose.ui.res.stringResource

/**
 * Structural primitives shared by every rebuilt screen.
 *
 * These live in the design system rather than in a feature package because several screens need the
 * same grouping and, crucially, the same correctness guarantees: a card group supplies the surface
 * pair to its rows, so a row can never end up with a foreground meant for a different background.
 */

/**
 * A titled group of rows on one card, with dividers between them.
 *
 * This is the main layout unit of the rebuilt app: settings groups, form sections, lists. The card
 * and its content colour are created together here, so rows drawn inside inherit the right colour
 * instead of guessing one.
 */
@Composable
fun FrostCardGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Column(Modifier.padding(start = FrostSpacing.xs, bottom = FrostSpacing.s)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FrostSurface(
            colors = frostCardColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = FrostRadius.card,
        ) {
            Column(content = content)
        }
    }
}

/** A divider between rows of a [FrostCardGroup], inset from the card edge. */
@Composable
fun FrostRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = FrostSpacing.cardPadding),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A labelled section of a form: heading plus the fields under it, on their own card. Editors use
 * this instead of one long flat list of text fields.
 */
@Composable
fun FrostFormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FrostCardGroup(modifier = modifier, title = title, subtitle = description) {
        Column(
            Modifier.padding(FrostSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.m),
        ) {
            content()
        }
    }
}

/**
 * A settings row: title, optional description, optional leading icon, and a trailing slot.
 *
 * When [onClick] is supplied the whole row is one focusable node with one activation; when it also
 * [checked] the state is exposed to accessibility services, so TalkBack reads "on"/"off" and the
 * action once — not a row plus a separate switch target.
 */
@Composable
fun FrostRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stateText = checked?.let {
        stringResource(if (it) R.string.frost_state_on else R.string.frost_state_off)
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = FrostSpacing.rowHeight)
        .let { base ->
            when {
                onClick == null -> base
                checked != null -> base.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { onClick() },
                )
                else -> base.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
            }
        }
        .padding(horizontal = FrostSpacing.cardPadding, vertical = FrostSpacing.m)
        .let { if (checked != null) it.semantics { if (stateText != null) stateDescription = stateText } else it }

    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * The one switch of the app.
 *
 * It exists because the previous code configured only the CHECKED colours, so an unchecked track
 * (#E8E5E8) sat at 1.19:1 against the page and a disabled one dimmed into the same tone — controls
 * that were effectively invisible. Every state is set here explicitly, and the unchecked track keeps
 * a visible outline border, which is what makes an "off" switch legible rather than a pale hole.
 *
 * The borders below stay on purpose: they are the only thing that makes an unchecked control
 * visible (WCAG 1.4.11 non-text contrast), not decoration. That is why this is the one place in the
 * app where an always-drawn outline remains.
 */
@Composable
fun FrostSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledCheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledCheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledUncheckedThumbColor = MaterialTheme.colorScheme.outlineVariant,
            disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledUncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** A round leading icon used by list rows: tonal circle, scheme colours, never a literal. */
@Composable
fun FrostLeadingIcon(painter: Painter, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}
