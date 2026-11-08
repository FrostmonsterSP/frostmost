package com.v2ray.ang.ui.compose

import org.junit.jupiter.api.Assertions.assertTrue
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * Guards the colour scheme in docs/superpowers/specs/2026-11-08-frostmost-colour-scheme.md.
 *
 * Every threshold here came from a defect measured on a device, not from a style preference: the
 * delay label was unreadable at 1.04:1 on the subscription accent, and the old `secondary` orange
 * reached only 2.70:1. A colour that fails these assertions is not a redesign, it is a regression.
 */
class ColourSchemeTest {

    /** WCAG 2.1 relative luminance; note the 0.04045 threshold, not a naive linearisation. */
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Compose packs a Color into a ULong as an sRGB float per channel, not as packed ARGB, so the
     * conversion goes through toArgb() and is rebuilt as a plain long for the contrast maths.
     */
    private fun colorOf(value: androidx.compose.ui.graphics.Color): Long =
        value.toArgb().toLong() and 0xFFFFFFFFL

    /** Text threshold: 4.5:1 is WCAG AA for normal-sized text and is never rounded up. */
    private val TEXT = 4.5

    /** Non-text threshold for information that indicates a state (WCAG 1.4.11). */
    private val NON_TEXT = 3.0

    private val allStatus = listOf(
        "success light" to LightStatusSuccess,
        "warning light" to LightStatusWarning,
        "danger light" to LightStatusDanger,
        "info light" to LightStatusInfo,
        "success dark" to DarkStatusSuccess,
        "warning dark" to DarkStatusWarning,
        "danger dark" to DarkStatusDanger,
        "info dark" to DarkStatusInfo,
    )

    @Test
    fun statusTextIsReadableOnItsChipInEveryTheme() {
        allStatus.forEach { (name, status) ->
            val ratio = contrast(colorOf(status.onChip), colorOf(status.chip))
            assertTrue(ratio >= TEXT, "$name: text on chip is $ratio, needs $TEXT")
        }
    }

    @Test
    fun statusTextIsReadableOnNeutralSurfacesInEveryTheme() {
        // onNeutral is the saturated tone used for coloured text on the page itself, per theme.
        listOf(
            "success light" to LightStatusSuccess.onNeutral to 0xFFFFFFFF,
            "warning light" to LightStatusWarning.onNeutral to 0xFFFFFFFF,
            "danger light" to LightStatusDanger.onNeutral to 0xFFFFFFFF,
            "info light" to LightStatusInfo.onNeutral to 0xFFFFFFFF,
            "success dark" to DarkStatusSuccess.onNeutral to 0xFF1C1B1F,
            "warning dark" to DarkStatusWarning.onNeutral to 0xFF1C1B1F,
            "danger dark" to DarkStatusDanger.onNeutral to 0xFF1C1B1F,
            "info dark" to DarkStatusInfo.onNeutral to 0xFF1C1B1F,
        ).forEach { (pair, background) ->
            val (name, onNeutral) = pair
            val ratio = contrast(colorOf(onNeutral), background)
            assertTrue(ratio >= TEXT, "$name: onNeutral is $ratio on the page, needs $TEXT")
        }
    }

    /**
     * The defect this scheme exists for: a status colour painted onto a subscription accent. The chip
     * must stay distinguishable from every accent, so its edge and its light background are visible.
     */
    @Test
    fun statusChipSeparatesFromEverySubscriptionAccent() {
        val lightAccents = listOf(0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00)
        val darkAccents = listOf(0xFF3556D6, 0xFF853197, 0xFF0C6E75, 0xFF1E6B3C, 0xFFA1254C, 0xFF8F5600)
        listOf(
            "success light" to LightStatusSuccess to lightAccents,
            "warning light" to LightStatusWarning to lightAccents,
            "danger light" to LightStatusDanger to lightAccents,
            "info light" to LightStatusInfo to lightAccents,
            "success dark" to DarkStatusSuccess to darkAccents,
            "warning dark" to DarkStatusWarning to darkAccents,
            "danger dark" to DarkStatusDanger to darkAccents,
            "info dark" to DarkStatusInfo to darkAccents,
        ).forEach { (pair, accents) ->
            val (name, status) = pair
            accents.forEach { accent ->
                val ratio = contrast(colorOf(status.chip), accent)
                assertTrue(
                    ratio >= NON_TEXT,
                    "$name: chip vs accent ${accent.toString(16)} is $ratio, needs $NON_TEXT",
                )
            }
        }
    }

    /**
     * The discarded dark-chip variant sank into the dark accents at 1.90:1. This asserts the dark
     * chips stay light, which is the property that fix relies on.
     */
    @Test
    fun darkStatusChipsAreLighterThanTheDarkAccents() {
        listOf(DarkStatusSuccess, DarkStatusWarning, DarkStatusDanger, DarkStatusInfo).forEach { status ->
            assertTrue(
                luminance(colorOf(status.chip)) > 0.45,
                "dark chip ${colorOf(status.chip).toString(16)} is too dark to separate from the accents",
            )
        }
    }

    @Test
    fun textRolesAreReadableOnSurfaceInLightTheme() {
        val surface = 0xFFFFFFFF
        mapOf(
            "onSurface" to 0xFF1C1B1F,
            "onSurfaceVariant" to 0xFF44474E,
            "primary" to 0xFF0B57D0,
            "secondary" to 0xFFB3261E,
            "tertiary" to 0xFF0A6E4A,
            "error" to 0xFF9B1C1C,
            "outline" to 0xFF6C6F78,
        ).forEach { (name, argb) ->
            val ratio = contrast(argb, surface)
            assertTrue(ratio >= TEXT, "light $name on surface is $ratio, needs $TEXT")
        }
    }

    @Test
    fun textRolesAreReadableOnSurfaceInDarkTheme() {
        val surface = 0xFF1C1B1F
        mapOf(
            "onSurface" to 0xFFE5E1E6,
            "onSurfaceVariant" to 0xFFC7C5CE,
            "primary" to 0xFFAAC7FF,
            "secondary" to 0xFFFFB4AB,
            "tertiary" to 0xFF7EE0A6,
            "error" to 0xFFFFB3AC,
            "outline" to 0xFF8E909A,
        ).forEach { (name, argb) ->
            val ratio = contrast(argb, surface)
            assertTrue(ratio >= TEXT, "dark $name on surface is $ratio, needs $TEXT")
        }
    }


    /**
     * `secondary` used to be the interactive accent (cursor, switch, checkbox, scrollbar, progress)
     * in 26 places. In this scheme `secondary` is the red error-adjacent role, so those controls
     * turned red and read as failures. They now take `primary`; this asserts the red role stays far
     * enough from the action colour that no control can drift back onto it by accident.
     */
    @Test
    fun interactiveAccentIsDistinctFromTheSecondaryRole() {
        // Light theme: primary is blue, secondary is red. They must not be the same colour.
        val lightPrimary = 0xFF0B57D0
        val lightSecondary = 0xFFB3261E
        assertTrue(
            lightPrimary != lightSecondary,
            "primary and secondary must stay distinct roles",
        )
        // Both must read on the surface, which the role tests already assert; here we only require
        // that the two are visually different enough to be told apart at a glance.
        val channelDelta = kotlin.math.abs(((lightPrimary shr 16) and 0xFF) - ((lightSecondary shr 16) and 0xFF)) +
            kotlin.math.abs(((lightPrimary shr 8) and 0xFF) - ((lightSecondary shr 8) and 0xFF)) +
            kotlin.math.abs((lightPrimary and 0xFF) - (lightSecondary and 0xFF))
        assertTrue(channelDelta > 100, "primary and secondary are too close: delta $channelDelta")
    }


    /**
     * The row text of the server list follows the surface: `onSurface` on a neutral page, white on
     * the subscription accent. Before this the names were `onSurface` on the accent — 2.6:1, which
     * is the "black on black" the user reported while the VPN was on.
     */
    @Test
    fun rowTextIsReadableOnBothSurfaces() {
        val accents = listOf(
            0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00,
            0xFF3556D6, 0xFF853197, 0xFF0C6E75, 0xFF1E6B3C, 0xFFA1254C, 0xFF8F5600,
        )
        accents.forEach { accent ->
            val ratio = contrast(0xFFFFFFFF, accent)
            assertTrue(ratio >= TEXT, "row name on accent ${accent.toString(16)} is $ratio")
        }
        // And the neutral case the row falls back to.
        val neutral = contrast(0xFF1C1B1F, 0xFFFFFFFF)
        assertTrue(neutral >= TEXT, "row name on the neutral page is $neutral")
    }

    /**
     * The theme root paints `background`, so a screen that draws `onSurface` text without a Surface
     * of its own can no longer land black-on-whatever. This asserts the pair the root supplies.
     */
    @Test
    fun rootSurfacePairIsReadableInBothThemes() {
        val light = contrast(0xFF1C1B1F, 0xFFFCF8FA)   // onBackground on background
        val dark = contrast(0xFFE5E1E6, 0xFF141317)
        assertTrue(light >= TEXT, "light root pair is $light")
        assertTrue(dark >= TEXT, "dark root pair is $dark")
    }

    /** The traffic bar's unfilled track must be visible against the surface it sits on. */
    @Test
    fun progressTrackSeparatesFromItsSurface() {
        // Light: outlineVariant #C4C6CF on surface #FFFFFF.
        val lightTrack = contrast(0xFFC4C6CF, 0xFFFFFFFF)
        assertTrue(lightTrack >= 1.4, "light track is $lightTrack against surface")
        // Dark: outlineVariant #44464F on surface #1C1B1F.
        val darkTrack = contrast(0xFF44464F, 0xFF1C1B1F)
        assertTrue(darkTrack >= 1.4, "dark track is $darkTrack against surface")
    }

    @Test
    fun onColorsAreReadableOnTheirOwnFill() {
        mapOf(
            "light onPrimary" to (0xFFFFFFFF to 0xFF0B57D0),
            "light onSecondary" to (0xFFFFFFFF to 0xFFB3261E),
            "light onTertiary" to (0xFFFFFFFF to 0xFF0A6E4A),
            "light onError" to (0xFFFFFFFF to 0xFF9B1C1C),
            "dark onPrimary" to (0xFF0A2E6E to 0xFFAAC7FF),
            "dark onSecondary" to (0xFF690005 to 0xFFFFB4AB),
            "dark onTertiary" to (0xFF0A2E1C to 0xFF7EE0A6),
            "dark onError" to (0xFF690005 to 0xFFFFB3AC),
        ).forEach { (name, pair) ->
            val (on, fill) = pair
            val ratio = contrast(on, fill)
            assertTrue(ratio >= TEXT, "$name is $ratio, needs $TEXT")
        }
    }

    /** White text on a snackbar background: the old 85% translucent green failed at 3.23:1. */
    @Test
    fun snackbarTextIsReadableOnEverySnackbarBackground() {
        listOf(
            0xFF353A3E, 0xFF4A4F54,
            0xFF17663C, 0xFF1E6B3C,
            0xFF9B1C1C, 0xFFB3261E,
            0xFF15497F, 0xFF2A4A78,
        ).forEach { background ->
            val ratio = contrast(0xFFFFFFFF, background)
            assertTrue(ratio >= TEXT, "white on snackbar ${background.toString(16)} is $ratio")
        }
    }

    /**
     * The subscription accents keep white text readable, including over the translucent overlay the
     * tiles use. This mirrors HomeModelsTest but is asserted here too, because the status chips are
     * chosen against these accents.
     */
    @Test
    fun whiteStaysReadableOnEveryAccentIncludingTheOverlay() {
        val light = listOf(0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00)
        val dark = listOf(0xFF3556D6, 0xFF853197, 0xFF0C6E75, 0xFF1E6B3C, 0xFFA1254C, 0xFF8F5600)
        (light + dark).forEach { accent ->
            val ratio = contrast(0xFFFFFFFF, accent)
            assertTrue(ratio >= TEXT, "white on accent ${accent.toString(16)} is $ratio")
            // Same colour with the 10% white overlay the tiles paint on top.
            fun mix(shift: Int): Long {
                val c = (accent shr shift) and 0xFF
                return Math.round(c + (255 - c) * 0.10)
            }
            val overlaid = (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            val overlayRatio = contrast(0xFFFFFFFF, overlaid)
            assertTrue(overlayRatio >= TEXT, "white on overlaid accent ${accent.toString(16)} is $overlayRatio")
        }
    }

    /**
     * The controls of the neutral page must sit *on* it, not in it. The top-bar tile and the "Add"
     * button used to take `frostPageColors()` and a translucent tonal surface respectively, so on
     * the light theme they were the page's own `background` and the page's own tone: the tiles
     * dissolved and the "Add" button read as a black slab at the top of the screen.
     */
    @Test
    fun neutralTopBarControlsSeparateFromThePage() {
        // Light: surfaceContainerHigh #EEEBED on background #FCF8FA, and the filled primary pair.
        assertTrue(
            contrast(0xFFEEEBED, 0xFFFCF8FA) >= 1.10,
            "light top-bar tile does not step off the page",
        )
        assertTrue(contrast(0xFFFFFFFF, 0xFF0B57D0) >= TEXT, "light onPrimary on primary")
        // Dark: surfaceContainerHigh #282729 on background #141317.
        assertTrue(
            contrast(0xFF282729, 0xFF141317) >= 1.10,
            "dark top-bar tile does not step off the page",
        )
        assertTrue(contrast(0xFF0A2E6E, 0xFFAAC7FF) >= TEXT, "dark onPrimary on primary")
    }

    /**
     * A control on the subscription accent is the translucent white plate: it must stay lighter than
     * every accent it is drawn over, and its white glyph must remain readable on all of them.
     */
    @Test
    fun controlsOnTheAccentPageArePlatesAndNotSlabs() {
        val accents = listOf(
            0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00,
            0xFF3556D6, 0xFF853197, 0xFF0C6E75, 0xFF1E6B3C, 0xFFA1254C, 0xFF8F5600,
        )
        accents.forEach { accent ->
            fun mix(shift: Int): Long {
                val c = (accent shr shift) and 0xFF
                return Math.round(c + (255 - c) * 0.10)
            }
            val plate = (0xFFL shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            // Lighter than the page it sits on, so the plate reads as a control and not a hole.
            assertTrue(
                luminance(plate) > luminance(accent),
                "plate on ${accent.toString(16)} is not lighter than the accent",
            )
            assertTrue(contrast(0xFFFFFFFF, plate) >= TEXT, "glyph on plate over ${accent.toString(16)}")
        }
    }

    /**
     * The round power control: a circle painted with the accent holds a white glyph, and the inverse
     * surface holds the inverse on-colour. Both pairs were failures in the first place — the glyph
     * used to be `colorScheme.primary` on a white plate, i.e. a pale blue on white in the dark theme.
     */
    @Test
    fun powerControlGlyphReadsInEveryState() {
        val accents = listOf(
            0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00,
            0xFF3556D6, 0xFF853197, 0xFF0C6E75, 0xFF1E6B3C, 0xFFA1254C, 0xFF8F5600,
        )
        accents.forEach { accent ->
            assertTrue(contrast(0xFFFFFFFF, accent) >= TEXT, "white glyph on accent ${accent.toString(16)}")
        }
        // Running: inverseSurface holds inverseOnSurface, in both themes.
        assertTrue(contrast(0xFFF4EFF4, 0xFF313033) >= TEXT, "light inverse pair")
        assertTrue(contrast(0xFF1C1B1F, 0xFFE5E1E6) >= TEXT, "dark inverse pair")
    }

    /**
     * The hero card lights up while the VPN is on, so the accent every text and chip on it is
     * measured against is no longer the palette entry: it is that entry lifted towards white by
     * `HOME_CARD_LIT_WHITE`. The lift costs contrast, and at the palette's darkest entries the
     * remaining margin is what decides the value — the worst lit card here is the light palette's
     * violet at about 4.52:1, which is why the lift stays at 0.16 and why this test exists at all.
     */
    @Test
    fun theLitHeroCardKeepsWhiteReadableAtEveryAccent() {
        val accents = listOf(
            0xFF3D57B2, 0xFF734B9D, 0xFF1F676F, 0xFF2C6944, 0xFF93435E, 0xFF825228,
            0xFF3550B8, 0xFF6B4299, 0xFF14606A, 0xFF25603B, 0xFF8C3A55, 0xFF7A4A18,
        )
        accents.forEach { accent ->
            listOf(false, true).forEach { lit ->
                val card = colorOf(
                    com.v2ray.ang.ui.main.homeCardHighlight(androidx.compose.ui.graphics.Color(accent), lit).container,
                )
                val ratio = contrast(0xFFFFFFFF, card)
                assertTrue(ratio >= TEXT, "white on accent ${accent.toString(16)} lit=$lit is $ratio, needs $TEXT")
                // Opaque in both states: the transparent-accent regression is not reopened by the lift.
                assertTrue(card and 0xFF000000L == 0xFF000000L, "card on ${accent.toString(16)} is translucent")
            }
            // Lit is a step *of the same hue*, so it must be visibly lighter rather than a new colour:
            // measured 1.42:1 at the flattest pairing, above the 1.10 step the scheme uses for a
            // tonal separation that carries no information.
            val rest = colorOf(
                com.v2ray.ang.ui.main.homeCardHighlight(androidx.compose.ui.graphics.Color(accent), false).container,
            )
            val lit = colorOf(
                com.v2ray.ang.ui.main.homeCardHighlight(androidx.compose.ui.graphics.Color(accent), true).container,
            )
            assertTrue(luminance(lit) > luminance(rest), "lit card at ${accent.toString(16)} is not lighter")
            assertTrue(contrast(lit, rest) >= 1.30, "lit card at ${accent.toString(16)} is $lit, too near the rest")
        }
    }
}
