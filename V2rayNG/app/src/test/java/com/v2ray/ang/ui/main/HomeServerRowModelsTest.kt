package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test fun latencyToneBoundaries() {
        assertEquals(ServerLatencyTone.Unknown, serverLatencyTone(-1L))
        assertEquals(ServerLatencyTone.Unknown, serverLatencyTone(0L))
        assertEquals(ServerLatencyTone.Fast, serverLatencyTone(1L))
        assertEquals(ServerLatencyTone.Fast, serverLatencyTone(300L))
        assertEquals(ServerLatencyTone.Medium, serverLatencyTone(301L))
        assertEquals(ServerLatencyTone.Medium, serverLatencyTone(800L))
        assertEquals(ServerLatencyTone.Slow, serverLatencyTone(801L))
    }

}
