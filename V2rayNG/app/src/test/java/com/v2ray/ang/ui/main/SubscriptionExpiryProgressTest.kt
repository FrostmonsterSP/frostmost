package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The card's expiry rail. Every case is expressed in days against the published window, because the
 * function's contract is "share of the last [SUBSCRIPTION_EXPIRY_WINDOW_DAYS] days", never "share of
 * the paid period" — only the end date is published, so the period's start is unknowable.
 */
class SubscriptionExpiryProgressTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun noExpiryDateHasNoBar() {
        assertNull(subscriptionExpiryProgress(expireMillis = null, expired = false, nowMillis = now))
    }

    @Test
    fun expiredDrawsAnEmptyRail() {
        assertEquals(0f, subscriptionExpiryProgress(now - day, expired = true, nowMillis = now)!!, 0.0001f)
        // Flagged expired but with an end date still ahead (clock skew between panel and device):
        // the flag wins, so the rail matches the "expired on …" line shown beside it.
        assertEquals(0f, subscriptionExpiryProgress(now + 10 * day, expired = true, nowMillis = now)!!, 0.0001f)
    }

    @Test
    fun endDateBehindNowIsEmptyEvenWithoutTheFlag() {
        assertEquals(0f, subscriptionExpiryProgress(now - 1, expired = false, nowMillis = now)!!, 0.0001f)
        assertEquals(0f, subscriptionExpiryProgress(now, expired = false, nowMillis = now)!!, 0.0001f)
    }

    @Test
    fun moreThanAWindowAheadIsFull() {
        assertEquals(1f, subscriptionExpiryProgress(now + 31 * day, expired = false, nowMillis = now)!!, 0.0001f)
        assertEquals(1f, subscriptionExpiryProgress(now + 3650 * day, expired = false, nowMillis = now)!!, 0.0001f)
    }

    @Test
    fun exactlyOneWindowAheadIsFull() {
        assertEquals(1f, subscriptionExpiryProgress(now + 30 * day, expired = false, nowMillis = now)!!, 0.0001f)
    }

    @Test
    fun halfTheWindowLeftIsHalf() {
        assertEquals(0.5f, subscriptionExpiryProgress(now + 15 * day, expired = false, nowMillis = now)!!, 0.0001f)
    }

    @Test
    fun aFewDaysLeftIsASmallShare() {
        assertEquals(0.1f, subscriptionExpiryProgress(now + 3 * day, expired = false, nowMillis = now)!!, 0.0001f)
    }

    @Test
    fun fractionNeverLeavesTheUnitRange() {
        val probes = listOf(Long.MIN_VALUE, -day, 0L, 1L, now - day, now, now + 1, now + 15 * day, now + 30 * day, Long.MAX_VALUE)
        probes.forEach { expire ->
            listOf(true, false).forEach { expired ->
                val fraction = subscriptionExpiryProgress(expire, expired, now)
                assertTrue(fraction != null, "a date always yields a rail: $expire/$expired")
                assertTrue(fraction!! in 0f..1f, "fraction $fraction out of range for $expire/$expired")
                assertTrue(!fraction.isNaN(), "fraction is NaN for $expire/$expired")
            }
        }
    }
}
