package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionFailure
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeImportStateTest {
    @Test fun firstSubscriptionSkipsConfirmation() {
        assertEquals(HomeImportState.Downloading("u"), nextImportState("u", hasSubscriptions = false, title = "T"))
    }

    @Test fun laterLinkAsksWithTitleOrHost() {
        assertEquals(HomeImportState.Confirm("https://a.b/s", "T"), nextImportState("https://a.b/s", true, "T"))
        assertEquals(HomeImportState.Confirm("https://a.b/s", "a.b"), nextImportState("https://a.b/s", true, null))
    }

    @Test fun blankTitleFallsBackToHost() {
        assertEquals(HomeImportState.Confirm("https://a.b/s", "a.b"), nextImportState("https://a.b/s", true, "  "))
    }

    @Test fun hostFallbackNeverEmptyAndNeverShowsPathOrQuery() {
        assertEquals("a.b", linkDisplayHost("https://a.b:8443/s?token=secret"))
        assertEquals("…", linkDisplayHost("not a url"))
    }

    @Test fun failureStaysOnScreenOnlyOnFirstRun() {
        assertEquals(
            HomeImportState.Failed("u", SubscriptionFailure.DOWNLOAD),
            importFailureState("u", firstRun = true, SubscriptionFailure.DOWNLOAD),
        )
        assertEquals(HomeImportState.Idle, importFailureState("u", firstRun = false, SubscriptionFailure.DOWNLOAD))
    }

    @Test fun deepLinkExtraKeepsValidLinkAndMarksInvalidAsEmpty() {
        assertEquals("https://a.b/s", deepLinkExtra("install-sub", " https://a.b/s "))
        assertEquals("vless://x@y:1#n", deepLinkExtra("install-config", "vless://x@y:1#n"))
        assertEquals("", deepLinkExtra("install-sub", "javascript:alert(1)"))
        assertEquals("", deepLinkExtra("install-sub", "vless://x@y:1"))
        assertEquals("", deepLinkExtra("other", "https://a.b"))
        assertEquals("", deepLinkExtra("install-sub", null))
    }

    @Test fun deepLinkFragmentNamesTheLinkOnlyWhenTheAddressHasNone() {
        assertEquals("https://a.b/s#Name", deepLinkExtra("install-sub", "https://a.b/s", "Name"))
        assertEquals("https://a.b/s#own", deepLinkExtra("install-sub", "https://a.b/s#own", "Name"))
        assertEquals("", deepLinkExtra("install-sub", "javascript:x", "Name"))
    }

    @Test fun firstRunNeedsNoSubscriptionAndNoServers() {
        assertEquals(true, isFirstRun(hasSubscriptions = false, serverCount = 0))
        assertEquals(false, isFirstRun(hasSubscriptions = false, serverCount = 3))
        assertEquals(false, isFirstRun(hasSubscriptions = true, serverCount = 0))
    }

    @Test fun repeatedLinkResolvesToTheStoredSubscription() {
        fun sub(guid: String, url: String) = SubscriptionCache(guid, SubscriptionItem().apply { this.url = url })
        val stored = listOf(sub("", "https://a.b/s"), sub("g1", ""), sub("g2", "https://a.b/s"))
        assertEquals("g2", existingSubscriptionGuid(stored, "https://a.b/s"))
        assertEquals(null, existingSubscriptionGuid(stored, "https://other/s"))
    }

    @Test fun sharedTextIsPassedThroughTrimmedOrBlank() {
        assertEquals("{\"a\":1}", sharedTextExtra("  {\"a\":1}\n"))
        assertEquals("see https://a.b/s", sharedTextExtra("see https://a.b/s"))
        assertEquals("", sharedTextExtra("   "))
        assertEquals("", sharedTextExtra(null))
    }
}
