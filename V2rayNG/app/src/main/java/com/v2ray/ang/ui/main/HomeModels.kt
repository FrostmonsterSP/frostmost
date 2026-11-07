package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem

/** Pure model of the home screen; no Android or Compose dependencies. Identity is by group id / server GUID only. */
internal data class HomeCard(
    val id: String,
    val name: String,
    /** Null for a manual group or a subscription that published no account information. */
    val info: SubscriptionInfoUiModel?,
    val isManual: Boolean,
    val announce: String?,
    /** Index into [HOME_ACCENTS]. */
    val accent: Int,
)

internal enum class HomeAction { UPDATE, CABINET, SUPPORT }

sealed interface HomeIssue {
    data class UpdateFailed(val lastUpdated: Long) : HomeIssue
    data object ServerUnreachable : HomeIssue
    data object Expired : HomeIssue
    data object TrafficExhausted : HomeIssue
    data object VpnPermissionDenied : HomeIssue
}

/** Group ids the pseudo group "All" uses; it never becomes a card. */
private const val ALL_GROUP_ID = ""

internal fun buildHomeCards(
    groups: List<GroupMapItem>,
    urls: Map<String, String>,
    nowMillis: Long,
): List<HomeCard> = groups
    .filter { it.id != ALL_GROUP_ID }
    .map { group ->
        val isManual = urls[group.id].isNullOrEmpty()
        HomeCard(
            id = group.id,
            name = group.remarks,
            info = if (isManual) null else group.usageInfo?.let { buildSubscriptionInfoUiModel(it, nowMillis) },
            isManual = isManual,
            announce = if (isManual) null else group.usageInfo?.announce,
            accent = accentFor(group.id),
        )
    }

/** Actions shown in the action row; absent data hides the action. A manual group has no actions. */
internal fun homeActions(card: HomeCard): List<HomeAction> {
    if (card.isManual) return emptyList()
    return buildList {
        add(HomeAction.UPDATE)
        if (card.info?.webPageUrl != null) add(HomeAction.CABINET)
        if (card.info?.supportUrl != null) add(HomeAction.SUPPORT)
    }
}

/** Transient issue wins; otherwise Expired (traffic does not help once expired), then TrafficExhausted. */
internal fun resolveHomeIssue(card: HomeCard, transient: HomeIssue?): HomeIssue? {
    if (transient != null) return transient
    val info = card.info ?: return null
    return when {
        info.expired -> HomeIssue.Expired
        info.trafficFraction != null && info.trafficFraction >= 1f -> HomeIssue.TrafficExhausted
        else -> null
    }
}

/** Cabinet, else support, else nothing for account issues; unreachable server and VPN permission have their own strip action. */
internal fun highlightedAction(card: HomeCard, issue: HomeIssue?): HomeAction? = when (issue) {
    is HomeIssue.UpdateFailed -> HomeAction.UPDATE
    HomeIssue.Expired, HomeIssue.TrafficExhausted -> when {
        card.info?.webPageUrl != null -> HomeAction.CABINET
        card.info?.supportUrl != null -> HomeAction.SUPPORT
        else -> null
    }
    HomeIssue.ServerUnreachable, HomeIssue.VpnPermissionDenied, null -> null
}

/** Remembered server if it still exists in the group, else the group's first server, else null. */
internal fun resolveServerForSubscription(remembered: String?, groupGuids: List<String>): String? =
    remembered?.takeIf { it in groupGuids } ?: groupGuids.firstOrNull()

/** First run only when no subscription exists and there is no server to connect to. */
internal fun shouldShowFirstRun(cards: List<HomeCard>, serverCount: Int): Boolean =
    serverCount == 0 && cards.none { !it.isManual }

internal val HOME_ACCENTS: List<Long> = listOf(
    0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00,
)

internal fun accentFor(id: String): Int = Math.floorMod(id.hashCode(), HOME_ACCENTS.size)
