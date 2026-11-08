package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.FrostChip
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSecondaryButton
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSurfaceColors
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostPrimaryButton
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.LocalStatusColors
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostInnerColors
import com.v2ray.ang.ui.compose.frostPageColors
import com.v2ray.ang.ui.compose.frostRaisedColors
import com.v2ray.ang.ui.compose.frostSecondaryActionColors

/**
 * Home screen of the fork: which subscription, whether the VPN is on, and how to turn it on.
 * Durable state comes from [MainViewModel]; only expansion, menus and the search field are local.
 *
 * The screen follows the FrostMost map: a fixed connection area (top bar, subscription row, hero
 * card, quick actions, message) above a scrollable configuration area (routing and servers, opening
 * in place). On a window too short to keep the connection area usable the whole page becomes one
 * scroll container instead, so nothing is clipped or shrunk away.
 */
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onEditSubscription: (String) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val cards = remember(uiState.groups, uiState.subscriptionUrls) {
        buildHomeCards(uiState.groups, uiState.subscriptionUrls, System.currentTimeMillis())
    }
    val shownId = displayedCardId(cards, uiState.selectedGroupId)
    val card = cards.firstOrNull { it.id == shownId }
    val issue = card?.let { resolveHomeIssue(it, uiState.homeIssue) }

    val selectedRows by remember(uiState.selectedGroupId) {
        mainViewModel.serverGroupState(uiState.selectedGroupId)
    }.collectAsStateWithLifecycle()
    val serverName = selectedRows.rows.firstOrNull { it.guid == uiState.selectedGuid }?.remarks
    val delay = (uiState.status as? MainStatus.ConnectionTest)?.result?.delayMillis
    val connection = HomeConnection(
        status = homeStatus(uiState.isRunning, uiState.isConnecting, uiState.undoTargetId != null, uiState.homeIssue),
        serverName = serverName,
        delayMillis = delay,
        isRunning = uiState.isRunning,
        powerEnabled = uiState.isRunning || !uiState.isConnecting,
        isConnecting = uiState.isConnecting,
    )

    val accent = card?.let { Color(accentColor(it.accent, LocalDarkTheme.current)) }
        ?: MaterialTheme.colorScheme.primary
    // The one flag of the whole tree: a connected VPN fills the page with the subscription accent,
    // and every block below the card reads this instead of the scheme's neutral pair. It is derived
    // once here and passed down, so the page and its contents cannot disagree about the background.
    val accentPage = homeAccentPage(
        // The colour itself, not `accent.value`: that is the colour's float bits, and rebuilding a
        // Color from them yields an almost transparent accent.
        accent = accent,
        connected = uiState.isRunning,
    )
    val page = if (accentPage.onAccent) {
        FrostSurfaceColors(accentPage.accent!!, accentPage.content!!)
    } else {
        frostPageColors()
    }
    // A dark glyph would vanish on the accent, so the icons go light exactly when the page does.
    LightStatusBarIcons(onAccent = accentPage.onAccent)

    // Every rememberSaveable of the screen lives above the early returns below: the downloading and
    // first-run branches skip the rest of the composable, and a slot created on only one side of
    // that branch would be a different slot each time the state flipped.
    //
    // The configuration area is one lazy list so the page has a single vertical scroll owner; the
    // screen falls back to scrolling the page itself when the connection area cannot stay fixed.
    var sectionState by rememberSaveable(stateSaver = homeSectionStateSaver) { mutableStateOf(HomeSectionState()) }
    // This copy is the source of truth for the server search: the ViewModel's filter is cleared by
    // other actions, so the field cannot read it back.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // A selection made elsewhere (a share dialog, the subscription editor) must still be findable.
    LaunchedEffect(shownId) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            onAction(MainAction.Search(""))
        }
    }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val fixedZonePolicy = rememberFixedZonePolicy(windowHeight)

    var sheet by rememberSaveable { mutableStateOf<HomeSheet?>(null) }
    // A clipboard, QR or file result arrives after the Add sheet closed: reopen it with the text and its type.
    LaunchedEffect(uiState.importDraft) { if (uiState.importDraft != null) sheet = HomeSheet.Add }
    HomeSheetHost(
        sheet = sheet,
        onSheetChange = {
            sheet = it
            if (it != HomeSheet.Add) onAction(MainAction.ClearImportDraft)
        },
        pendingRules = uiState.pendingRules,
        importDraft = uiState.importDraft,
        importDraftFailure = uiState.importDraftFailure,
        onAction = onAction,
        onNavigate = onNavigate,
    )

    val dialogs = rememberHomeDialogs(onAction)
    dialogs.Show(confirmRemove = uiState.confirmRemove, qrBitmap = uiState.shareQRCodeBitmap)
    (uiState.importState as? HomeImportState.Confirm)?.let { HomeLinkConfirm(it, onAction) }

    // Downloading replaces the page (never an empty home); first run shows only its form.
    if (uiState.importState is HomeImportState.Downloading) {
        HomeDownloading()
        return
    }
    val serverCount = uiState.serverCount
    if (serverCount != null && shouldShowFirstRun(cards, serverCount)) {
        HomeFirstRun(
            failed = uiState.importState as? HomeImportState.Failed,
            onAction = onAction,
            onOtherWays = { sheet = HomeSheet.More },
        )
        return
    }

    HomeFixedZoneLayout(
        page = page,
        onAccent = accentPage.onAccent,
        header = {
            HomeTopBar(
                connection = connection,
                onAccent = accentPage.onAccent,
                onAdd = { sheet = HomeSheet.Add },
                onMore = { sheet = HomeSheet.More },
            )
        },
        connectionArea = {
            if (card == null) {
                HomeEmpty(onAdd = { sheet = HomeSheet.Add })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap)) {
                    if (cards.size > 1) {
                        // Above the card and outside its plate. The shown card simply *is* this page
                        // while connected, so the row states that fact instead of repeating the
                        // page's own colour a second time: the selected pill keeps the scheme's
                        // inner tone, white in either state, and the dot stays the accent.
                        HomeSubscriptionRow(
                            cards = cards,
                            shownId = card.id,
                            onSelect = { id -> onAction(MainAction.SwitchSubscription(id, card.id)) },
                        )
                    }
                    HeroConnectionCard(
                        card = card,
                        connection = connection,
                        accent = if (accentPage.onAccent) accentPage.accent!! else accent,
                        onToggle = { onAction(MainAction.ToggleService) },
                        onConfigure = { onEditSubscription(card.id) },
                    )
                    HomeActionRow(
                        card = card,
                        issue = issue,
                        updating = isLoading,
                        onAccent = accentPage.onAccent,
                        onUpdate = { onAction(MainAction.UpdateGroupSubscription(card.id)) },
                        onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                    )
                    issue?.let {
                        HomeIssueStrip(
                            issue = it,
                            onAccent = accentPage.onAccent,
                            onOtherServer = { sectionState = sectionState.expand(HomeSection.Servers) },
                            onAllowVpn = { onAction(MainAction.ToggleService) },
                        )
                    }
                    card.announce?.takeIf { it.isNotBlank() }?.let { text ->
                        HomeAnnounceStrip(cardId = card.id, text = text, onAccent = accentPage.onAccent)
                    }
                }
            }
        },
        configuration = {
            HomeConfigurationList(
                // The card on screen, so the list always matches it; with no card at all the
                // stored selection is the only group there is to show.
                groupId = shownId ?: uiState.selectedGroupId,
                sectionState = sectionState,
                onSectionChange = { next -> sectionState = next },
                // Routing needs a card: with no subscription there is no preset to change.
                routingAvailable = card != null,
                preset = uiState.routingPreset,
                presetLoaded = uiState.routingPresetLoaded,
                selectedServerName = serverName,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    onAction(MainAction.Search(query))
                },
                onAction = onAction,
                mainViewModel = mainViewModel,
                uiState = uiState,
                dialogs = dialogs,
                isTesting = uiState.isTesting,
                onAddOwnRules = { sheet = HomeSheet.Add },
                // Tells the list whether it is the scroll owner or the page is: claiming the
                // viewport inside the page's own scroll crashes with infinite height constraints.
                zonePolicy = fixedZonePolicy,
                // The same flag the blocks above the list read, passed rather than derived again:
                // the sections are drawn on the accent too, and with the scheme's opaque surface
                // they read as holes in the page.
                onAccent = accentPage.onAccent,
            )
        },
        fixedZone = fixedZonePolicy,
        undo = {
            val undoName = uiState.undoTargetId?.let { id -> cards.firstOrNull { it.id == id }?.name }
            if (undoName != null) {
                HomeUndoBar(
                    targetName = undoName,
                    onUndo = { onAction(MainAction.UndoSwitch) },
                )
            }
        },
    )
}

/**
 * Status-bar icons follow the page they sit on. [onAccent] is the screen's one "page is on the
 * subscription accent" flag: a connected VPN paints the page white-text-on-accent, so the glyphs
 * must be light there in either theme, and the dark page keeps dark-glyphs-in-light-theme.
 */
@Composable
private fun LightStatusBarIcons(onAccent: Boolean) {
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    val latestDark by rememberUpdatedState(dark)
    val latestOnAccent by rememberUpdatedState(onAccent)
    // SideEffect, not an effect keyed once: AppTheme resets the icons in its own SideEffect, and a
    // child's SideEffect runs after the parent's in the same frame, so this one wins.
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !onAccent && !dark
    }
    DisposableEffect(view) {
        onDispose {
            val window = (view.context as? android.app.Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !latestOnAccent && !latestDark
        }
    }
}

/**
 * Brand and the two global actions. The brand line always reads the connection state, so the most
 * important fact of the screen is also the first thing TalkBack reaches.
 *
 * It sits straight on the page, so [onAccent] switches both texts and the "More" tile to the page's
 * own foreground. The "Add" action keeps its own surfaces: it is a filled button, and a filled
 * button on any background carries its own pair.
 */
@Composable
private fun HomeTopBar(
    connection: HomeConnection,
    onAccent: Boolean,
    onAdd: () -> Unit,
    onMore: () -> Unit,
) {
    val state = stringResource(homeStatusLabelRes(connection.status))
    val foreground = if (onAccent) ACCENT_ON_PAGE else LocalContentColor.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FrostSpacing.touchTarget)
            .padding(start = FrostSpacing.screenH, end = FrostSpacing.s, top = FrostSpacing.s, bottom = FrostSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
    ) {
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state,
                style = MaterialTheme.typography.bodySmall,
                // SECONDARY_ALPHA is measured against the neutral page and reaches only 3.92:1 on
                // the teal accent, so the accented page takes its own pair instead.
                color = if (onAccent) ACCENT_ON_PAGE_MUTED else foreground.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AddButton(onAccent = onAccent, onClick = onAdd)
        HomeTopBarAction(
            iconRes = R.drawable.ic_menu_24dp,
            labelRes = R.string.home_more,
            contentDescription = stringResource(R.string.home_more),
            onAccent = onAccent,
            onClick = onMore,
        )
    }
}

/**
 * Secondary line on a surface: one alpha for every muted label of the screen. It is a property of
 * the foreground the surface pushed down, so it can never be applied to a colour of another block.
 */
internal const val SECONDARY_ALPHA = 0.72f

/**
 * "Add" opens the Add sheet: the one action of the top bar.
 *
 * The tile is the same one as [HomeTopBarAction] beside it, painted with the same pair: on the
 * accent page it takes the translucent white plate with the page's white foreground. It used to
 * keep the scheme's filled pair in both states, which turned it into a dark slab in the middle of
 * the accent field while the button next to it was translucent — one bar drawn in two languages.
 */
@Composable
private fun AddButton(onAccent: Boolean, onClick: () -> Unit) {
    FrostSurface(
        colors = if (onAccent) {
            FrostSurfaceColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), ACCENT_ON_PAGE)
        } else {
            frostSecondaryActionColors(enabled = true)
        },
        modifier = Modifier.defaultMinSize(minHeight = FrostSpacing.touchTarget),
        shape = FrostRadius.tile,
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_add),
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = FrostSpacing.touchTarget)
                .padding(horizontal = FrostSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
        ) {
            Icon(
                painterResource(R.drawable.ic_add_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.home_add),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** A labelled round target: every icon with an action carries its name, never a bare glyph. */
@Composable
private fun HomeTopBarAction(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    contentDescription: String,
    onAccent: Boolean,
    onClick: () -> Unit,
) {
    FrostSurface(
        // On the accent page the tile takes the translucent white plate the blocks below the card
        // use; on the neutral page it keeps the scheme's own pair.
        colors = if (onAccent) {
            FrostSurfaceColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), ACCENT_ON_PAGE)
        } else {
            frostPageColors()
        },
        shape = FrostRadius.tile,
        onClick = onClick,
        onClickLabel = contentDescription,
    ) {
        Column(
            Modifier
                .size(FrostSpacing.touchTarget)
                .padding(FrostSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/**
 * Fallback for servers that no group holds: [shouldShowFirstRun] has already claimed the truly empty
 * install, so this screen never means "no subscriptions yet" and must not say so.
 */
@Composable
private fun HomeEmpty(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSpacing.screenH, vertical = FrostSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.m),
    ) {
        Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.home_empty_hint), style = MaterialTheme.typography.bodyMedium)
        FrostPrimaryButton(
            label = stringResource(R.string.home_add),
            icon = painterResource(R.drawable.ic_add_24dp),
            onClick = onAdd,
        )
    }
}

/**
 * How the page is laid out: [Fixed] keeps the connection area on screen and scrolls only the
 * configuration list; [ScrollAll] lets the whole page scroll, when the screen is too short to hold a
 * usable fixed area without clipping the card or its main action.
 */
internal enum class HomeZonePolicy { Fixed, ScrollAll }

/** Shortest window that still leaves room for the card plus a usable slice of the configuration list. */
private val MIN_FIXED_ZONE_WINDOW = 480.dp

/** A window shorter than [MIN_FIXED_ZONE_WINDOW] falls back to scrolling the whole page. */
internal fun rememberFixedZonePolicy(windowHeight: Dp): HomeZonePolicy =
    if (windowHeight < MIN_FIXED_ZONE_WINDOW) HomeZonePolicy.ScrollAll else HomeZonePolicy.Fixed

/**
 * Saves the open sections by name, so rotation and process death reopen what the user had open.
 * Names, not ordinals, so a saved state stays readable if the enum gains a member.
 */
private val homeSectionStateSaver = listSaver<HomeSectionState, String>(
    save = { state -> state.expanded.map { it.name } },
    restore = { names -> HomeSectionState(HomeSection.entries.filter { it.name in names }.toSet()) },
)

/**
 * The home page: [header] and [connectionArea] stay put while [configuration] scrolls under them.
 * In [HomeZonePolicy.ScrollAll] all three share one scroll container instead, so a short window
 * never clips the card or its main action. [undo] floats over the bottom in either policy.
 *
 * The canvases here take [page]'s container and nothing else: the blocks drawn on them bring their
 * own pair, so [onAccent] only decides the tint of the gaps between them (the header sits straight
 * on the page, and the configuration area always keeps the scheme's own surfaces).
 */
@Composable
private fun HomeFixedZoneLayout(
    page: FrostSurfaceColors,
    onAccent: Boolean,
    header: @Composable () -> Unit,
    connectionArea: @Composable () -> Unit,
    configuration: @Composable () -> Unit,
    fixedZone: HomeZonePolicy,
    undo: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(page.container)
            .statusBarsPadding(),
    ) {
        when (fixedZone) {
            HomeZonePolicy.Fixed -> Column(Modifier.fillMaxSize()) {
                header()
                // A fixed area of natural height: the card decides how tall it is, never a constant.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = FrostSpacing.screenH, end = FrostSpacing.screenH, top = FrostSpacing.xs),
                ) { connectionArea() }
                // Filling what the connection area leaves: the list, not the page, scrolls here.
                Box(Modifier.weight(1f)) { configuration() }
            }
            HomeZonePolicy.ScrollAll -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                header()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = FrostSpacing.screenH, end = FrostSpacing.screenH, top = FrostSpacing.xs),
                ) { connectionArea() }
                // The page already scrolls, so the list wraps its content instead of claiming a
                // viewport of its own; two nested vertical scrollers are what the spec forbids.
                configuration()
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) { undo() }
    }
}

/** Date in the user's locale, for expiry and "servers from" lines. */
@Composable
internal fun formatHomeDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(DateFormat.LONG, locale).format(Date(millis))
}

/**
 * The subscription buttons: named targets for every card, the accessible equivalent of a swipe.
 *
 * The row sits above the hero card, on the page. While the page is on the accent the shown card
 * *is* that page, so repeating the accent on the selected pill would paint the same colour on
 * itself. The pill therefore keeps the scheme's inner tone in both states — white text either way —
 * and the dot carries the subscription's identity as it always does. On the neutral page the
 * behaviour is unchanged.
 */
@Composable
private fun HomeSubscriptionRow(
    cards: List<HomeCard>,
    shownId: String,
    onSelect: (String) -> Unit,
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
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .semantics { this.contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
    ) {
        items(cards, key = { it.id }) { card ->
            val selected = card.id == shownId
            val accent = Color(accentColor(card.accent, LocalDarkTheme.current))
            val colors = if (selected) {
                FrostSurfaceColors(accent, Color.White)
            } else {
                frostInnerColors()
            }
            FrostSurface(
                colors = colors,
                shape = FrostRadius.pill,
                modifier = Modifier
                    .heightIn(min = FrostSpacing.touchTarget)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(card.id) }),
            ) {
                Row(
                    Modifier.padding(start = FrostSpacing.m, end = FrostSpacing.l, top = FrostSpacing.s, bottom = FrostSpacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
                ) {
                    Box(
                        Modifier
                            .size(DOT_SIZE)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else accent)
                    )
                    Text(
                        card.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The subscription colour dot of a chip: a hint next to the name, never the only channel. */
private val DOT_SIZE = 10.dp

/**
 * "Update", "Account", "Support" under the card; missing data hides the button.
 *
 * These are controls, so they keep their own backgrounds on the accent page: a white-filled action
 * with the scheme's primary as its glyph, which reads on any accent, and the highlighted one takes
 * the role's chip pair. Neither can end up as dark text on a saturated background.
 */
@Composable
private fun HomeActionRow(
    card: HomeCard,
    issue: HomeIssue?,
    updating: Boolean,
    onAccent: Boolean,
    onUpdate: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val actions = homeActions(card)
    if (actions.isEmpty()) return
    val hot = visibleHighlight(card, issue)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s)) {
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
                    {
                        card.info?.webPageUrl?.let(onOpenLink)
                        Unit
                    },
                )
                HomeAction.SUPPORT -> Triple(
                    R.string.home_action_support,
                    if (com.v2ray.ang.handler.SubscriptionInfoParser.isTelegramLink(card.info?.supportUrl)) {
                        R.drawable.ic_telegram_24dp
                    } else {
                        R.drawable.ic_feedback_24dp
                    },
                    {
                        card.info?.supportUrl?.let(onOpenLink)
                        Unit
                    },
                )
            }
            val label = stringResource(labelRes)
            if (action == hot) {
                // The row's own highlight: the primary role's filled pair, which carries its
                // foreground with it in either theme.
                FrostChip(
                    label = label,
                    modifier = Modifier.weight(1f),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    icon = painterResource(iconRes),
                    onClick = onClick,
                    onClickLabel = label,
                )
            } else {
                RowActionButton(
                    label = label,
                    iconRes = iconRes,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    colors = if (onAccent) {
                        // The translucent white plate every other block below the card uses, with
                        // the page's own white foreground. It was a solid white plate with
                        // `colorScheme.primary` as its glyph, and in the dark theme that primary is
                        // a pale blue: 1.70:1 on white, i.e. the label was nearly invisible while
                        // the plate itself glared out of the accent field.
                        FrostSurfaceColors(
                            Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY),
                            ACCENT_ON_PAGE,
                        )
                    } else {
                        frostSecondaryActionColors(enabled = true)
                    },
                )
            }
        }
    }
}

/**
 * One secondary action of the row. It is the same tile as [FrostSecondaryButton], drawn with an
 * explicit pair so the action row can sit on the accent without leaving a scheme colour on a
 * background it was not measured against. The row is the only caller.
 */
@Composable
private fun RowActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: FrostSurfaceColors,
) {
    FrostSurface(
        colors = colors,
        modifier = modifier.defaultMinSize(minHeight = FrostSpacing.touchTarget),
        shape = FrostRadius.tile,
        onClick = onClick,
        onClickLabel = label,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FrostSpacing.touchTarget)
                .padding(horizontal = FrostSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
        ) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A text-only action on a strip. The colour is the strip's own foreground, not a scheme tone: the
 * strip is what knows which background the text was measured against.
 */
@Composable
private fun StripTextButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .defaultMinSize(minHeight = FrostSpacing.touchTarget)
            .clip(FrostRadius.tile)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = label)
            .padding(horizontal = FrostSpacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/**
 * Explains the current issue. When the action already sits in the action row, the row highlights it
 * and the strip only explains; otherwise the strip carries its own button.
 *
 * On the accent page a calm strip becomes the translucent white plate with white text, which is the
 * scheme's 6.00:1 worst case; the warning strip keeps its own chip pair, which was measured against
 * all six accents in both themes.
 */
@Composable
private fun HomeIssueStrip(
    issue: HomeIssue,
    onAccent: Boolean,
    onOtherServer: () -> Unit,
    onAllowVpn: () -> Unit,
) {
    val text = when (issue) {
        is HomeIssue.UpdateFailed -> updateFailedDate(issue)
            ?.let { stringResource(R.string.home_issue_update_failed_on, formatHomeDate(it)) }
            ?: stringResource(R.string.home_issue_update_failed)
        HomeIssue.ServerUnreachable -> stringResource(R.string.home_issue_server)
        HomeIssue.Expired -> stringResource(R.string.home_issue_expired)
        HomeIssue.TrafficExhausted -> stringResource(R.string.home_issue_traffic)
        HomeIssue.VpnPermissionDenied -> stringResource(R.string.home_issue_permission)
    }
    val action: Pair<String, () -> Unit>? = when (issue) {
        HomeIssue.ServerUnreachable -> stringResource(R.string.home_issue_server_action) to onOtherServer
        HomeIssue.VpnPermissionDenied -> stringResource(R.string.home_issue_permission_action) to onAllowVpn
        else -> null
    }
    // A failed update is calm; every other issue takes the warning chip, which brings its own
    // measured background and keeps the tone readable on either theme.
    val warning = LocalStatusColors.current.warning
    val colors = when {
        issue !is HomeIssue.UpdateFailed -> FrostSurfaceColors(warning.chip, warning.onChip)
        onAccent -> FrostSurfaceColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), ACCENT_ON_PAGE)
        else -> frostRaisedColors()
    }
    FrostSurface(colors = colors, shape = FrostRadius.tile) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = FrostSpacing.m, end = FrostSpacing.xs, top = FrostSpacing.xs, bottom = FrostSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = FrostSpacing.s)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            action?.let { (label, onClick) ->
                // The strip's own pair decides the button's colour, so it never falls back to a
                // scheme tone picked for a surface this strip is not.
                StripTextButton(
                    label = label,
                    color = LocalContentColor.current,
                    onClick = onClick,
                )
            }
        }
    }
}

/**
 * Provider message: two lines, expands on tap. Expansion is visual state only, saved per card id.
 * On the accent page it takes the translucent white plate; the card keeps its own pair otherwise.
 */
@Composable
private fun HomeAnnounceStrip(
    cardId: String,
    text: String,
    onAccent: Boolean,
) {
    var expanded by rememberSaveable(cardId) { mutableStateOf(false) }
    val toggleLabel = stringResource(if (expanded) R.string.home_announce_collapse else R.string.home_announce_expand)
    val state = stringResource(if (expanded) R.string.home_announce_expanded else R.string.home_announce_collapsed)
    FrostSurface(
        colors = if (onAccent) {
            FrostSurfaceColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), ACCENT_ON_PAGE)
        } else {
            frostCardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = state },
        onClick = { expanded = !expanded },
        onClickLabel = toggleLabel,
    ) {
        Row(
            Modifier.padding(FrostSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
        ) {
            Icon(
                painterResource(R.drawable.ic_about_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Shown while a switch made during a connection can still be undone.
 *
 * It floats over the bottom edge, where the page behind it is either the neutral background or the
 * accent, so it always paints an opaque plate of its own instead of taking whatever it covers. With
 * the VPN connected that pair is the inverse surface, which is opaque and reads in both themes.
 */
@Composable
private fun HomeUndoBar(
    targetName: String,
    onUndo: () -> Unit,
) {
    FrostSurface(
        colors = FrostSurfaceColors(
            MaterialTheme.colorScheme.inverseSurface,
            MaterialTheme.colorScheme.inverseOnSurface,
        ),
        shape = FrostRadius.tile,
        elevation = com.v2ray.ang.ui.compose.FrostElevation.overlay,
        modifier = Modifier.padding(
            start = FrostSpacing.screenH,
            end = FrostSpacing.screenH,
            bottom = FrostSpacing.s,
        ),
    ) {
        Row(
            Modifier.padding(start = FrostSpacing.l, end = FrostSpacing.xs, top = FrostSpacing.xs, bottom = FrostSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_switching_to, targetName),
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            StripTextButton(
                label = stringResource(R.string.home_undo),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                onClick = onUndo,
            )
        }
    }
}

/** Localized name of a connection state; one source for the top bar, the hero card and TalkBack. */
@StringRes
internal fun homeStatusLabelRes(status: HomeStatus): Int = when (status) {
    HomeStatus.DISCONNECTED -> R.string.home_status_disconnected
    HomeStatus.CONNECTING -> R.string.home_status_connecting
    HomeStatus.SWITCHING -> R.string.home_status_switching
    HomeStatus.CONNECTED -> R.string.home_status_connected
    HomeStatus.FAILED -> R.string.home_status_failed
}
