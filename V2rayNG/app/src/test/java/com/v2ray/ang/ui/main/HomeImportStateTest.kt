package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionFailure
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
            importFailureState("u", hasSubscriptions = false, SubscriptionFailure.DOWNLOAD),
        )
        assertEquals(HomeImportState.Idle, importFailureState("u", hasSubscriptions = true, SubscriptionFailure.DOWNLOAD))
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
}
