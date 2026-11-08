package com.v2ray.ang.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.FrostElevation
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSurfaceColors
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.LocalStatusColors
import com.v2ray.ang.ui.compose.homeMotionSpringSpec

/**
 * The hero card: what the app is for, in one plate. It is also the on/off switch of the VPN.
 *
 * Design rules applied here (this is the "financial" language):
 * - the subscription accent fills this card and everything below it while the VPN is on;
 * - the traffic figure is the largest number on screen, because it is the number people open the
 *   app to see; the connection state is second;
 * - depth comes from one soft shadow and a tonal inner panel, not from stacked borders;
 * - every colour pair is created by the surface system, so text cannot end up black on black.
 *
 * Interaction rules, which are the fragile part:
 * - the plate is a single toggle node: one name, one on/off state, one action. The circle and the
 *   state panel inside it are merged into that node and carry no action of their own, so touch,
 *   TalkBack, Enter and D-pad centre all reach the same target once;
 * - "Configure" is the one descendant that keeps its own node, because it does something else. It
 *   sits above the plate ([ConfigureChip] is a sibling of the plate, not a child of it), so its
 *   press never passes through the toggle;
 * - the plate stays enabled while a start is in flight and the action itself refuses the repeat
 *   ([canStartFromOff]). A disabled plate would swallow the tap and would report the card as
 *   unavailable, which it is not: it is still a switch.
 */
@Composable
internal fun HeroConnectionCard(
    card: HomeCard,
    connection: HomeConnection,
    accent: Color,
    onToggle: () -> Unit,
    onConfigure: () -> Unit,
) {
    // The accent palette is fixed so white always reads on it; the check keeps that true if a
    // future palette entry is added carelessly.
    val onAccent = ACCENT_ON_PAGE
    val mutedOnAccent = ACCENT_ON_PAGE_MUTED

    val status = connection.status
    val on = heroSwitchOn(connection.isRunning, status == HomeStatus.SWITCHING)
    val canToggle = canStartFromOff(connection.isRunning, connection.isConnecting, connection.powerEnabled)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The press is animated on the whole card and read by the circle inside it: a press that lands
    // on the plate is not visible to its children, so it is passed down from here.
    val scale by animateFloatAsState(
        targetValue = if (canToggle && pressed) 0.99f else 1f,
        animationSpec = homeMotionSpringSpec(),
        label = "heroPress",
    )

    FrostSurface(
        colors = FrostSurfaceColors(accent, onAccent),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .toggleable(
                value = on,
                role = Role.Switch,
                interactionSource = interactionSource,
                // The press is animated on the card itself, so the ripple would only fight the
                // scale for the same gesture.
                indication = null,
                onValueChange = { if (canToggle) onToggle() },
            ),
        shape = FrostRadius.hero,
        elevation = FrostElevation.hero,
    ) {
        Column(
            // Everything inside is one reading of the same card, so it merges into the toggle
            // node instead of offering separate nodes for the name, the figures and the state.
            // The configure chip opts out of the merge below with its own semantics node.
            Modifier
                .semantics(mergeDescendants = true) {}
                .padding(FrostSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (card.isManual) {
                        Text(
                            stringResource(R.string.home_manual_group),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedOnAccent,
                        )
                    }
                }
                // Inside the plate, because it belongs to this card and looked like a loose
                // fragment while it sat beside it. A child with its own click handler consumes
                // the press before the parent toggleable sees it, so "configure" still cannot
                // turn the VPN on; clearAndSetSemantics keeps it a separate TalkBack node with
                // its own name instead of being merged into the card's single reading.
                ConfigureChip(
                    name = card.name,
                    onConfigure = onConfigure,
                    color = onAccent,
                    modifier = Modifier.padding(top = FrostSpacing.xs),
                )
            }

            // --- the number: traffic used of total ---
            SubscriptionFigures(card = card, onAccent = onAccent, muted = mutedOnAccent)

            // --- the state panel inside the hero ---
            StatusPanel(
                status = status,
                serverName = connection.serverName,
                delayMillis = connection.delayMillis,
                onAccent = onAccent,
                muted = mutedOnAccent,
                isRunning = connection.isRunning,
                // The screen's own rule, reused rather than restated: a running VPN is always
                // stoppable, and a start in flight is not started twice.
                enabled = canToggle,
                pressed = pressed,
            )
        }
    }
}

/** Traffic and expiry, sized as the primary information of the card. */
@Composable
private fun SubscriptionFigures(
    card: HomeCard,
    onAccent: Color,
    muted: Color,
) {
    val info = card.info
    if (info == null) {
        Text(
            stringResource(R.string.home_traffic_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
        Text(
            info.usedBytes.toTrafficString(),
            style = MaterialTheme.typography.displaySmall,
            color = onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (info.totalBytes <= 0L) {
                stringResource(R.string.home_traffic_unlimited_short)
            } else {
                stringResource(R.string.home_traffic_of_short, info.totalBytes.toTrafficString())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        info.trafficFraction?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    // White on white would be invisible: the rail is a translucent white and the
                    // filled part is solid white, so the bar reads on any accent.
                    .clearAndSetSemantics {},
                color = onAccent,
                trackColor = Color.White.copy(alpha = 0.28f),
                drawStopIndicator = {},
            )
        }
        val expire = info.expireMillis
        Text(
            if (expire == null) {
                stringResource(R.string.home_expire_none)
            } else {
                val date = formatHomeDate(expire)
                stringResource(if (info.expired) R.string.home_expired_on else R.string.home_expire_until, date)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (info.expired) FontWeight.SemiBold else FontWeight.Normal,
            color = muted,
        )
    }
}

/**
 * The power indicator and the connection state, on a tonal panel so the reading part of the card
 * stays grouped. Nothing here is a target: the card is.
 *
 * The status label travels in a chip that brings its own background. A saturated status colour
 * drawn straight on an accent was measured at 1.08–1.29:1 at every hue, which is why the scheme
 * gives each role a `chip`/`onChip` pair; disconnection has no role, so it takes a translucent
 * white plate and the accent's own on-colour.
 */
@Composable
private fun StatusPanel(
    status: HomeStatus,
    serverName: String?,
    delayMillis: Long?,
    onAccent: Color,
    muted: Color,
    isRunning: Boolean,
    enabled: Boolean,
    pressed: Boolean,
) {
    val roles = LocalStatusColors.current
    val (chipContainer, chipContent) = when (status) {
        HomeStatus.CONNECTED -> roles.success.chip to roles.success.onChip
        HomeStatus.CONNECTING, HomeStatus.SWITCHING -> roles.warning.chip to roles.warning.onChip
        HomeStatus.FAILED -> roles.danger.chip to roles.danger.onChip
        HomeStatus.DISCONNECTED -> Color.White.copy(alpha = 0.18f) to onAccent
    }
    FrostSurface(
        colors = FrostSurfaceColors(Color.White.copy(alpha = 0.14f), onAccent),
        shape = FrostRadius.inner,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(FrostSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
        ) {
            FrostPowerControl(
                isRunning = isRunning,
                enabled = enabled,
                busy = status == HomeStatus.CONNECTING || status == HomeStatus.SWITCHING,
                pressed = pressed,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
                Text(
                    stringResource(statusLabel(status)),
                    style = MaterialTheme.typography.labelLarge,
                    color = chipContent,
                    modifier = Modifier
                        .clip(FrostRadius.chip)
                        .background(chipContainer)
                        .padding(horizontal = FrostSpacing.s, vertical = FrostSpacing.xxs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    serverLine(serverName, delayMillis, status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: HomeStatus): Int = when (status) {
    HomeStatus.DISCONNECTED -> R.string.home_status_disconnected
    HomeStatus.CONNECTING -> R.string.home_status_connecting
    HomeStatus.SWITCHING -> R.string.home_status_switching
    HomeStatus.CONNECTED -> R.string.home_status_connected
    HomeStatus.FAILED -> R.string.home_status_failed
}

@Composable
private fun serverLine(serverName: String?, delayMillis: Long?, status: HomeStatus): String {
    val name = serverName ?: return stringResource(R.string.home_server_none)
    val delay = delayMillis
    return if (status == HomeStatus.CONNECTED && delay != null && delay > 0L) {
        stringResource(R.string.home_server_delay, name, delay)
    } else {
        stringResource(R.string.home_server, name)
    }
}
