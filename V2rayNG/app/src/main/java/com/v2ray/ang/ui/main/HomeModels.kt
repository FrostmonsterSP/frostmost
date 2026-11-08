package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.ui.compose.FrostSurfaceColors

/**
 * Pure model of the home screen; no Android or Compose dependencies. Identity is by group id /
 * server GUID only.
 *
 * The one exception is the accent colour: [ACCENT_ON_PAGE] and [ACCENT_ON_PAGE_MUTED] are Compose
 * `Color` values. They are constants of the scheme rather than a theme lookup, so the only thing a
 * JVM test needs to read them is `toArgb()`, which does not touch the Android framework. Keeping
 * the pair here lets the screen and the contrast tests share one definition of `on-accent`.
 */
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
    .mapIndexed { index, group ->
        val isManual = urls[group.id].isNullOrEmpty()
        HomeCard(
            id = group.id,
            name = group.remarks,
            info = if (isManual) null else group.usageInfo?.let { buildSubscriptionInfoUiModel(it, nowMillis) },
            isManual = isManual,
            announce = if (isManual) null else group.usageInfo?.announce,
            accent = accentFor(index),
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

/**
 * Subscription colors, light theme. Every entry keeps white text at 4.5:1 or better, also over the
 * [HOME_ON_ACCENT_OVERLAY] scrim and over anything the sheet scrim can leave of the accent.
 *
 * The palette was re-picked around an equal readable distance instead of around darkness: the
 * previous wine `#8C1D40` and bronze `#7A4A00` were the heaviest tones and read as "alarm" rather
 * than as the identity of an ordinary subscription. Each entry now sits at the same point of the
 * white-on-accent scale (6.5:1), so no accent shouts louder than another and the hue alone carries
 * the difference.
 *
 * Brightness has a hard ceiling here and it is not a taste call. WCAG's sRGB switch sits at channel
 * value 10/255, so a lighter pastel (a rose-400 or amber-400 tone) drops white text below 4.5:1 on
 * its own hue — measured 3.26:1 for that attempt. The lever that does work is hue and saturation at a
 * fixed contrast distance, and that is what these values use.
 *
 * Worst case over both palettes and both backgrounds (bare accent and the 10% overlay plate) is
 * 4.93:1, up from 4.62:1 before, so the lightest accent here is still calmer than the darkest one
 * used to be.
 */
internal val HOME_ACCENTS: List<Long> = listOf(
    0xFF3D57B2, // indigo
    0xFF734B9D, // violet
    0xFF1F676F, // teal
    0xFF2C6944, // green
    0xFF93435E, // rose
    0xFF825228, // amber
)

/**
 * The same subscription colors for the dark theme: the light values are too dark to read as color on
 * a dark surface, these keep white text at 4.5:1 as well.
 *
 * Same hues as [HOME_ACCENTS], one step deeper: on the near-black dark surface the lighter light-theme
 * values lose saturation against the accent page, which is why the dark entries keep more chroma.
 */
internal val HOME_ACCENTS_DARK: List<Long> = listOf(
    0xFF3550B8, // indigo
    0xFF6B4299, // violet
    0xFF14606A, // teal
    0xFF25603B, // green
    0xFF8C3A55, // rose
    0xFF7A4A18, // amber
)

/**
 * White overlay behind blocks drawn on an accent background: the mockup's calm "raised, not
 * shouted" tone. 12% already dropped white text to 4.9:1 on the teal accent, so it stays at 10%.
 */
internal const val HOME_ON_ACCENT_OVERLAY = 0.10f

/**
 * Accent index of the group at [position] in the card list. The first (or only) subscription is
 * always accent 0.
 *
 * It used to hash the group id, which made "the default accent" a lottery: one test subscription drew
 * the wine tone and that became what the app looked like out of the box. Position is stable for a
 * given `groups` order, and that order is the persisted subscription order, not the visible or
 * selected card — so adding, removing, or switching cards cannot repaint an unrelated card.
 *
 * More subscriptions than colours wrap around ([Math.floorMod]), so the index never leaves the
 * palette.
 */
internal fun accentFor(position: Int): Int = Math.floorMod(position, HOME_ACCENTS.size)

/** Accent index [accent] as a color, in the palette of the current theme. */
internal fun accentColor(accent: Int, dark: Boolean): Long =
    if (dark) HOME_ACCENTS_DARK[Math.floorMod(accent, HOME_ACCENTS_DARK.size)]
    else HOME_ACCENTS[Math.floorMod(accent, HOME_ACCENTS.size)]

// ---------- Accent page ----------

/**
 * How much white is mixed into the accent while the VPN is on: the card lifts from "the plain
 * accent" to "the accent, lit". It is a *lift of the same hue*, not a different colour, so the card
 * keeps the identity of its subscription and only gains the reading that says the tunnel is up.
 *
 * 0.16 was chosen at the light palette's darkest entries, which are the ones with the least room
 * before white text starts to lose contrast: it is a visible step on every hue, and white still
 * clears 4.5:1 at the worst accent in both palettes. Mixing this back towards white would erase the
 * step; mixing more would start to read as a pale card with a bright title.
 */
internal const val HOME_CARD_LIT_WHITE = 0.16f

/** `on-accent`: the foreground of anything drawn on a subscription accent. */
internal val ACCENT_ON_PAGE = androidx.compose.ui.graphics.Color.White

/**
 * `on-accent-muted`: the second line on an accent. 0.96 clears 4.5:1 on both backgrounds the line is
 * drawn on — the bare accent (6.15:1 at its worst, teal in the light palette) and the 10% overlay
 * plate a tile below the card paints (4.93:1 at its worst, violet in the light palette).
 *
 * Two published values were measured against the accent alone and are not enough here: the
 * scheme's 0.88 drops to 4.17:1 once the plate is behind it, and [FROST_MUTED_ALPHA]'s 0.72 is
 * measured against neutral surfaces and reaches only 3.92:1 on the old dark teal at all. 0.94 sits on
 * the limit exactly (4.50:1) and flips over a one-unit rounding, so this takes the step above it.
 *
 * `HomeModelsTest.everyTextOnTheAccentedPageStaysReadable` holds this number in place; lowering it
 * fails that test at the exact accent it breaks on.
 */
internal val ACCENT_ON_PAGE_MUTED = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.96f)

/**
 * Whether the whole page — not just the hero card — is painted with the subscription accent.
 *
 * One flag for the entire tree: the page background, the blocks below the card and the status-bar
 * icons all read this, so a block can never be drawn on the wrong background. When it is false the
 * page keeps the scheme's own pair and nothing visible changes.
 */
internal data class HomeAccentPage(
    val onAccent: Boolean,
    /** The accent that fills the page, or null when the page keeps the scheme's background. */
    val accent: androidx.compose.ui.graphics.Color?,
    /** `on-accent`, or null when the page is not accented. */
    val content: androidx.compose.ui.graphics.Color?,
    /** `on-accent-muted`, or null when the page is not accented. */
    val muted: androidx.compose.ui.graphics.Color?,
)

/**
 * The card's own surface: what fills it, and the foreground everything on it is drawn in.
 *
 * At rest the card *is* its subscription accent — the palette entry as it comes. While the VPN is
 * on the same accent is lifted by [HOME_CARD_LIT_WHITE], so the whole plate brightens as one block
 * instead of only its status chip changing colour. Both states share [ACCENT_ON_PAGE]: the lift is
 * small enough that the white-text threshold holds at the darkest accent of each palette, which is
 * why one foreground covers the pair.
 *
 * Pure function on purpose: the composable feeds it the accent of the card's palette entry and the
 * theme, and `HomeModelsTest`/`ColourSchemeTest` assert the contrast of the result for every entry.
 */
internal fun homeCardHighlight(
    accent: androidx.compose.ui.graphics.Color,
    connected: Boolean,
): FrostSurfaceColors =
    FrostSurfaceColors(
        container = if (connected) accent.mixTowardsWhite(HOME_CARD_LIT_WHITE) else accent,
        content = ACCENT_ON_PAGE,
    )

/**
 * [fraction] of white mixed into this colour, alpha kept at full opacity.
 *
 * Compositing white *over* the accent, not interpolating the ARGB words: the alpha channel of an
 * opaque colour is 0xFF in both ends, so only the three channels move and the result can never come
 * out translucent — the failure that once turned the accent page into a wash.
 */
internal fun androidx.compose.ui.graphics.Color.mixTowardsWhite(
    fraction: Float,
): androidx.compose.ui.graphics.Color {
    val t = fraction.coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.Color(
        red = red + (1f - red) * t,
        green = green + (1f - green) * t,
        blue = blue + (1f - blue) * t,
        alpha = 1f,
    )
}

/**
 * The page accent of one card. A connected VPN fills the whole page with the accent of the
 * subscription on screen: the accent is the identity of that subscription and the page already
 * shows nothing else it could tint.
 *
 * The accent arrives as a [androidx.compose.ui.graphics.Color], never as a packed Long: `Color.value`
 * is the colour's **float bits**, so `Color(color.value.toLong())` rebuilds a colour whose alpha
 * byte is that float's exponent (0x4F for every entry of the palette, about 31%) instead of 0xFF.
 * That turned the accent page into a near-transparent wash over the neutral background, which is
 * why a connected VPN left the page looking unaccented. Keep the type; do not add a Long overload.
 */
internal fun homeAccentPage(
    accent: androidx.compose.ui.graphics.Color,
    connected: Boolean,
): HomeAccentPage =
    if (!connected) HomeAccentPage(onAccent = false, accent = null, content = null, muted = null)
    else HomeAccentPage(
        onAccent = true,
        accent = accent,
        content = ACCENT_ON_PAGE,
        muted = ACCENT_ON_PAGE_MUTED,
    )

/**
 * Whether the hero card's power target can start a connection now. A start from "off" is already in
 * flight ([powerEnabled] false), and a second start would queue a duplicate behind the first.
 *
 * This is the guard the whole-card tap goes through. The card is one focusable node, so the UI
 * cannot hide the second tap the way a disabled button would hide it; the action itself has to be
 * the one that refuses it.
 */
internal fun canStartFromOff(isRunning: Boolean, connecting: Boolean, powerEnabled: Boolean): Boolean =
    isRunning || (powerEnabled && !connecting)

/**
 * Whether the card's single toggle node reports itself as on: a VPN that is running, or one being
 * started or switched. `HomeStatus.CONNECTED` alone would drop the on-state during a switch, and
 * `HomeStatus.FAILED` can sit on top of a VPN that is still up (an unreachable server).
 */
internal fun heroSwitchOn(isRunning: Boolean, switching: Boolean): Boolean = isRunning || switching

/**
 * Whether the round power control still acts on its own. It does not any more: the whole card is
 * the only toggle target, and the circle only shows the state. Kept as a named constant so the
 * next reader does not re-attach a click to it while adding sense to the control.
 *
 * The region below the state panel is not covered either: its status label travels in a chip with
 * its own background, so it needs no tap handling of its own.
 */
internal const val HERO_POWER_IS_INDICATOR_ONLY = true

/** What the hero card shows about the connection; built by the screen from the UI state. */
internal data class HomeConnection(
    val status: HomeStatus,
    /** Selected server name and, when connected and measured, its delay; null when no server is resolved. */
    val serverName: String?,
    val delayMillis: Long?,
    val isRunning: Boolean,
    /** A start from "off" is in flight: the card cannot start a second one, so it waits for the result. */
    val powerEnabled: Boolean,
    /**
     * A start is in flight. [status] already reads CONNECTING for it, but the status also reflects
     * an undo window and an unreachable server, so the refusal of a repeat start reads this field
     * instead of inferring it.
     */
    val isConnecting: Boolean,
)

/**
 * The connection a card that is not the shown one reports: it knows nothing about the running VPN,
 * so it must not claim any of it. A preview reads "off", cannot start or stop anything, and carries
 * no server line of its own — showing the *shown* subscription's server under another
 * subscription's name was the one wrong reading this pager could produce.
 */
internal fun HomeConnection.disconnected(serverName: String?): HomeConnection = HomeConnection(
    status = HomeStatus.DISCONNECTED,
    serverName = serverName,
    delayMillis = null,
    isRunning = false,
    powerEnabled = false,
    isConnecting = false,
)

/** String resource naming a routing preset; null is the "custom rules" state. */
@androidx.annotation.StringRes
internal fun routingPresetLabelRes(preset: com.v2ray.ang.enums.RoutingType?): Int = when (preset) {
    com.v2ray.ang.enums.RoutingType.GLOBAL -> com.v2ray.ang.R.string.home_rules_global
    com.v2ray.ang.enums.RoutingType.WHITE_RUSSIA -> com.v2ray.ang.R.string.home_rules_russia
    com.v2ray.ang.enums.RoutingType.WHITE_IRAN -> com.v2ray.ang.R.string.home_rules_iran
    com.v2ray.ang.enums.RoutingType.WHITE -> com.v2ray.ang.R.string.home_rules_china_white
    com.v2ray.ang.enums.RoutingType.BLACK -> com.v2ray.ang.R.string.home_rules_china_black
    null -> com.v2ray.ang.R.string.home_rules_custom
}
