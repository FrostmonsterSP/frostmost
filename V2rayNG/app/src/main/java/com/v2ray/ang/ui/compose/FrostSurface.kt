package com.v2ray.ang.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A surface that carries its own colour pair.
 *
 * This type exists because the opposite caused every invisible-text bug in this app: a block was
 * drawn with one background and its children picked a colour meant for a different one, so text
 * ended up black on black. Here the background and its foreground are created together and cannot
 * disagree, and [FrostSurface] pushes the foreground down through `LocalContentColor`, so a child
 * that does not ask for a specific colour inherits the correct one.
 */
data class FrostSurfaceColors(val container: Color, val content: Color)

/** The page: theme background with the matching foreground. */
@Composable
fun frostPageColors(): FrostSurfaceColors = FrostSurfaceColors(
    container = MaterialTheme.colorScheme.background,
    content = MaterialTheme.colorScheme.onBackground,
)

/** A card resting on the page. */
@Composable
fun frostCardColors(): FrostSurfaceColors = FrostSurfaceColors(
    container = MaterialTheme.colorScheme.surface,
    content = MaterialTheme.colorScheme.onSurface,
)

/** A block inside a card. */
@Composable
fun frostInnerColors(): FrostSurfaceColors = FrostSurfaceColors(
    container = MaterialTheme.colorScheme.surfaceContainerLow,
    content = MaterialTheme.colorScheme.onSurface,
)

/** Something that must read as lifted, e.g. the secondary action row. */
@Composable
fun frostRaisedColors(): FrostSurfaceColors = FrostSurfaceColors(
    container = MaterialTheme.colorScheme.surfaceContainerHigh,
    content = MaterialTheme.colorScheme.onSurface,
)

/** The muted foreground for secondary text on a given surface. */
@Composable
fun FrostSurfaceColors.muted(): Color = content.copy(alpha = FROST_MUTED_ALPHA)

/**
 * Secondary text on a surface. 0.72 keeps every pairing above 4.5:1 for body-size text on the
 * light and dark inner surfaces alike; the previous 0.75 was measured at 4.71:1 on the accent.
 */
const val FROST_MUTED_ALPHA = 0.72f

/**
 * The single way to draw a coloured block in this app.
 *
 * Every parameter that could cause a colour mismatch is closed off: the container and its content
 * come in as one pair, the shape comes from the FrostMost radius scale, and `LocalContentColor` is
 * set so descendants inherit it. Callers may pass a [border] instead of relying on a shadow.
 */
@Composable
fun FrostSurface(
    colors: FrostSurfaceColors,
    modifier: Modifier = Modifier,
    shape: Shape = FrostRadius.card,
    elevation: Dp = FrostElevation.card,
    border: androidx.compose.foundation.BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var base = modifier.clip(shape)
    if (elevation > 0.dp) {
        base = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = FROST_SHADOW_COLOR,
                spotColor = FROST_SHADOW_COLOR,
            )
            .clip(shape)
    }
    base = base.background(colors.container)
    if (border != null) {
        base = base.then(Modifier.border(border, shape))
    }
    if (onClick != null) {
        base = base.clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            interactionSource = interactionSource,
            onClick = onClick,
        )
    }
    Box(base) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            content()
        }
    }
}

/** Shadows are tinted rather than pure black, so they read as depth and not as dirt. */
private val FROST_SHADOW_COLOR = Color(0x33000000)

/** Convenience: a rounded clip for row content that must not overflow its card. */
internal fun Modifier.frostClip(radius: Dp = 20.dp): Modifier =
    clip(RoundedCornerShape(radius))
