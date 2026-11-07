package com.v2ray.ang.handler

import com.v2ray.ang.dto.SubscriptionFailure
import com.v2ray.ang.dto.SubscriptionImportResult
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionImportTest {
    @Test
    fun readsSubscriptionItemWithoutNewFields() {
        val item = JsonUtil.fromJson("{\"remarks\":\"a\",\"url\":\"https://x\"}", SubscriptionItem::class.java)!!
        assertNull(item.selectedGuid)
        assertNull(item.usageInfo)
    }

    @Test
    fun titleReplacesOnlyAutomaticName() {
        assertEquals("Наш VPN", resolveImportedRemarks("import sub", "Наш VPN"))
        assertEquals("Моё имя", resolveImportedRemarks("Моё имя", "Наш VPN"))
        assertEquals("import sub", resolveImportedRemarks("import sub", " "))
        assertEquals("import sub", resolveImportedRemarks("import sub", null))
    }

    @Test
    fun existingSubscriptionIsNotImportedTwice() {
        assertEquals(SubscriptionImportResult.AlreadyExists("g"), decideImport("g", null))
    }

    @Test
    fun failureReasonMapsToResult() {
        val download = SubscriptionUpdateResult(failureCount = 1, failure = SubscriptionFailure.DOWNLOAD)
        val empty = SubscriptionUpdateResult(failureCount = 1, failure = SubscriptionFailure.NO_SERVERS)
        assertEquals(SubscriptionImportResult.DownloadFailed, decideImport(null, download, "n"))
        assertEquals(SubscriptionImportResult.NoServers, decideImport(null, empty, "n"))
    }

    @Test
    fun failureWithoutReasonIsDownloadFailure() {
        assertEquals(
            SubscriptionImportResult.DownloadFailed,
            decideImport(null, SubscriptionUpdateResult(failureCount = 1), "n"),
        )
    }

    @Test
    fun successGivesAdded() {
        val ok = SubscriptionUpdateResult(configCount = 3, successCount = 1)
        assertEquals(SubscriptionImportResult.Added("n"), decideImport(null, ok, "n"))
    }

    @Test
    fun combinedResultKeepsFirstFailure() {
        val a = SubscriptionUpdateResult(failureCount = 1, failure = SubscriptionFailure.NO_SERVERS)
        val b = SubscriptionUpdateResult(failureCount = 1, failure = SubscriptionFailure.DOWNLOAD)
        assertEquals(SubscriptionFailure.NO_SERVERS, (a + b).failure)
        assertNull((SubscriptionUpdateResult() + SubscriptionUpdateResult()).failure)
    }
}
