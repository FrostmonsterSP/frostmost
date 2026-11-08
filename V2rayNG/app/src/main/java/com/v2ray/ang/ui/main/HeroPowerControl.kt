package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
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

/** Diameter of the round power control. */
private val POWER_DIAMETER = 56.dp

/** The glyph inside it. Well inside the circle, so it can never touch or clip the edge. */
private val POWER_GLYPH = 24.dp

/** Ring thickness of the idle circle: thin enough to read as a state, not as a second button. */
private val POWER_RING = 1.dp

/**
 * The round power control inside the hero card: an *indicator*, never a target.
 *
 * The whole card is the one toggle (see the hero card in `HomeSubscriptionPager.kt`); this circle
 * shows the state the way a switch shows it and carries no clickable of its own, so the same gesture
 * cannot fire twice and TalkBack reaches one node for the card rather than two for one action. On a
 * card that is not the shown one it is inert, like the rest of that card.
 *
 * It draws one glyph and nothing else: the "Turn on"/"Turn off" caption that used to fit inside it
 * did not, and repeated what the status panel beside it says in the user's own language. That glyph
 * is the same in every state — the circle's plate, its ring and the glyph's tone carry the state, so
 * connecting does not swap the drawing for another one.
 *
 * **Why the plate is the accent instead of white.** The button used to be a white (or a translucent
 * white) plate on the accent card. That is a filled control inside a filled control: it competed
 * with the card for the eye, and — the reason it was a bug rather than a preference — it put the
 * glyph through `LocalContentColor`, which the plate had set to `colorScheme.primary`. In the dark
 * theme that primary is a pale blue, so a pale glyph sat on a pale plate, and the "in flight" state
 * dimmed the plate to 0.85 white *and* kept that same pale glyph. Measured on a device, the lock
 * read as a blob in both the busy and the idle state.
 *
 * The plate is therefore the *accent's own* colour, inset from a ring drawn in the card's
 * `on-accent`: the ring is what makes a control that is exactly as bright as the card behind it
 * still read as a control (WCAG 1.4.11, measured 6.15:1 at the worst accent). The glyph is
 * `on-accent` too — white on the accent, the same pair the card's title uses, so it is the measured
 * 4.5:1+ pair rather than a scheme tone chosen for a surface this circle is not drawn on.
 *
 * The glyph itself is always a plain power symbol, not the fork's padlock. The padlock said "大山 is
 * open" in one state and "locked" in the other, but the open one is drawn as an outline whose inner
 * sub-path is *not* reversed, so the un-lockable body fills solid and the shackle turns into a
 * blob at any size below 32dp. A power symbol has one closed path, reads at 24dp, and is what the
 * control actually does.
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
    active: Boolean = true,
) {
    // The glyph's tone, one value for every state of the circle:
    //  * running   — the circle is the inverse surface, so the glyph is its on-colour;
    //  * otherwise  — the circle is the accent itself, so the glyph is `on-accent`;
    //  * not the shown card, or a start already in flight — the same pair, dimmed. It is still a
    //    control that says what it would do, so it is dimmed rather than emptied out.
    val idle = active && (enabled || busy)
    val glyph = when {
        isRunning -> MaterialTheme.colorScheme.inverseOnSurface
        idle -> ACCENT_ON_PAGE
        else -> ACCENT_ON_PAGE_MUTED
    }
    // The ring is what makes a circle painted exactly as bright as the card behind it still read as
    // a control (WCAG 1.4.11). Running drops it: the plate itself has become the contrast.
    val ring = when {
        isRunning -> Color.Transparent
        active && (busy || pressed) -> ACCENT_ON_PAGE
        else -> ACCENT_ON_PAGE_MUTED
    }
    FrostSurface(
        colors = FrostSurfaceColors(
            if (isRunning) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
            glyph,
        ),
        shape = CircleShape,
        border = if (ring == Color.Transparent) null else BorderStroke(POWER_RING, ring),
        modifier = Modifier.size(POWER_DIAMETER),
    ) {
        Box(Modifier.size(POWER_DIAMETER), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_power_24dp),
                contentDescription = null,
                tint = LocalContentColor.current,
                // A fixed square, well inside the circle: the glyph can never be stretched to the
                // plate's width or clipped by its edge, whatever the card's own size does.
                modifier = Modifier.size(POWER_GLYPH),
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
 *
 * **Why it may be drawn disabled.** The chip is laid out on every hero card, including the previews
 * beside the shown one, because its width is what the subscription name gives up in the header row:
 * mounting it only on the shown card made the name wrap one line earlier there, which changed the
 * header height and made the whole plate jump as the pager moved. A disabled chip keeps that space
 * and draws the same box, but the caller dims it to nothing (`alpha(0f)`) — so it must also stop
 * being a target: with [enabled] `false` there is no [clickable], no click, no role and no
 * focusable semantics node, which `alpha` alone would not do (a transparent clickable still eats
 * touches and would swallow the pager's swipe).
 */
@Composable
internal fun ConfigureChip(
    name: String,
    onConfigure: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Draw the chip's plate against the top of its 48dp box instead of centring it in it.
     *
     * The box is a touch target, not a visual: a 11sp label is ~20dp tall, so a centred plate sits
     * about 14dp below where the caller aligned it. Beside a one-line title that is invisible, but
     * beside a title allowed to wrap it lands on the second line — measured on a device, the title
     * occupied y 364–506 and the chip's label rendered at y 375–501, overlapping it. Callers that
     * put this chip next to multi-line text set this so the plate follows the first line.
     */
    alignToFirstLine: Boolean = false,
) {
    val label = stringResource(R.string.home_configure_named, name)
    val text = stringResource(R.string.home_configure)
    Box(
        modifier
            // The chip is one line of 11sp text beside a 14dp glyph, so it is ~20dp tall in itself.
            // It still has to be a 48dp target; the extra height is the target, not the plate.
            .heightIn(min = FrostSpacing.touchTarget)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(onClickLabel = label, role = Role.Button, onClick = onConfigure)
                        .clearAndSetSemantics {
                            contentDescription = label
                            role = Role.Button
                            onClick(label = label) {
                                onConfigure()
                                true
                            }
                        }
                } else {
                    // Nothing to click and nothing to focus: the plate is a preview, not a target.
                    Modifier.clearAndSetSemantics {}
                },
            ),
        contentAlignment = if (alignToFirstLine) Alignment.TopEnd else Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(FrostRadius.chip)
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
}
