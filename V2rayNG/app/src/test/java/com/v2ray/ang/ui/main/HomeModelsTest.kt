package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
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
        assertEquals(HomeAction.UPDATE, highlightedAction(card(), HomeIssue.UpdateFailed(1L)))
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
}
