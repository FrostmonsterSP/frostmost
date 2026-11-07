package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.ui.compose.LocalDarkTheme
import java.text.DateFormat
import java.util.Date

private val STRIP_SHAPE = RoundedCornerShape(14.dp)

/** Colors of a strip or button drawn on the screen background, which is the accent while connected. */
internal data class OnBackgroundColors(val container: Color, val content: Color)

/** Neutral surface for strips and buttons: a light overlay on the accent while lit, a container tone otherwise. */
@Composable
internal fun neutralColors(lit: Boolean): OnBackgroundColors = if (lit) {
    OnBackgroundColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), Color.White)
} else {
    OnBackgroundColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
}

/** Orange "needs attention" tone of the mockup; fixed so it reads the same with dynamic color. */
@Composable
internal fun warnColors(): OnBackgroundColors = if (LocalDarkTheme.current) {
    OnBackgroundColors(Color(0xFF4F3208), Color(0xFFFFE6C7))
} else {
    OnBackgroundColors(Color(0xFFFFE6C7), Color(0xFF4A2703))
}

/**
 * Tinted, rounded block; clickable when [onClick] is set, with [onClickLabel] read by TalkBack as the
 * action. One semantic node: descendants are merged into it. Pass [selectedState] when the block
 * represents one of several mutually exclusive choices, so TalkBack announces which one is active.
 */
@Composable
internal fun HomeTile(
    colors: OnBackgroundColors,
    modifier: Modifier = Modifier,
    shape: Shape = STRIP_SHAPE,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    selectedState: Boolean? = null,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(colors.container)
        .let { if (border != null) it.border(border, shape) else it }
        .let { if (selectedState != null) it.semantics { selected = selectedState } else it }
    Box(
        if (onClick != null) {
            base.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        } else {
            base.semantics(mergeDescendants = true) {}
        }
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) { content() }
    }
}

/** Date in the user's locale, for expiry and "servers from" lines. */
@Composable
internal fun formatHomeDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(millis))
}

/**
 * Explains the current issue. When the action already sits in the action row, the row highlights it
 * and the strip only explains; otherwise the strip carries its own button.
 */
@Composable
internal fun HomeIssueStrip(
    issue: HomeIssue,
    lit: Boolean,
    onOtherServer: () -> Unit,
    onAllowVpn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when (issue) {
        is HomeIssue.UpdateFailed -> updateFailedDate(issue)
            ?.let { stringResource(R.string.home_issue_update_failed_on, formatHomeDate(it)) }
            ?: stringResource(R.string.home_issue_update_failed)
        HomeIssue.ServerUnreachable -> stringResource(R.string.home_issue_server)
        HomeIssue.Expired -> stringResource(R.string.home_issue_expired)
        HomeIssue.TrafficExhausted -> stringResource(R.string.home_issue_traffic)
        HomeIssue.VpnPermissionDenied -> stringResource(R.string.home_issue_permission)
    }
    val action: Pair<String, () -> Unit>? = when (issue) {
        HomeIssue.ServerUnreachable -> stringResource(R.string.home_issue_server_action) to onOtherServer
        HomeIssue.VpnPermissionDenied -> stringResource(R.string.home_issue_permission_action) to onAllowVpn
        else -> null
    }
    // A failed update is calm; every other issue is orange. On the accent both become the light overlay.
    val colors = when {
        lit -> neutralColors(lit = true)
        issue is HomeIssue.UpdateFailed -> OnBackgroundColors(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> warnColors()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = STRIP_SHAPE,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick) {
                    Text(label, fontWeight = FontWeight.Bold, color = colors.content)
                }
            }
        }
    }
}

/** Provider message: two lines, expands on tap. Expansion is visual state only, saved per card id. */
@Composable
internal fun HomeAnnounceStrip(
    cardId: String,
    text: String,
    lit: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(cardId) { mutableStateOf(false) }
    val colors = neutralColors(lit)
    val toggleLabel = stringResource(if (expanded) R.string.home_announce_collapse else R.string.home_announce_expand)
    val state = stringResource(if (expanded) R.string.home_announce_expanded else R.string.home_announce_collapsed)
    HomeTile(
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = state },
        onClick = { expanded = !expanded },
        onClickLabel = toggleLabel,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_about_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun routingPresetLabel(loaded: Boolean, preset: RoutingType?): String =
    if (loaded) stringResource(routingPresetLabelRes(preset)) else ""

@StringRes
internal fun routingPresetLabelRes(preset: RoutingType?): Int = when (preset) {
    RoutingType.GLOBAL -> R.string.home_rules_global
    RoutingType.WHITE_RUSSIA -> R.string.home_rules_russia
    RoutingType.WHITE_IRAN -> R.string.home_rules_iran
    RoutingType.WHITE -> R.string.home_rules_china_white
    RoutingType.BLACK -> R.string.home_rules_china_black
    null -> R.string.home_rules_custom
}

/** Active rule set; opens the rules sheet. */
@Composable
internal fun HomeRulesRow(
    presetLoaded: Boolean,
    preset: RoutingType?,
    lit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = neutralColors(lit)
    val chooseLabel = stringResource(R.string.home_rules_choose)
    HomeTile(
        colors = colors,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = chooseLabel,
    ) {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_routing_24dp), contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_rules), style = MaterialTheme.typography.labelMedium)
                Text(
                    routingPresetLabel(presetLoaded, preset),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(painterResource(R.drawable.ic_expand_more_24dp), contentDescription = null)
        }
    }
}

/** Shown while a switch made during a connection can still be undone. */
@Composable
internal fun HomeUndoBar(
    targetName: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = STRIP_SHAPE,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_switching_to, targetName),
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onUndo) {
                Text(
                    stringResource(R.string.home_undo),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inversePrimary,
                )
            }
        }
    }
}
