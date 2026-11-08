package com.v2ray.ang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSurfaceColors
import com.v2ray.ang.ui.compose.FrostSpacing

/**
 * The round power control inside the hero card: an *indicator*, never a target.
 *
 * The whole card is the one toggle (see [HeroConnectionCard]); this circle shows the state the way
 * a switch shows it and carries no clickable of its own, so the same gesture cannot fire twice and
 * TalkBack reaches one node for the card rather than two for one action.
 *
 * It draws one glyph and nothing else. It used to carry the "Turn on"/"Turn off" caption inside a
 * 72dp circle as well, which is where the visual mess came from: the word did not fit, wrapped or
 * clipped against the circle's edge, and it repeated what the status panel beside it already says
 * in the user's own language. The action's name lives on the card node, the state in the panel.
 *
 * Each state is one measured pair from the scheme, so the glyph can never be a pale tone on a pale
 * plate: off is the primary pair (dark glyph on the white plate in the light theme, and in the dark
 * theme the scheme's on-primary against its own primary), on is a translucent plate with white.
 * A start in flight gets its own pair rather than a ring drawn over the icon.
 *
 * [pressed] is the card's press, passed in from card coordinates: a child cannot see a press that
 * lands on its parent, and the circle is half of what that press should animate.
 */
@Composable
internal fun FrostPowerControl(
    isRunning: Boolean,
    enabled: Boolean,
    busy: Boolean,
    pressed: Boolean,
) {
    val plate = when {
        busy -> FrostSurfaceColors(Color.White.copy(alpha = 0.85f), MaterialTheme.colorScheme.primary)
        !enabled -> FrostSurfaceColors(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.85f))
        isRunning -> FrostSurfaceColors(Color.White.copy(alpha = 0.22f), Color.White)
        pressed -> FrostSurfaceColors(Color.White.copy(alpha = 0.85f), MaterialTheme.colorScheme.primary)
        else -> FrostSurfaceColors(Color.White, MaterialTheme.colorScheme.primary)
    }
    FrostSurface(colors = plate, shape = CircleShape) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            // The fork's glass mark: an open lock while the VPN is on, a closed one while it is off.
            // The plate is white or a translucent white, so the glyph comes from the plate's own
            // pair and never from a scheme tone measured against some other background.
            Icon(
                painterResource(if (isRunning) R.drawable.ic_lock_open_24dp else R.drawable.ic_lock_24dp),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * "Configure" affordance inside the hero card, beside the subscription name: quiet, always reachable.
 *
 * It lives inside the plate because it configures that very card; parked outside it, it read as a
 * fragment that had fallen off the card. Staying an inside child does not risk turning the VPN on:
 * a child's own clickable consumes the press before the parent `toggleable` receives it, so the
 * card's toggle never fires for a tap that lands here.
 *
 * [clearAndSetSemantics] is what keeps it a second TalkBack node: the card merges its descendants
 * into one toggle reading, and without this the chip would be swallowed by that merge and its name
 * would never be announced. The colour is the card's own foreground, so the chip stays white on the
 * accent instead of taking a scheme colour measured against a neutral surface.
 */
@Composable
internal fun ConfigureChip(
    name: String,
    onConfigure: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.home_configure_named, name)
    val text = stringResource(R.string.home_configure)
    Row(
        modifier
            .heightIn(min = FrostSpacing.touchTarget)
            .clip(FrostRadius.chip)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onConfigure)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    onConfigure()
                    true
                }
            }
            .padding(horizontal = FrostSpacing.m, vertical = FrostSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
    ) {
        Icon(
            painterResource(R.drawable.ic_settings_24dp),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
