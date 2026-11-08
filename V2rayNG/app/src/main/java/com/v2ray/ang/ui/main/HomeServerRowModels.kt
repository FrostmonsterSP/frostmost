package com.v2ray.ang.ui.main

/** Joins the parts of the compact row's single secondary line. */
private const val DETAIL_SEPARATOR = " · "

/** Upper bound of the "fast" latency band, in milliseconds. */
private const val LATENCY_FAST_MAX_MILLIS = 300L

/** Upper bound of the "medium" latency band, in milliseconds. */
private const val LATENCY_MEDIUM_MAX_MILLIS = 800L

/**
 * The one technical line under a server name: protocol/transport description first, then the
 * address:port, so the row stays calm instead of repeating every field on its own line.
 */
internal fun serverRowDetailLine(row: ServerRowUiModel): String {
    val address = serverAddressLabel(row.profile.server, row.profile.serverPort)
    return listOf(row.typeDescription, address)
        .filter { it.isNotBlank() }
        .joinToString(DETAIL_SEPARATOR)
}

/** Neutral latency bands; the tone only matters when [shouldTintLatency] says so. */
internal enum class ServerLatencyTone { Unknown, Fast, Medium, Slow }

/**
 * Maps a stored delay to its band. A delay of 0 (or any non-positive value) means the server
 * has not been tested, so it reads as [ServerLatencyTone.Unknown] rather than as the fastest band.
 */
internal fun serverLatencyTone(delayMillis: Long): ServerLatencyTone = when {
    delayMillis <= 0L -> ServerLatencyTone.Unknown
    delayMillis <= LATENCY_FAST_MAX_MILLIS -> ServerLatencyTone.Fast
    delayMillis <= LATENCY_MEDIUM_MAX_MILLIS -> ServerLatencyTone.Medium
    else -> ServerLatencyTone.Slow
}

/** Latency is neutral everywhere except on the selected server's row. */
internal fun shouldTintLatency(isSelected: Boolean): Boolean = isSelected

/**
 * `address:port` for the detail line. A known address alone is still a location and stays; a port
 * without an address is not, so it is dropped rather than shown as a dangling ":443".
 */
private fun serverAddressLabel(server: String?, serverPort: String?): String {
    val address = server.orEmpty().trim()
    if (address.isEmpty()) return ""
    val port = serverPort.orEmpty().trim()
    return if (port.isEmpty()) address else "$address:$port"
}
