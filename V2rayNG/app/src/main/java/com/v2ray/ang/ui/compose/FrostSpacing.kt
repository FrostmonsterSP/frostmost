package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Every gap in the app comes from here; the previous interface mixed 3, 6, 7, 10, 11,
 * 12 and 14 dp in the same screen, which is exactly what reads as "flat and unfinished".
 *
 * The scale is 4-based, not 8-based: 4 dp is the smallest gap that is still visible, and a
 * 4/8/12/16/24/32 ladder gives enough steps without inviting improvisation.
 */
object FrostSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Screen side padding. */
    val screenH = 20.dp

    /** Padding inside a card. */
    val cardPadding = 20.dp

    /** Gap between stacked cards. */
    val cardGap = 12.dp

    /** Minimum touch target; WCAG and Material agree on this number. */
    val touchTarget = 48.dp

    /** Minimum height of a list row. */
    val rowHeight = 64.dp
}

/** Standard screen content padding. */
val FrostScreenPadding = PaddingValues(
    start = FrostSpacing.screenH,
    end = FrostSpacing.screenH,
    top = FrostSpacing.l,
    bottom = FrostSpacing.xl,
)

/**
 * The four surface levels of the app. Depth is expressed by moving a surface one step toward the
 * foreground, never by stacking shadows, and never by tinting a block with the subscription accent:
 * the accent belongs to the hero card alone.
 */
enum class FrostSurfaceLevel {
    /** The page background. */
    Page,

    /** A card resting on the page. */
    Card,

    /** A block inside a card: rows, input fields, nested tiles. */
    Inner,

    /** Something that must read as lifted: the primary action, an active selection. */
    Raised,
}
