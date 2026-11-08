package com.v2ray.ang.ui.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

class HomeModelsTest {

    private fun info(
        web: String? = null,
        support: String? = null,
        expired: Boolean = false,
        exhausted: Boolean = false,
    ) = SubscriptionInfoUiModel(
        title = null,
        usedBytes = if (exhausted) 100 else 0,
        totalBytes = 100,
        trafficFraction = if (exhausted) 1f else 0f,
        expireMillis = if (expired) 1L else null,
        expired = expired,
        supportUrl = support,
        webPageUrl = web,
    )

    private fun card(
        web: String? = null,
        support: String? = null,
        isManual: Boolean = false,
        expired: Boolean = false,
        exhausted: Boolean = false,
    ) = HomeCard(
        id = "g",
        name = "g",
        info = if (isManual) null else info(web, support, expired, exhausted),
        isManual = isManual,
        announce = null,
        accent = 0,
    )

    private fun expired() = card(expired = true)

    private fun contrastWithWhite(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val l = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        return 1.05 / (l + 0.05)
    }

    /** WCAG 2.1 contrast of two fully opaque colours; the threshold is never rounded up. */
    private fun contrast(a: Long, b: Long): Double {
        fun luminance(argb: Long): Double {
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test fun hidesActionsWithoutData() {
        assertEquals(listOf(HomeAction.UPDATE, HomeAction.SUPPORT), homeActions(card(support = "s")))
        assertEquals(
            listOf(HomeAction.UPDATE, HomeAction.CABINET, HomeAction.SUPPORT),
            homeActions(card(web = "w", support = "s")),
        )
        assertEquals(emptyList<HomeAction>(), homeActions(card(isManual = true)))
    }

    @Test fun issuePriorityAndHighlightFallback() {
        assertEquals(HomeIssue.ServerUnreachable, resolveHomeIssue(expired(), HomeIssue.ServerUnreachable))
        assertEquals(HomeIssue.Expired, resolveHomeIssue(expired(), null))
        assertEquals(HomeIssue.Expired, resolveHomeIssue(card(expired = true, exhausted = true), null))
        assertEquals(HomeIssue.TrafficExhausted, resolveHomeIssue(card(exhausted = true), null))
        assertNull(resolveHomeIssue(card(), null))
        assertNull(resolveHomeIssue(card(isManual = true), null))
        assertEquals(HomeAction.CABINET, highlightedAction(card(web = "w", support = "s"), HomeIssue.Expired))
        assertEquals(HomeAction.SUPPORT, highlightedAction(card(support = "s"), HomeIssue.Expired))
        assertNull(highlightedAction(card(), HomeIssue.Expired))
        assertEquals(HomeAction.UPDATE, highlightedAction(card(), HomeIssue.UpdateFailed("g", 1L)))
        assertNull(highlightedAction(card(web = "w"), HomeIssue.ServerUnreachable))
        assertNull(highlightedAction(card(web = "w"), HomeIssue.VpnPermissionDenied))
        assertNull(highlightedAction(card(), null))
    }

    @Test fun rememberedServerFallsBackToFirst() {
        assertEquals("b", resolveServerForSubscription("b", listOf("a", "b")))
        assertEquals("a", resolveServerForSubscription("gone", listOf("a", "b")))
        assertEquals("a", resolveServerForSubscription(null, listOf("a", "b")))
        assertNull(resolveServerForSubscription("gone", emptyList()))
    }

    @Test fun firstRunOnlyWhenNothingToConnect() {
        assertTrue(shouldShowFirstRun(emptyList(), 0))
        assertTrue(shouldShowFirstRun(listOf(card(isManual = true)), 0))
        assertFalse(shouldShowFirstRun(listOf(card(isManual = true)), 1))
        assertFalse(shouldShowFirstRun(listOf(card()), 0))
    }

    @Test fun deletedLastSubscriptionReturnsToFirstRunWithoutCrash() {
        val before = buildHomeCards(listOf(GroupMapItem("s1", "S")), mapOf("s1" to "https://x"), 0L)
        assertFalse(shouldShowFirstRun(before, 0))
        val after = buildHomeCards(emptyList(), emptyMap(), 0L)
        assertTrue(after.isEmpty())
        assertTrue(shouldShowFirstRun(after, 0))
    }

    @Test fun buildCardsSkipsAllGroupAndMarksManual() {
        val usage = SubscriptionUsageInfo(total = 100, expire = 1, webPageUrl = "w", announce = "hi")
        val cards = buildHomeCards(
            listOf(GroupMapItem("", "All"), GroupMapItem("m", "Manual"), GroupMapItem("s", "Sub", usage)),
            mapOf("m" to "", "s" to "https://x"),
            nowMillis = 5_000L,
        )
        assertEquals(listOf("m", "s"), cards.map { it.id })
        assertTrue(cards[0].isManual)
        assertNull(cards[0].info)
        assertFalse(cards[1].isManual)
        assertEquals("hi", cards[1].announce)
        assertTrue(cards[1].info!!.expired)
        // The accent follows the card's position among the real groups, not the group id.
        assertEquals(listOf(0, 1), cards.map { it.accent })
    }

    // ---------- Accent assignment ----------

    private fun group(id: String) = GroupMapItem(id, id)

    @Test fun firstOrOnlySubscriptionAlwaysGetsAccentZero() {
        assertEquals(0, accentFor(0))
        val only = buildHomeCards(listOf(group("s")), mapOf("s" to "https://x"), 0L)
        assertEquals(1, only.size)
        assertEquals(0, only[0].accent)
        // Ids that used to hash into the wine tone no longer decide the default.
        listOf("s", "a", "test", "sub", "anything").forEach { id ->
            val cards = buildHomeCards(listOf(group(id)), emptyMap(), 0L)
            assertEquals(0, cards[0].accent, "single subscription $id must be accent 0")
        }
        // The pseudo group "All" is not a card, so the first real group is still position 0.
        val withAll = buildHomeCards(listOf(group(""), group("s")), emptyMap(), 0L)
        assertEquals(listOf(0), withAll.map { it.accent })
    }

    @Test fun accentFollowsGroupOrderAndNotTheSelectedCard() {
        val groups = listOf(group("a"), group("b"), group("c"))
        val forward = buildHomeCards(groups, emptyMap(), 0L)
        assertEquals(listOf(0, 1, 2), forward.map { it.accent })
        // Same groups, same answer: no hash, no randomness, no dependence on call history.
        assertEquals(forward, buildHomeCards(groups, emptyMap(), 0L))
        // A different build of the very same list of groups yields the same accents.
        assertEquals(
            forward.map { it.id to it.accent },
            buildHomeCards(groups.toList(), emptyMap(), 0L).map { it.id to it.accent },
        )
    }

    @Test fun moreSubscriptionsThanColoursWrapAroundInsideThePalette() {
        val ids = (0 until HOME_ACCENTS.size * 3).map { "g$it" }
        val cards = buildHomeCards(ids.map(::group), emptyMap(), 0L)
        assertEquals(ids, cards.map { it.id })
        // Every card stays inside the palette and the sequence repeats by position.
        cards.forEachIndexed { index, card ->
            assertEquals(Math.floorMod(index, HOME_ACCENTS.size), card.accent, "card $index")
            assertTrue(card.accent in HOME_ACCENTS.indices)
            assertTrue(card.accent in HOME_ACCENTS_DARK.indices)
        }
    }

    @Test fun accentForWrapsAndTakesNegativePositions() {
        assertEquals(0, accentFor(0))
        assertEquals(1, accentFor(1))
        assertEquals(HOME_ACCENTS.size - 1, accentFor(HOME_ACCENTS.size - 1))
        assertEquals(0, accentFor(HOME_ACCENTS.size))
        assertEquals(1, accentFor(HOME_ACCENTS.size + 1))
        // Defensive: an index is never allowed to escape the palette bounds.
        assertTrue(accentFor(-1) in HOME_ACCENTS.indices)
        assertTrue(accentFor(HOME_ACCENTS.size * -7) in HOME_ACCENTS.indices)
    }

    @Test fun removingASubscriptionDoesNotRepaintTheOnesAfterIt() {
        val a = group("a")
        val b = group("b")
        val c = group("c")
        val before = buildHomeCards(listOf(a, b, c), emptyMap(), 0L)
        // Dropping the first group shifts the survivors, but each survivor keeps a valid accent and
        // the list order still decides it — never the id.
        val after = buildHomeCards(listOf(b, c), emptyMap(), 0L)
        assertEquals(listOf(0, 1), after.map { it.accent })
        assertEquals(before.drop(1).map { it.id }, after.map { it.id })
    }

    @Test fun accentsAreStableAndReadableWithWhite() {
        // The palette itself: every accent of both themes keeps white text readable.
        HOME_ACCENTS.forEach { assertTrue(contrastWithWhite(it) >= 4.5) }
        HOME_ACCENTS_DARK.forEach { assertTrue(contrastWithWhite(it) >= 4.5) }
    }

    // ---------- Home screen helpers ----------

    @Test fun visibleHighlightDropsActionsTheRowDoesNotShow() {
        val manual = card(isManual = true)
        assertEquals(HomeAction.UPDATE, highlightedAction(manual, HomeIssue.UpdateFailed("g", 5L)))
        assertNull(visibleHighlight(manual, HomeIssue.UpdateFailed("g", 5L)))
        assertEquals(HomeAction.UPDATE, visibleHighlight(card(), HomeIssue.UpdateFailed("g", 5L)))
        assertEquals(HomeAction.CABINET, visibleHighlight(card(web = "w", expired = true), HomeIssue.Expired))
        assertEquals(HomeAction.SUPPORT, visibleHighlight(card(support = "s", exhausted = true), HomeIssue.TrafficExhausted))
        assertNull(visibleHighlight(card(), HomeIssue.Expired))
        assertNull(visibleHighlight(card(web = "w"), null))
        assertNull(visibleHighlight(card(web = "w"), HomeIssue.ServerUnreachable))
    }

    @Test fun homeStatusOrder() {
        assertEquals(HomeStatus.DISCONNECTED, homeStatus(false, false, false, null))
        assertEquals(HomeStatus.CONNECTED, homeStatus(true, false, false, null))
        assertEquals(HomeStatus.CONNECTING, homeStatus(false, true, false, null))
        assertEquals(HomeStatus.CONNECTING, homeStatus(true, true, false, null))
        assertEquals(HomeStatus.SWITCHING, homeStatus(true, true, true, null))
        assertEquals(HomeStatus.SWITCHING, homeStatus(true, false, true, null))
        assertEquals(HomeStatus.FAILED, homeStatus(false, false, false, HomeIssue.ServerUnreachable))
        // Spec «Ошибки»: an unreachable server reads «Не удалось подключиться» also while the VPN stays on.
        assertEquals(HomeStatus.FAILED, homeStatus(true, false, false, HomeIssue.ServerUnreachable))
        assertEquals(HomeStatus.CONNECTING, homeStatus(true, true, false, HomeIssue.ServerUnreachable))
        assertEquals(HomeStatus.SWITCHING, homeStatus(true, false, true, HomeIssue.ServerUnreachable))
        assertEquals(HomeStatus.CONNECTED, homeStatus(true, false, false, HomeIssue.UpdateFailed("g", 1L)))
        assertEquals(HomeStatus.DISCONNECTED, homeStatus(false, false, false, HomeIssue.VpnPermissionDenied))
    }

    @Test fun displayedCardFallsBackWhenSelectionIsNotACard() {
        val cards = listOf("a", "b").map { card().copy(id = it) }
        assertEquals("b", displayedCardId(cards, "b"))
        assertEquals("a", displayedCardId(cards, ""))
        assertEquals("a", displayedCardId(cards, "removed"))
        assertNull(displayedCardId(emptyList(), "a"))
    }

    @Test fun subscriptionUrlsComeFromTheSameLoad() {
        val subs = listOf(
            SubscriptionCache("s", SubscriptionItem(url = "https://x")),
            SubscriptionCache("m", SubscriptionItem()),
        )
        val urls = subscriptionUrls(subs)
        assertEquals(mapOf("s" to "https://x", "m" to ""), urls)
        val groups = listOf(GroupMapItem("s", "S"), GroupMapItem("m", "M"))
        val cards = buildHomeCards(groups, urls, 0L)
        assertFalse(cards.first { it.id == "s" }.isManual)
        assertTrue(cards.first { it.id == "m" }.isManual)
        assertTrue(subscriptionUrls(emptyList()).isEmpty())
    }

    @Test fun updateFailedDateHidesUnknown() {
        assertEquals(1234L, updateFailedDate(HomeIssue.UpdateFailed("g", 1234L)))
        assertNull(updateFailedDate(HomeIssue.UpdateFailed("g", -1L)))
        assertNull(updateFailedDate(HomeIssue.UpdateFailed("g", 0L)))
    }

    @Test fun whiteTextReadableOnOverlayOverEveryAccent() {
        (HOME_ACCENTS + HOME_ACCENTS_DARK).forEach { accent ->
            fun mix(shift: Int): Long {
                val c = (accent shr shift) and 0xFF
                return Math.round(c + (255 - c) * HOME_ON_ACCENT_OVERLAY.toDouble())
            }
            val blended = 0xFF000000 or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            assertTrue(contrastWithWhite(blended) >= 4.5, "accent ${accent.toString(16)}")
        }
    }

    @Test fun bothPalettesKeepWhiteReadable() {
        HOME_ACCENTS.forEach { assertTrue(contrastWithWhite(it) >= 4.5, "light accent ${it.toString(16)}") }
        HOME_ACCENTS_DARK.forEach { assertTrue(contrastWithWhite(it) >= 4.5, "dark accent ${it.toString(16)}") }
        assertEquals(HOME_ACCENTS.size, HOME_ACCENTS_DARK.size)
    }

    @Test fun accentColorPicksThePaletteOfTheTheme() {
        // Both palettes are checked index by index, so a swapped or missing entry is named.
        for (accent in HOME_ACCENTS.indices) {
            assertEquals(HOME_ACCENTS[accent], accentColor(accent, dark = false), "light accent $accent")
            assertEquals(HOME_ACCENTS_DARK[accent], accentColor(accent, dark = true), "dark accent $accent")
        }
        // A stored accent stays valid when a palette later shrinks.
        assertEquals(HOME_ACCENTS[0], accentColor(HOME_ACCENTS.size, dark = false))
        assertEquals(HOME_ACCENTS_DARK[0], accentColor(-HOME_ACCENTS_DARK.size, dark = true))
        // A position (what buildHomeCards stores now) also stays in range for any group count.
        (0 until HOME_ACCENTS.size * 2).forEach { assertTrue(accentFor(it) in HOME_ACCENTS.indices) }
    }

    @Test fun failedUpdateShowsOnlyOnItsOwnCard() {
        val a = card().copy(id = "a")
        val bExpired = card(expired = true).copy(id = "b")
        val failedA = HomeIssue.UpdateFailed("a", 5L)
        assertEquals(failedA, resolveHomeIssue(a, failedA))
        // B keeps its own account issue and is not highlighted for A's failure.
        assertEquals(HomeIssue.Expired, resolveHomeIssue(bExpired, failedA))
        assertNull(resolveHomeIssue(card().copy(id = "b"), failedA))
        assertNull(visibleHighlight(card().copy(id = "b"), resolveHomeIssue(card().copy(id = "b"), failedA)))
        // Connection issues stay global.
        assertEquals(HomeIssue.ServerUnreachable, resolveHomeIssue(bExpired, HomeIssue.ServerUnreachable))
        assertEquals(HomeIssue.VpnPermissionDenied, resolveHomeIssue(bExpired, HomeIssue.VpnPermissionDenied))
    }

    @Test fun homeGroupResolvesAllAndMissingToARealGroup() {
        val ids = listOf("", "a", "b")
        assertEquals("b", resolveHomeGroup(ids, "b", null))
        assertEquals("b", resolveHomeGroup(ids, "b", "a"))
        assertEquals("b", resolveHomeGroup(ids, "", "b"))
        assertEquals("a", resolveHomeGroup(ids, "", null))
        assertEquals("a", resolveHomeGroup(ids, "", "gone"))
        assertEquals("b", resolveHomeGroup(ids, "removed", "b"))
        assertEquals("a", resolveHomeGroup(ids, "removed", null))
        assertEquals("", resolveHomeGroup(listOf(""), "", null))
        assertEquals("", resolveHomeGroup(emptyList(), "x", "x"))
    }

    @Test fun moreKeepsEverythingExceptPromotion() {
        assertEquals(MainDestination.entries.toSet() - MainDestination.Promotion, HOME_MORE_ITEMS.toSet())
    }

    // ---------- Accent page: the whole screen while the VPN is on ----------

    /** Compose `Color` back to packed ARGB; the accent tokens are not framework objects. */
    private fun argbOf(color: androidx.compose.ui.graphics.Color): Long =
        color.toArgb().toLong() and 0xFFFFFFFFL

    /**
     * The pixel a partly transparent white text lands on when it is drawn over [background]: the
     * foreground and its background are mixed, because WCAG measures composited pixels.
     */
    private fun whiteOn(alpha: Float, background: Long): Long {
        fun channel(shift: Int): Long {
            val bg = ((background shr shift) and 0xFF).toFloat()
            return Math.round(255f * alpha + bg * (1 - alpha)).toLong()
        }
        return 0xFF000000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    @Test fun accentPageIsOffUnlessTheVpnRuns() {
        val off = homeAccentPage(Color(HOME_ACCENTS[0]), connected = false)
        assertFalse(off.onAccent)
        // Nothing is carried while it is off: a block cannot accidentally paint the accent.
        assertNull(off.accent)
        assertNull(off.content)
        assertNull(off.muted)

        val on = homeAccentPage(Color(HOME_ACCENTS[0]), connected = true)
        assertTrue(on.onAccent)
        assertEquals(HOME_ACCENTS[0], argbOf(on.accent!!))
        assertEquals(0xFFFFFFFFL, argbOf(on.content!!))
    }

    @Test fun recognizedAccentPageIsFullyOpaque() {
        // The regression this pins: the screen used to pass `accent.value.toLong()` (the colour's
        // float bits) and rebuild the colour from it. The high byte then came out 0x4F instead of
        // 0xFF, so the whole page was a ~31% wash over the neutral background and a connected VPN
        // visibly changed nothing. Every palette entry was affected, and only the alpha reveals it:
        // the RGB channels can still round-trip closely enough to look right in a unit test.
        (HOME_ACCENTS + HOME_ACCENTS_DARK).forEach { accent ->
            val page = homeAccentPage(Color(accent), connected = true)
            assertEquals(
                1f,
                page.accent!!.alpha,
                "page accent ${accent.toString(16)} must be fully opaque",
            )
            assertEquals(accent, argbOf(page.accent!!), "page accent ${accent.toString(16)} keeps its rgb")
            // The same trap, spelled out: the float-bit round trip is what broke it.
            assertTrue(
                Color(Color(accent).value.toLong()).alpha < 1f,
                "if this ever becomes 1f the trap has changed shape and the test needs rewriting",
            )
        }
    }

    @Test fun accentPageUsesTheOnAccentPairInBothPalettes() {
        // Both palettes, every entry: the pair is white and white-at-0.88 regardless of the accent.
        (HOME_ACCENTS + HOME_ACCENTS_DARK).forEach { accent ->
            val page = homeAccentPage(Color(accent), connected = true)
            assertEquals(0xFFFFFFFFL, argbOf(page.content!!), "content for ${accent.toString(16)}")
            assertEquals(1f, page.content!!.alpha, "content alpha for ${accent.toString(16)}")
            assertEquals(ACCENT_ON_PAGE_MUTED.alpha, page.muted!!.alpha, "muted alpha for ${accent.toString(16)}")
        }
    }

    @Test fun onAccentMutedIsDarkerThanTheNeutralMutedAlpha() {
        // 0.72 is measured against neutral surfaces; on the accents it falls to 3.92:1 (teal, dark),
        // which is why the accent pair needs its own larger value. Pinned so a "unify the two"
        // cleanup cannot pass silently.
        assertTrue(ACCENT_ON_PAGE_MUTED.alpha > com.v2ray.ang.ui.compose.FROST_MUTED_ALPHA)
    }

    @Test fun everyTextOnTheAccentedPageStaysReadable() {
        val mutedAlpha = ACCENT_ON_PAGE_MUTED.alpha
        (HOME_ACCENTS + HOME_ACCENTS_DARK).forEach { accent ->
            val page = homeAccentPage(Color(accent), connected = true)
            val background = argbOf(page.accent!!)
            // The page itself: on-accent and on-accent-muted, both measured against the accent.
            assertTrue(
                contrastWithWhite(background) >= 4.5,
                "on-accent on ${accent.toString(16)} is ${contrastWithWhite(background)}",
            )
            val muted = whiteOn(mutedAlpha, background)
            val mutedRatio = contrast(background, muted)
            assertTrue(
                mutedRatio >= 4.5,
                "on-accent-muted on ${accent.toString(16)} is $mutedRatio",
            )
            // A tile below the card: the 10% white overlay, with the same two foregrounds on it.
            fun mix(shift: Int): Long {
                val c = (background shr shift) and 0xFF
                return Math.round(c + (255 - c) * HOME_ON_ACCENT_OVERLAY.toDouble())
            }
            val plate = 0xFF000000L or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            assertTrue(
                contrastWithWhite(plate) >= 4.5,
                "on-accent on the overlay over ${accent.toString(16)} is ${contrastWithWhite(plate)}",
            )
            val mutedOnPlate = contrast(plate, whiteOn(mutedAlpha, plate))
            assertTrue(
                mutedOnPlate >= 4.5,
                "on-accent-muted on the overlay over ${accent.toString(16)} is $mutedOnPlate",
            )
        }
    }

    @Test fun neutralSecondaryAlphaIsNotReusedOnTheAccentPage() {
        // SECONDARY_ALPHA (0.72) is measured against the neutral page: on the dark teal accent it
        // reaches only 3.92:1. The accented top bar therefore takes ACCENT_ON_PAGE_MUTED, and this
        // test fails if someone "simplifies" the two back into one alpha.
        val worstWithNeutralAlpha = (HOME_ACCENTS + HOME_ACCENTS_DARK)
            .minOf { accent -> contrast(accent, whiteOn(SECONDARY_ALPHA, accent)) }
        assertTrue(
            worstWithNeutralAlpha < 4.5,
            "SECONDARY_ALPHA unexpectedly holds on every accent ($worstWithNeutralAlpha)",
        )
        (HOME_ACCENTS + HOME_ACCENTS_DARK).forEach { accent ->
            val ratio = contrast(accent, whiteOn(ACCENT_ON_PAGE_MUTED.alpha, accent))
            assertTrue(ratio >= 4.5, "accent-pair line on ${accent.toString(16)} is $ratio")
        }
    }

    @Test fun heroSwitchStaysOnThroughAStartAndASwitch() {
        assertFalse(heroSwitchOn(isRunning = false, switching = false))
        assertTrue(heroSwitchOn(isRunning = true, switching = false))
        // A switch keeps the VPN up, so the card must not flicker to "off" mid-gesture.
        assertTrue(heroSwitchOn(isRunning = true, switching = true))
        assertTrue(heroSwitchOn(isRunning = false, switching = true))
    }

    @Test fun repeatStartIsRefusedWhileTheFirstIsInFlight() {
        // Idle and off: the tap may start.
        assertTrue(canStartFromOff(isRunning = false, connecting = false, powerEnabled = true))
        // The screen reports the start in flight through both flags; a second tap must not queue.
        assertFalse(canStartFromOff(isRunning = false, connecting = true, powerEnabled = false))
        // One flag alone is enough to refuse: the guard is not a single-source accident.
        assertFalse(canStartFromOff(isRunning = false, connecting = true, powerEnabled = true))
        assertFalse(canStartFromOff(isRunning = false, connecting = false, powerEnabled = false))
    }

    @Test fun runningConnectionCanAlwaysBeStopped() {
        // Stopping is never a queued action, so nothing refuses it; the hero must stay reachable.
        assertTrue(canStartFromOff(isRunning = true, connecting = false, powerEnabled = true))
    }
}
