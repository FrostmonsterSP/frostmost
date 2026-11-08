package com.v2ray.ang.ui.main

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
        assertEquals(accentFor("s"), cards[1].accent)
    }

    @Test fun accentsAreStableAndReadableWithWhite() {
        assertEquals(accentFor("x"), accentFor("x"))
        assertTrue(accentFor("anything") in HOME_ACCENTS.indices)
        HOME_ACCENTS.forEach { assertTrue(contrastWithWhite(it) >= 4.5) }
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
        // A stored accent stays valid when a palette later shrinks, and the hash stays in range.
        assertEquals(HOME_ACCENTS[0], accentColor(HOME_ACCENTS.size, dark = false))
        assertEquals(HOME_ACCENTS_DARK[0], accentColor(-HOME_ACCENTS_DARK.size, dark = true))
        assertTrue(accentFor("any-group") in HOME_ACCENTS.indices)
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
}
