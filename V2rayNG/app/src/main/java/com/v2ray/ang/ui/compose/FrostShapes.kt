package com.v2ray.ang.ui.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * FrostMost shapes. Generous radii are what separate this language from a stock Material app; the
 * scale is closed on purpose so a component never invents its own corner.
 *
 * The relationship is deliberate: the larger the surface, the larger the radius. A card at 28 dp
 * containing a tile at 18 dp and a chip at 12 dp reads as nested, not as three unrelated objects.
 */
private val FrostShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** The shape scale these tokens expose. */
object FrostRadius {
    /** Chips, badges, small tags. */
    val chip = RoundedCornerShape(12.dp)

    /** Buttons and small tiles. */
    val tile = RoundedCornerShape(18.dp)

    /** Secondary cards inside a hero card. */
    val inner = RoundedCornerShape(20.dp)

    /** The headline card. */
    val card = RoundedCornerShape(28.dp)

    /** Large sheets and the hero block. */
    val hero = RoundedCornerShape(34.dp)

    /** Fully round: the power control, pills. */
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Elevation. Depth here is carried by a light tint step plus one soft shadow on the primary action
 * only, never by stacking many shadows: the goal is "the card is a plate above the page", not a
 * pile of cards floating at different heights.
 */
object FrostElevation {
    /** The page itself. */
    val page = 0.dp

    /** A flat card that only needs to separate by tone. */
    val card = 0.dp

    /** The hero connection card: one soft shadow. */
    val hero = 2.dp

    /** The primary action, so it reads as pressable. */
    val action = 6.dp

    /** Sheets and dialogs. */
    val overlay = 12.dp
}

internal val FrostShapesForComposition = FrostShapes
