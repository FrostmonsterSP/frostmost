package com.v2ray.ang.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.FrostElevation
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSurfaceColors
import com.v2ray.ang.ui.compose.HomeMotionEmphasisColorSpec
import com.v2ray.ang.ui.compose.HomeMotionRevealSpec
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.LocalStatusColors
import com.v2ray.ang.ui.compose.homeMotionSpringSpec
import kotlinx.coroutines.flow.distinctUntilChanged

// ---------- the page one of this file's blocks is drawn on ----------

/** Air on each side of a swipe dot; the mockup's `.dots` puts `6px` between two marks. */
private val HOME_DOT_GAP = 6.dp

/** The inactive mark of the swipe indicator: a short bar, the mockup's `20×8` pill. */
private val HOME_DOT_SIZE = 8.dp

/** The current mark of the swipe indicator. */
private val HOME_DOT_ACTIVE_WIDTH = 20.dp

/**
 * How much of a neighbouring card shows beside the current one. This sliver is what tells a finger
 * the card can move before the dots under it have been read, and it is the mockup's `28px` padding.
 *
 * It is applied to *both* ends of the pager, not only to the end. With a one-sided padding the page
 * area is flush against the pager's start edge, so the page that arrives from the left during a
 * rightward swipe is clipped at that edge and the card looks pinned to it. Symmetric padding keeps
 * every page, whichever direction it arrives from, completely inside the page area with both of its
 * edges visible.
 */
private val HOME_NEXT_CARD_PEEK = 28.dp

/**
 * The height of both figure rails in the card, traffic and expiry.
 *
 * One constant for the pair keeps them from drifting apart: two rails measuring different things
 * must still look like one widget. The pill shape is derived from it ([FrostRadius.pill]), so the
 * rail's corners are always exactly half its height and can never be set out of scale.
 */
private val HOME_FIGURE_BAR_HEIGHT = 8.dp

/**
 * A dot of the swipe indicator.
 *
 * The dots sit on the *page*, never on the card, so the accent page needs its own pair: white for
 * the current mark and a dimmed white for the rest. The mockup puts the dim one at 0.45; 0.55 is the
 * measured floor that keeps every mark a visible edge (WCAG 1.4.11 asks for 3:1 against the page),
 * because a mark nobody can see is not an affordance. The neutral page keeps the scheme's own muted
 * line and its primary.
 */
@Composable
private fun swipeDotColor(current: Boolean, onAccent: Boolean): Color = when {
    onAccent && current -> ACCENT_ON_PAGE
    onAccent -> Color.White.copy(alpha = 0.55f)
    current -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outlineVariant
}

/**
 * The swipe indicator: one mark per subscription, the current one drawn as a short bar.
 *
 * It reports the pager's page and carries no action of its own. A dot is smaller than a touch
 * target and swiping is the gesture it stands for, so making each dot a target would either break
 * the 48dp rule or offer a second, fiddly way to do what the swipe already does. It is therefore one
 * read-only node saying what it is. The swipe is the only way to change subscription, and the dots
 * count the cards so the marks say whether there is anything to swipe to.
 */
@Composable
private fun SwipeDots(count: Int, selected: Int, onAccent: Boolean) {
    val label = stringResource(R.string.home_subscriptions)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = FrostSpacing.s)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val current = index == selected
            Box(
                Modifier
                    .padding(horizontal = HOME_DOT_GAP / 2)
                    .size(width = if (current) HOME_DOT_ACTIVE_WIDTH else HOME_DOT_SIZE, height = HOME_DOT_SIZE)
                    .clip(CircleShape)
                    .background(swipeDotColor(current, onAccent))
            )
        }
    }
}

/**
 * The subscription cards, swiped sideways, with the dot row under the current one.
 *
 * The named chip row that used to stand above the card is gone: it repeated every name in text
 * above a card that already says which subscription it is, and the design asks for a swipe. The menu
 * button that offered the same choice under the card is gone too, so the swipe and its dots are now
 * the *only* way to change subscription — which is why the pager is a real pager rather than a
 * one-card view, and why the dots are the affordance that says there is something to swipe to.
 *
 * A swipe that settles on another card sends [onSwitch] once, keyed by the group id of the page it
 * landed on; repeating the swipe reaches the next one, and a further swipe past the last card has
 * nothing to switch to. Scrolls this composable starts itself to follow [shownId] never send it, so
 * state and pager cannot loop. Pages are keyed by domain id, never by position.
 *
 * The pager's content padding is symmetric, [HOME_NEXT_CARD_PEEK] at each end, so a page always
 * fills the page area minus that sliver. The page that comes back from the left during a rightward
 * swipe is then as visible as the one waiting on the right; with end-only padding it began flush
 * against the pager's start edge and looked cut off there. A single card takes no padding, because a
 * lone card has no neighbour to hint at.
 */
@Composable
internal fun HomeSubscriptionPager(
    cards: List<HomeCard>,
    shownId: String,
    connection: HomeConnection,
    onAccent: Boolean,
    onSwitch: (String) -> Unit,
    onToggle: () -> Unit,
    onConfigure: (String) -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        contentPadding = if (single) PaddingValues(horizontal = 0.dp) else PaddingValues(horizontal = HOME_NEXT_CARD_PEEK),
        pageSpacing = FrostSpacing.s,
        userScrollEnabled = !single,
        key = { page -> cards.getOrNull(page)?.id ?: "home-card-$page" },
    ) { page ->
        val card = cards.getOrNull(page) ?: return@HorizontalPager
        val active = card.id == shownId
        HomeHeroCard(
            card = card,
            // Only the shown card reports the connection: a neighbour is a preview of another
            // subscription, and its own state is not what this screen is showing.
            connection = if (active) connection else connection.disconnected(serverName = null),
            accent = Color(accentColor(card.accent, LocalDarkTheme.current)),
            active = active,
            onAccent = onAccent,
            onToggle = { if (active) onToggle() },
            onConfigure = { onConfigure(card.id) },
        )
    }
    if (!single) {
        SwipeDots(count = cards.size, selected = pagerState.currentPage, onAccent = onAccent)
    }
}

/**
 * The hero card: what the app is for, in one plate. It is also the on/off switch of the VPN.
 *
 * Design rules applied here (this is the "financial" language):
 * - the subscription accent fills this card, and the page below it while the VPN is on;
 * - the traffic figure is the largest number on screen, because it is the number people open the
 *   app to see; the connection state is second;
 * - depth comes from one soft shadow and a tonal inner panel, not from stacked borders;
 * - every colour pair is created by the surface system, so text cannot end up black on black.
 *
 * Interaction rules, which are the fragile part:
 * - the plate is a single toggle node: one name, one on/off state, one action. The circle and the
 *   state panel inside it are merged into that node and carry no action of their own, so touch,
 *   TalkBack, Enter and D-pad centre all reach the same target once;
 * - "Configure" is the one descendant that keeps its own node, because it does something else. That
 *   holds on the shown card; the chip is still laid out on a preview, so the name keeps the same
 *   width and the header the same height everywhere, but there it is invisible and disabled, with
 *   neither a click nor a focus stop. The
 *   plate stays enabled while a start is in flight and the action itself refuses the repeat
 *   ([canStartFromOff]). A disabled plate would swallow the tap and would report the card as
 *   unavailable, which it is not: it is still a switch;
 * - a card that is not the shown one is a *preview*: it draws its own name and figures, reports no
 *   connection and carries no toggle. Swiping to it is the action; a tap on a neighbour that also
 *   switched would make the card it landed on look like it had already started something.
 */
@Composable
private fun HomeHeroCard(
    card: HomeCard,
    connection: HomeConnection,
    accent: Color,
    active: Boolean,
    onAccent: Boolean,
    onToggle: () -> Unit,
    onConfigure: () -> Unit,
) {
    // The accent palette is fixed so white always reads on it; the check keeps that true if a
    // future palette entry is added carelessly.
    val onCard = ACCENT_ON_PAGE
    val muted = ACCENT_ON_PAGE_MUTED

    val status = connection.status
    val on = heroSwitchOn(connection.isRunning, status == HomeStatus.SWITCHING)
    val canToggle = active && canStartFromOff(connection.isRunning, connection.isConnecting, connection.powerEnabled)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The press is animated on the whole card and read by the circle inside it: a press that lands
    // on the plate is not visible to its children, so it is passed down from here.
    val scale by animateFloatAsState(
        targetValue = if (canToggle && pressed) 0.99f else 1f,
        animationSpec = homeMotionSpringSpec(),
        label = "heroPress",
    )

    // --- the card lighting up and opening while the VPN is on ---
    //
    // Which card is "on" is a property of the connection this card reports, not of the screen:
    // a neighbour is drawn with `connection.disconnected(...)`, so a card the user has not
    // swiped to stays at rest even while the VPN is up. Both values are animated from the same
    // target so the colour and the air inside the plate move as one change.
    val lit = connection.isRunning
    val surface by animateColorAsState(
        targetValue = homeCardHighlight(accent, lit).container,
        animationSpec = HomeMotionEmphasisColorSpec,
        label = "heroCardLit",
    )
    // 0 = at rest, 1 = open. It is a single value because two consumers read it: the plate's
    // elevation and the inner panel's breathing room. Animating a height instead would make the
    // card change its measured size, which moves the configuration list under a finger that has
    // just pressed the card.
    val open by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = HomeMotionRevealSpec,
        label = "heroCardOpen",
    )
    val panelInset = FrostSpacing.xs * open
    val cardLift = FrostElevation.hero + (FrostElevation.action - FrostElevation.hero) * open

    val plate = Modifier
        .fillMaxWidth()
        .graphicsLayer { scaleX = scale; scaleY = scale }
    FrostSurface(
        colors = FrostSurfaceColors(surface, onCard),
        modifier = if (active) {
            plate.toggleable(
                value = on,
                role = Role.Switch,
                interactionSource = interactionSource,
                // The press is animated on the card itself, so the ripple would only fight the
                // scale for the same gesture.
                indication = null,
                onValueChange = { if (canToggle) onToggle() },
            )
        } else {
            // A preview: no toggle and no press. It takes no semantics of its own either —
            // `clearAndSetSemantics` keeps the node's own properties, so it left a focusable node
            // on every neighbour page: a focus stop, reachable by D-pad, Tab and TalkBack, that did
            // nothing when activated. A page the user has not swiped to is not a target; the card
            // announces itself once it is the shown one.
            plate
        },
        shape = FrostRadius.hero,
        elevation = cardLift,
    ) {
        Column(
            // Everything inside is one reading of the same card, so it merges into the toggle
            // node instead of offering separate nodes for the name, the figures and the state.
            // The configure chip opts out of the merge below with its own semantics node.
            Modifier
                .semantics(mergeDescendants = true) {}
                .padding(FrostSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onCard,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (card.isManual) {
                        Text(
                            stringResource(R.string.home_manual_group),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }
                }
                // Outside the `if (active)`: the chip is laid out on every card so the name gets the
                // same width and the header the same height on all of them — mounting it only on the
                // shown card made its name wrap one line earlier, and the whole plate changed size
                // as the pager moved. On a preview it is drawn but invisible (`alpha(0f)`) and
                // disabled, so it is neither a click nor a focus stop.
                // Inside the plate, because it belongs to this card and looked like a loose
                // fragment while it sat beside it. A child with its own click handler consumes
                // the press before the parent toggleable sees it, so "configure" still cannot
                // turn the VPN on; clearAndSetSemantics keeps it a separate TalkBack node with
                // its own name instead of being merged into the card's single reading.
                ConfigureChip(
                    name = card.name,
                    onConfigure = onConfigure,
                    color = onCard,
                    modifier = Modifier
                        .padding(top = FrostSpacing.xs)
                        .alpha(if (active) 1f else 0f),
                    enabled = active,
                    // The chip's 48dp box is as tall as the whole two-line title, and its content
                    // sits in the middle of it — which put "Настроить" across the title's *second*
                    // line. Aligning it to the first line is what keeps the two apart, and it does
                    // so without shrinking the touch target.
                    alignToFirstLine = true,
                )
            }

            // --- the number: traffic used of total ---
            SubscriptionFigures(card = card, onAccent = onCard, muted = muted)

            // --- the power control and the state, inside the hero ---
            StatusPanel(
                status = status,
                onAccent = onCard,
                isRunning = connection.isRunning,
                // The screen's own rule, reused rather than restated: a running VPN is always
                // stoppable, and a start in flight is not started twice.
                enabled = canToggle,
                pressed = pressed,
                active = active,
                // The panel is what opens: the air it gains at the top and bottom is the whole
                // visible growth of the plate, so the card reads as unfolding around its switch.
                openInset = panelInset,
            )
        }
    }
}

/** Traffic and expiry, sized as the primary information of the card. */
@Composable
private fun SubscriptionFigures(
    card: HomeCard,
    onAccent: Color,
    muted: Color,
) {
    val info = card.info
    if (info == null) {
        Text(
            stringResource(R.string.home_traffic_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
        Text(
            info.usedBytes.toTrafficString(),
            style = MaterialTheme.typography.displaySmall,
            color = onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // --- traffic: the bar, then the sentence that gives it a denominator ---
        val trafficCaption = if (info.totalBytes <= 0L) {
            stringResource(R.string.home_traffic_unlimited_short)
        } else {
            stringResource(R.string.home_traffic_of_short, info.totalBytes.toTrafficString())
        }
        // An unlimited plan gets no bar. A bar measures a share of something, and a plan without a
        // quota has nothing to measure against: a full rail would read "almost out", an empty one
        // "nothing used", and both would be invented. The caption "No limit" is the whole honest
        // answer, so the number and that caption stand alone.
        info.trafficFraction?.let { fraction ->
            SubscriptionFigureBar(
                fraction = fraction,
                onAccent = onAccent,
                // Cleared, not described: the caption right below reads the figure aloud, and a
                // second node for the bar would repeat it.
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            trafficCaption,
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        // --- expiry: the bar, then the date it counts down to ---
        val expire = info.expireMillis
        // Null means the subscription never expires: with no end date there is nothing for the rail
        // to count down, so only the caption "no expiry" is drawn.
        subscriptionExpiryProgress(expire, info.expired, System.currentTimeMillis())?.let { fraction ->
            SubscriptionFigureBar(
                fraction = fraction,
                onAccent = onAccent,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            if (expire == null) {
                stringResource(R.string.home_expire_none)
            } else {
                val date = formatHomeDate(expire)
                stringResource(if (info.expired) R.string.home_expired_on else R.string.home_expire_until, date)
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (info.expired) FontWeight.SemiBold else FontWeight.Normal,
            color = muted,
        )
    }
}

/**
 * One of the card's two progress rails: the traffic share, or the remaining expiry window.
 *
 * Both rails are the same object, so they read as one device: [HOME_FIGURE_BAR_HEIGHT] tall, a pill
 * at half that height, solid [onAccent] fill over a translucent white rail. White on an accent was
 * measured at 4.5:1 or better for every palette entry, which is why the fill is the accent's own
 * on-colour rather than a theme lookup; the translucent rail only ever sits on a saturated accent,
 * so it never has to hold contrast on its own.
 */
@Composable
private fun SubscriptionFigureBar(
    fraction: Float,
    onAccent: Color,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(HOME_FIGURE_BAR_HEIGHT)
            .clip(FrostRadius.pill),
        color = onAccent,
        trackColor = Color.White.copy(alpha = 0.28f),
        drawStopIndicator = {},
    )
}

/**
 * The power control and the connection state, on a tonal panel so the reading part of the card
 * stays grouped. Nothing here is a target: the card is.
 *
 * The status label travels in a chip that brings its own background. A saturated status colour
 * drawn straight on an accent was measured at 1.08–1.29:1 at every hue, which is why the scheme
 * gives each role a `chip`/`onChip` pair; disconnection has no role, so it takes a translucent
 * white plate and the accent's own on-colour.
 *
 * [openInset] is the card's "open" amount as vertical air. The panel is where that growth is spent:
 * it gains this much above and below its row while the VPN is on, so the card unfolding is read as
 * the panel making room for its switch rather than as the whole plate jumping in height.
 */
@Composable
private fun StatusPanel(
    status: HomeStatus,
    onAccent: Color,
    isRunning: Boolean,
    enabled: Boolean,
    pressed: Boolean,
    active: Boolean,
    openInset: Dp,
) {
    val roles = LocalStatusColors.current
    val (chipContainer, chipContent) = when (status) {
        HomeStatus.CONNECTED -> roles.success.chip to roles.success.onChip
        HomeStatus.CONNECTING, HomeStatus.SWITCHING -> roles.warning.chip to roles.warning.onChip
        HomeStatus.FAILED -> roles.danger.chip to roles.danger.onChip
        HomeStatus.DISCONNECTED -> Color.White.copy(alpha = 0.18f) to onAccent
    }
    FrostSurface(
        colors = FrostSurfaceColors(Color.White.copy(alpha = 0.14f), onAccent),
        shape = FrostRadius.inner,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = FrostSpacing.m,
                    end = FrostSpacing.m,
                    top = FrostSpacing.m + openInset,
                    bottom = FrostSpacing.m + openInset,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
        ) {
            FrostPowerControl(
                isRunning = isRunning,
                enabled = enabled,
                busy = status == HomeStatus.CONNECTING || status == HomeStatus.SWITCHING,
                pressed = pressed,
                active = active,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
                Text(
                    stringResource(statusLabel(status)),
                    style = MaterialTheme.typography.labelLarge,
                    color = chipContent,
                    modifier = Modifier
                        .clip(FrostRadius.chip)
                        .background(chipContainer)
                        .padding(horizontal = FrostSpacing.s, vertical = FrostSpacing.xxs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: HomeStatus): Int = when (status) {
    HomeStatus.DISCONNECTED -> R.string.home_status_disconnected
    HomeStatus.CONNECTING -> R.string.home_status_connecting
    HomeStatus.SWITCHING -> R.string.home_status_switching
    HomeStatus.CONNECTED -> R.string.home_status_connected
    HomeStatus.FAILED -> R.string.home_status_failed
}
