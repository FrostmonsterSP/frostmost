package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager

/** Upper bound of the "fast" latency band, in milliseconds. */
private const val LATENCY_FAST_MAX_MILLIS = 300L

/** Upper bound of the "medium" latency band, in milliseconds. */
private const val LATENCY_MEDIUM_MAX_MILLIS = 800L

/** One server as the home screen shows it, plus the band its measured delay falls into. */
internal data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    val subscriptionBadge: String,
)

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
): ServerRowUiModel {
    val profile = server.profile
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
    )
}

private fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}

/** Neutral latency bands of the server row. */
internal enum class ServerLatencyTone { Unknown, Fast, Medium, Slow }

/**
 * Maps a stored delay to its band. A delay of 0 (or any non-positive value) means the server has
 * not been tested, so it reads as [ServerLatencyTone.Unknown] rather than as the fastest band.
 */
internal fun serverLatencyTone(delayMillis: Long): ServerLatencyTone = when {
    delayMillis <= 0L -> ServerLatencyTone.Unknown
    delayMillis <= LATENCY_FAST_MAX_MILLIS -> ServerLatencyTone.Fast
    delayMillis <= LATENCY_MEDIUM_MAX_MILLIS -> ServerLatencyTone.Medium
    else -> ServerLatencyTone.Slow
}
