package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeServerRowModelsTest {

    private fun row(
        typeDescription: String = "VMess / ws / tls",
        server: String? = "1.2.3.4",
        serverPort: String? = "443",
    ) = ServerRowUiModel(
        guid = "g",
        profile = ProfileItem(configType = EConfigType.VMESS).apply {
            this.server = server
            this.serverPort = serverPort
        },
        remarks = "r",
        statistics = "s",
        typeDescription = typeDescription,
        testDelayMillis = 0L,
        subscriptionBadge = "",
    )

    @Test fun detailLineJoinsProtocolAndAddress() {
        assertEquals("VMess / ws / tls · 1.2.3.4:443", serverRowDetailLine(row()))
    }

    @Test fun detailLineKeepsWhateverIsPresent() {
        // Network and security blank in the row's description: only the protocol remains.
        assertEquals("VMess · 1.2.3.4:443", serverRowDetailLine(row(typeDescription = "VMess")))
        // No address: the description carries the line alone.
        assertEquals("VMess", serverRowDetailLine(row(typeDescription = "VMess", server = null, serverPort = null)))
        // Partial address: keep the part that is known, without a dangling port separator.
        assertEquals("a.example", serverRowDetailLine(row(typeDescription = "", server = "a.example", serverPort = null)))
        assertEquals("", serverRowDetailLine(row(typeDescription = "", server = "", serverPort = "443")))
        // A port without an address is not a location, so it is dropped, not shown as ":443".
        assertEquals("VMess / ws / tls", serverRowDetailLine(row(server = "", serverPort = "443")))
    }

    @Test fun detailLineIsEmptyWhenEverythingIsBlank() {
        assertEquals("", serverRowDetailLine(row(typeDescription = "", server = null, serverPort = null)))
        assertEquals("", serverRowDetailLine(row(typeDescription = "  ", server = " ", serverPort = " ")))
    }

    @Test fun latencyToneBoundaries() {
        assertEquals(ServerLatencyTone.Unknown, serverLatencyTone(-1L))
        assertEquals(ServerLatencyTone.Unknown, serverLatencyTone(0L))
        assertEquals(ServerLatencyTone.Fast, serverLatencyTone(1L))
        assertEquals(ServerLatencyTone.Fast, serverLatencyTone(300L))
        assertEquals(ServerLatencyTone.Medium, serverLatencyTone(301L))
        assertEquals(ServerLatencyTone.Medium, serverLatencyTone(800L))
        assertEquals(ServerLatencyTone.Slow, serverLatencyTone(801L))
    }

    @Test fun latencyIsTintedOnlyOnTheSelectedRow() {
        assertTrue(shouldTintLatency(true))
        assertFalse(shouldTintLatency(false))
    }
}
