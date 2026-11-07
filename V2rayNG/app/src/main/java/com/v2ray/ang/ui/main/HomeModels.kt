package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.SubscriptionCache

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
    /** Update of group [groupId] failed; shown only on that group's card. [lastUpdated] is -1 when unknown. */
    data class UpdateFailed(val groupId: String, val lastUpdated: Long) : HomeIssue
    data object ServerUnreachable : HomeIssue
    data object Expired : HomeIssue
    data object TrafficExhausted : HomeIssue
    data object VpnPermissionDenied : HomeIssue
}

/** Rare destinations reachable from "More"; Promotion is removed in the fork. */
internal val HOME_MORE_ITEMS: List<MainDestination> = MainDestination.entries - MainDestination.Promotion

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

/**
 * Transient issue wins; otherwise Expired (traffic does not help once expired), then TrafficExhausted.
 * A failed update belongs to its group: on another card it is ignored. Connection issues are global.
 */
internal fun resolveHomeIssue(card: HomeCard, transient: HomeIssue?): HomeIssue? {
    if (transient != null && !(transient is HomeIssue.UpdateFailed && transient.groupId != card.id)) return transient
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

/** Highlight only an action the row actually shows: [highlightedAction] names UPDATE even for a manual card. */
internal fun visibleHighlight(card: HomeCard, issue: HomeIssue?): HomeAction? =
    highlightedAction(card, issue)?.takeIf { it in homeActions(card) }

/** Status line of the card; locale-neutral, formatted by the UI. */
internal enum class HomeStatus { DISCONNECTED, CONNECTING, SWITCHING, CONNECTED, FAILED }

/**
 * An open undo window wins, then a start or restart in flight, then an unreachable server (a failed
 * start, or a failed delay test while the VPN stays on), then the running state.
 */
internal fun homeStatus(
    isRunning: Boolean,
    isConnecting: Boolean,
    undoPending: Boolean,
    issue: HomeIssue?,
): HomeStatus = when {
    undoPending -> HomeStatus.SWITCHING
    isConnecting -> HomeStatus.CONNECTING
    issue == HomeIssue.ServerUnreachable -> HomeStatus.FAILED
    isRunning -> HomeStatus.CONNECTED
    else -> HomeStatus.DISCONNECTED
}

/**
 * Card the home screen shows: the selected group when it is a card, else the first card (the selected
 * group can be the pseudo group "All" or one that was just removed), else null for an empty list.
 */
internal fun displayedCardId(cards: List<HomeCard>, selectedGroupId: String): String? =
    cards.firstOrNull { it.id == selectedGroupId }?.id ?: cards.firstOrNull()?.id

/**
 * Real group the home screen selects. The pseudo group "All" ("") or a removed group resolves to the
 * group holding the selected server, else the first real group; "" only when there is none.
 */
internal fun resolveHomeGroup(groupIds: List<String>, current: String, selectedServerGroup: String?): String {
    val real = groupIds.filter { it != ALL_GROUP_ID }
    return when {
        current != ALL_GROUP_ID && current in real -> current
        selectedServerGroup != null && selectedServerGroup in real -> selectedServerGroup
        else -> real.firstOrNull() ?: ALL_GROUP_ID
    }
}

/** Group id to subscription URL, taken from the same subscription load that builds the groups. */
internal fun subscriptionUrls(subscriptions: List<SubscriptionCache>): Map<String, String> =
    subscriptions.associate { it.guid to it.subscription.url }

/** Last successful update for the "not updated" strip; null when unknown (-1 on "update all"), so no bogus date is shown. */
internal fun updateFailedDate(issue: HomeIssue.UpdateFailed): Long? = issue.lastUpdated.takeIf { it > 0L }

/** Remembered server if it still exists in the group, else the group's first server, else null. */
internal fun resolveServerForSubscription(remembered: String?, groupGuids: List<String>): String? =
    remembered?.takeIf { it in groupGuids } ?: groupGuids.firstOrNull()

/** First run only when no subscription exists and there is no server to connect to. */
internal fun shouldShowFirstRun(cards: List<HomeCard>, serverCount: Int): Boolean =
    serverCount == 0 && cards.none { !it.isManual }

internal val HOME_ACCENTS: List<Long> = listOf(
    0xFF2447C8, 0xFF6B2A80, 0xFF0E6A70, 0xFF1F6B3A, 0xFF8C1D40, 0xFF7A4A00,
)

/** White overlay behind chips, actions and strips drawn on an accent background; keeps white text at 4.5:1 on every accent. */
internal const val HOME_ON_ACCENT_OVERLAY = 0.12f

internal fun accentFor(id: String): Int = Math.floorMod(id.hashCode(), HOME_ACCENTS.size)
