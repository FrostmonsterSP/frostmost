package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.ui.compose.LocalDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged

private val CARD_SHAPE = RoundedCornerShape(24.dp)
private val POWER_SHAPE = RoundedCornerShape(26.dp)
private val ACTION_SHAPE = RoundedCornerShape(14.dp)

internal fun HomeCard.accentColor(): Color = Color(HOME_ACCENTS[accent])

/** What the active card shows about the connection; built by [HomeScreen] from the UI state. */
internal data class HomeConnection(
    val status: HomeStatus,
    /** Selected server name and, when connected and measured, its delay; null when no server is resolved. */
    val serverName: String?,
    val delayMillis: Long?,
    val isRunning: Boolean,
    /** A start from "off" is in flight: the button cannot cancel it, so it waits for the result. */
    val powerEnabled: Boolean,
)

/** Named buttons for every subscription; the accessible equivalent of swiping the card. */
@Composable
internal fun HomeSubscriptionRow(
    cards: List<HomeCard>,
    shownId: String,
    lit: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latestCards by rememberUpdatedState(cards)
    // Keep the active button fully visible, also after the list changes.
    LaunchedEffect(shownId, cards.size) {
        val index = latestCards.indexOfFirst { it.id == shownId }
        if (index < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null && item.offset >= 0 &&
                item.offset + item.size <= layout.viewportEndOffset - layout.afterContentPadding
        if (!fullyVisible) listState.animateScrollToItem(index)
    }
    val label = stringResource(R.string.home_subscriptions)
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics { this.contentDescription = label },
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cards, key = { it.id }) { card ->
            val selected = card.id == shownId
            val accent = card.accentColor()
            val (container, content, dot) = when {
                lit && selected -> Triple(Color.White, accent, accent)
                lit -> Triple(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), Color.White, Color.White)
                selected -> Triple(accent, Color.White, Color.White)
                else -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface, accent)
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(container)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(card.id) })
                    .padding(start = 11.dp, end = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Text(
                    card.name,
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Subscription cards, swiped sideways. A swipe (or any scroll the user starts, including an
 * accessibility scroll) that settles on another card sends [onSwitch] once; scrolls this composable
 * starts itself to follow [shownId] never do, so state and pager cannot loop. Pages are keyed by id.
 */
@Composable
internal fun HomeCardPager(
    cards: List<HomeCard>,
    shownId: String,
    connection: HomeConnection,
    lit: Boolean,
    onSwitch: (String) -> Unit,
    onPower: () -> Unit,
    onConfigure: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = cards.indexOfFirst { it.id == shownId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial) { cards.size }
    val latestCards by rememberUpdatedState(cards)
    val latestShown by rememberUpdatedState(shownId)
    val latestOnSwitch by rememberUpdatedState(onSwitch)
    // Plain holder, not snapshot state: it only marks scrolls this composable started.
    val programmatic = remember { booleanArrayOf(false) }

    LaunchedEffect(shownId, cards.map { it.id }) {
        val target = cards.indexOfFirst { it.id == shownId }
        if (target < 0 || pagerState.isScrollInProgress || pagerState.currentPage == target) return@LaunchedEffect
        programmatic[0] = true
        try {
            pagerState.animateScrollToPage(target)
        } finally {
            programmatic[0] = false
        }
    }

    LaunchedEffect(pagerState) {
        var userScroll = false
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    userScroll = !programmatic[0]
                    return@collect
                }
                val pageId = latestCards.getOrNull(pagerState.currentPage)?.id
                if (userScroll && pageId != null && pageId != latestShown) {
                    latestOnSwitch(pageId)
                } else if (!userScroll && pageId != latestShown) {
                    // A user drag interrupted our own scroll: settle back on the shown card.
                    val target = latestCards.indexOfFirst { it.id == latestShown }
                    if (target >= 0) pagerState.scrollToPage(target)
                }
                userScroll = false
            }
    }

    val single = cards.size <= 1
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = if (single) PaddingValues(horizontal = 16.dp) else PaddingValues(start = 16.dp, end = 28.dp),
        pageSpacing = 8.dp,
        userScrollEnabled = !single,
        key = { page -> cards.getOrNull(page)?.id ?: "home-card-$page" },
    ) { page ->
        val card = cards.getOrNull(page) ?: return@HorizontalPager
        HomeSubscriptionCard(
            card = card,
            active = card.id == shownId,
            connection = connection,
            lit = lit,
            onSelect = { onSwitch(card.id) },
            onPower = onPower,
            onConfigure = { onConfigure(card.id) },
        )
    }
}

@Composable
private fun trafficLine(info: SubscriptionInfoUiModel?): String {
    info ?: return stringResource(R.string.home_traffic_unknown)
    val used = info.usedBytes.toTrafficString()
    return when {
        info.totalBytes <= 0L -> stringResource(R.string.home_traffic_unlimited, used)
        (info.trafficFraction ?: 0f) >= 1f ->
            stringResource(R.string.home_traffic_used_up, used, info.totalBytes.toTrafficString())
        else -> stringResource(R.string.home_traffic_of, used, info.totalBytes.toTrafficString())
    }
}

@Composable
private fun expiryLine(info: SubscriptionInfoUiModel?): String {
    val millis = info?.expireMillis ?: return stringResource(R.string.home_expire_none)
    val date = formatHomeDate(millis)
    return stringResource(if (info.expired) R.string.home_expired_on else R.string.home_expire_until, date)
}

@Composable
private fun statusText(status: HomeStatus): String = stringResource(
    when (status) {
        HomeStatus.DISCONNECTED -> R.string.home_status_disconnected
        HomeStatus.CONNECTING -> R.string.home_status_connecting
        HomeStatus.SWITCHING -> R.string.home_status_switching
        HomeStatus.CONNECTED -> R.string.home_status_connected
        HomeStatus.FAILED -> R.string.home_status_failed
    }
)

@Composable
private fun serverLine(connection: HomeConnection): String {
    val name = connection.serverName ?: return stringResource(R.string.home_server_none)
    val delay = connection.delayMillis
    return if (connection.status == HomeStatus.CONNECTED && delay != null && delay > 0L) {
        stringResource(R.string.home_server_delay, name, delay)
    } else {
        stringResource(R.string.home_server, name)
    }
}

/**
 * One subscription card. Off: the card has the subscription color with white text. Lit (connected):
 * the screen takes the color and the card turns to the theme surface (dark in dark theme).
 * A card that is not the shown one is only a preview: one button that selects it.
 */
@Composable
private fun HomeSubscriptionCard(
    card: HomeCard,
    active: Boolean,
    connection: HomeConnection,
    lit: Boolean,
    onSelect: () -> Unit,
    onPower: () -> Unit,
    onConfigure: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val accent = card.accentColor()
    val onCard = lit && active
    val colors = if (onCard) {
        OnBackgroundColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
    } else {
        OnBackgroundColors(accent, Color.White)
    }
    val status = if (active) connection.status else HomeStatus.DISCONNECTED
    val okColor = if (dark) Color(0xFF62D9AE) else Color(0xFF0A7455)
    val busyColor = if (dark) Color(0xFFFFC07A) else Color(0xFF8F4C08)
    val statusColor = when {
        !onCard -> Color.White
        status == HomeStatus.CONNECTED -> okColor
        status == HomeStatus.CONNECTING || status == HomeStatus.SWITCHING -> busyColor
        status == HomeStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val selectLabel = stringResource(R.string.home_card_select)
    val cardName = stringResource(R.string.home_card_named, card.name)

    HomeTile(
        colors = colors,
        shape = CARD_SHAPE,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 216.dp)
            .let { if (active) it else it.semantics { this.contentDescription = cardName } },
        onClick = if (active) null else onSelect,
        onClickLabel = if (active) null else selectLabel,
        selectedState = active,
    ) {
        Column(
            Modifier
                .let { if (active) it else it.clearAndSetSemantics {} }
                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    HomeTile(
                        colors = OnBackgroundColors(Color.Transparent, LocalContentColor.current),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, LocalContentColor.current.copy(alpha = 0.55f)),
                        onClick = onConfigure,
                        onClickLabel = stringResource(R.string.home_configure_named, card.name),
                    ) {
                        Row(
                            Modifier
                                .heightIn(min = 36.dp)
                                .padding(start = 8.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_settings_24dp), null, Modifier.size(16.dp))
                            Text(stringResource(R.string.home_configure), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            // Read-only figures form one node so TalkBack reads them in one go.
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trafficLine(card.info), style = MaterialTheme.typography.bodyMedium)
                card.info?.trafficFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .clearAndSetSemantics {},
                        color = if (onCard) (if (dark) MaterialTheme.colorScheme.onSurface else accent) else Color.White,
                        trackColor = if (onCard) MaterialTheme.colorScheme.surfaceVariant else Color.White.copy(alpha = 0.3f),
                        drawStopIndicator = {},
                    )
                }
                val expired = card.info?.expired == true
                Text(
                    expiryLine(card.info),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (expired) FontWeight.Bold else FontWeight.Normal,
                    color = if (expired && onCard) busyColor else LocalContentColor.current,
                )
            }
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PowerButton(
                    isRunning = active && connection.isRunning,
                    enabled = active && connection.powerEnabled,
                    busy = active && (status == HomeStatus.CONNECTING || status == HomeStatus.SWITCHING),
                    onCard = onCard,
                    accent = accent,
                    onClick = onPower,
                )
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        statusText(status),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                    if (active) {
                        Text(
                            serverLine(connection),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerButton(
    isRunning: Boolean,
    enabled: Boolean,
    busy: Boolean,
    onCard: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val warn = warnColors()
    val colors = when {
        busy -> warn
        onCard -> OnBackgroundColors(accent, Color.White)
        else -> OnBackgroundColors(Color.White, accent)
    }
    val label = stringResource(if (isRunning) R.string.home_power_off else R.string.home_power_on)
    HomeTile(
        colors = colors,
        shape = POWER_SHAPE,
        modifier = Modifier.size(84.dp),
        onClick = onClick,
        onClickLabel = label,
        enabled = enabled,
    ) {
        Column(
            Modifier
                .size(84.dp)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** "Update", "Account", "Support" under the card; missing data hides the button. */
@Composable
internal fun HomeActionRow(
    card: HomeCard,
    issue: HomeIssue?,
    lit: Boolean,
    updating: Boolean,
    onUpdate: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = homeActions(card)
    if (actions.isEmpty()) return
    val hot = visibleHighlight(card, issue)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            val (labelRes, iconRes, onClick) = when (action) {
                HomeAction.UPDATE -> Triple(
                    if (updating) R.string.home_action_updating else R.string.home_action_update,
                    R.drawable.ic_refresh_24dp,
                    onUpdate,
                )
                HomeAction.CABINET -> Triple(
                    R.string.home_action_cabinet,
                    R.drawable.ic_about_24dp,
                    { card.info?.webPageUrl?.let(onOpenLink); Unit },
                )
                HomeAction.SUPPORT -> Triple(
                    R.string.home_action_support,
                    if (SubscriptionInfoParser.isTelegramLink(card.info?.supportUrl)) R.drawable.ic_telegram_24dp else R.drawable.ic_feedback_24dp,
                    { card.info?.supportUrl?.let(onOpenLink); Unit },
                )
            }
            val colors = when {
                action != hot -> neutralColors(lit)
                action == HomeAction.UPDATE -> OnBackgroundColors(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
                else -> warnColors()
            }
            HomeTile(
                colors = colors,
                modifier = Modifier.weight(1f),
                shape = ACTION_SHAPE,
                border = if (action == hot) BorderStroke(2.dp, colors.content) else null,
                onClick = onClick,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
