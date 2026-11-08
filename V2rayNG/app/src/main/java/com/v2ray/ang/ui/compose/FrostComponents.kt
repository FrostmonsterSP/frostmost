package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The buttons of FrostMost.
 *
 * Why these exist at all: the previous interface used `HomeTile` for every clickable block and
 * Material's default buttons elsewhere. That gave one visual weight to a primary action, a row and
 * a link, which is the "flat" complaint. Here there are exactly three weights, and the difference
 * between them is not just colour: size, elevation and corner radius change together.
 */

/** A filled, lifted action: one per screen, at most. */
@Composable
fun FrostPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    val colors = frostActionColors(enabled)
    FrostSurface(
        colors = colors,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        shape = FrostRadius.tile,
        elevation = if (enabled) FrostElevation.action else 0.dp,
        onClick = onClick,
        onClickLabel = label,
        enabled = enabled,
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = FrostSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A quieter action: same geometry, no shadow, tonal background. */
@Composable
fun FrostSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    FrostSurface(
        colors = frostSecondaryActionColors(enabled),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = FrostRadius.tile,
        onClick = onClick,
        onClickLabel = label,
        enabled = enabled,
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = FrostSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** A text-only action, for the least important path. */
@Composable
fun FrostTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(FrostRadius.tile)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = label)
            .padding(horizontal = FrostSpacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/**
 * The round main control. Size and elevation make it obviously the primary target without dimming
 * everything else, and the label always sits under the glyph so the action is readable without
 * colour or icon recognition.
 */
@Composable
fun FrostCircleAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = if (filled) frostActionColors(enabled) else frostSecondaryActionColors(enabled)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.96f else 1f,
        animationSpec = homeMotionSpringSpec(),
        label = "frostCirclePress",
    )
    FrostSurface(
        colors = colors,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = CircleShape,
        elevation = if (filled && enabled) FrostElevation.action else 0.dp,
    ) {
        Column(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    onClickLabel = label,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = FrostSpacing.xxs, start = 6.dp, end = 6.dp),
            )
        }
    }
}

/** Small status or filter tag. Never carries meaning by colour alone: it always has a label. */
@Composable
fun FrostChip(
    label: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val shape = FrostRadius.chip
    // Background first, then the clickable, so the ripple draws on top of the chip rather than
    // being covered by it.
    val base = modifier
        .clip(shape)
        .background(container)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        )
    Row(
        base
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Action colours. The disabled state is a real state: it keeps enough contrast to be read, because
 * a disabled control that becomes invisible is a bug, not a design choice.
 */
@Composable
fun frostActionColors(enabled: Boolean): FrostSurfaceColors = if (enabled) {
    FrostSurfaceColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
} else {
    FrostSurfaceColors(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun frostSecondaryActionColors(enabled: Boolean): FrostSurfaceColors = if (enabled) {
    FrostSurfaceColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
} else {
    FrostSurfaceColors(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The filled pair, stated as its own name: the one button of the top bar uses it, and it must stay
 * a filled *action*, not the quiet tonal plate of [frostSecondaryActionColors]. Passing the tonal
 * plate for a button that reads as the primary one of its bar made it a black slab in the light
 * theme and a grey one in the dark, beside a tile painted with the page's own pair.
 */
@Composable
fun frostPrimaryActionColors(enabled: Boolean): FrostSurfaceColors = frostActionColors(enabled)

/**
 * A control of the top bar on the neutral page: the raised neutral step, opaque in both themes.
 *
 * It used to be [frostPageColors], i.e. `background` — the exact colour the bar is drawn on — so the
 * tile dissolved into the page and its glyph floated with no visible target. A step of tone is what
 * separates it, and the same step is what every other quiet control of the app uses.
 */
@Composable
fun frostTopBarActionColors(): FrostSurfaceColors = FrostSurfaceColors(
    container = MaterialTheme.colorScheme.surfaceContainerHigh,
    content = MaterialTheme.colorScheme.onSurface,
)

/**
 * A control that sits on the subscription accent: the translucent white plate, with the page's own
 * `on-accent` as its foreground.
 *
 * This pair exists because the alternative was measurably wrong twice. A scheme role (`primary`, or
 * `surfaceContainerHigh`) drawn over the accent turned the control into a dark slab; and the plate
 * itself has to be *opaque enough* that the accent behind it does not read through as a different
 * colour, which is why [HOME_ON_ACCENT_OVERLAY] is the one alpha every block below the card uses.
 *
 * `HOME_ON_ACCENT_OVERLAY` and `ACCENT_ON_PAGE` live in `ui/main/HomeModels.kt`: they are the
 * home screen's own measured constants, and a Compose file may not depend on that package.
 */
@Composable
fun topBarOnAccentColors(): FrostSurfaceColors = FrostSurfaceColors(
    container = Color.White.copy(alpha = 0.10f),
    content = Color.White,
)
