package com.v2ray.ang.ui.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.ui.compose.FrostChip
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSurfaceColors
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostInnerColors
import com.v2ray.ang.ui.compose.frostRaisedColors
import com.v2ray.ang.ui.compose.HomeMotionEnterSpec

/** Air between the last server row and the bottom of the page. */
private val CONFIGURATION_END_GAP = 16.dp

/** One entry of the routing section: a preset that can be picked, or the fixed "custom rules" state. */
private data class RoutingEntry(val type: RoutingType?, val labelRes: Int)

/**
 * Routing and servers as inline sections of one lazy list, so the configuration area has a single
 * vertical scroll owner. Section expansion is presentation state the caller owns, so collapsing a
 * section never drops the selection, the search query or the VPN state.
 */
@Composable
internal fun HomeConfigurationList(
    groupId: String,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    routingAvailable: Boolean,
    preset: RoutingType?,
    presetLoaded: Boolean,
    selectedServerName: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAction: (MainAction) -> Unit,
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    dialogs: HomeDialogs,
    isTesting: Boolean,
    onAddOwnRules: () -> Unit,
    /**
     * Whether this list owns the vertical scroll of the page ([HomeZonePolicy.Fixed]) or the page
     * itself scrolls ([HomeZonePolicy.ScrollAll]). Only the owner may claim the viewport: a lazy
     * list measured with an infinite maximum height throws, which is exactly what happens when the
     * page scrolls and the list still asks for `fillMaxSize` inside it.
     */
    zonePolicy: HomeZonePolicy,
    /**
     * The screen's one "the page is painted with the subscription accent" flag, passed down rather
     * than derived again: a connected VPN fills the page with the accent, and the sections would
     * otherwise keep drawing the scheme's opaque dark `surface` and read as holes punched in it.
     * When it is false the sections keep exactly the tone they had.
     */
    onAccent: Boolean,
) {
    val serverRows by mainViewModel.serverGroupState(groupId).collectAsStateWithLifecycle()
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (zonePolicy == HomeZonePolicy.ScrollAll) {
        // The page itself scrolls in this mode. A lazy list can never be measured here: it needs a
        // finite maximum height, and `verticalScroll` gives its children an infinite one, which
        // throws "Vertically scrollable component was measured with an infinity maximum height".
        // No modifier makes that legal, so this mode renders the sections as plain content and the
        // page owns the only vertical scroll. The mode is only chosen on a short window, where the
        // server list is the only long block and it is still folded by default.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = FrostSpacing.cardGap, bottom = bottomBar + CONFIGURATION_END_GAP),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap),
        ) {
            RoutingSectionBlock(
                preset = preset,
                presetLoaded = presetLoaded,
                available = routingAvailable,
                sectionState = sectionState,
                onSectionChange = onSectionChange,
                onAction = onAction,
                onAddOwnRules = onAddOwnRules,
                onAccent = onAccent,
            )
            ServersSectionBlock(
                groupId = groupId,
                rows = serverRows.rows,
                sectionState = sectionState,
                onSectionChange = onSectionChange,
                selectedServerName = selectedServerName,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedGuid = uiState.selectedGuid,
                confirmRemove = uiState.confirmRemove,
                dialogs = dialogs,
                onAction = onAction,
                isTesting = isTesting,
                onAccent = onAccent,
            )
        }
        return
    }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = FrostSpacing.screenH,
            end = FrostSpacing.screenH,
            // The gap above the first section belongs to the list, not to the page: the connection
            // area above it has no bottom padding of its own, so without this the first section sat
            // flush against the provider message and the two plates looked fused.
            top = FrostSpacing.cardGap,
            bottom = bottomBar + FrostSpacing.l,
        ),
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap),
    ) {
        routingSection(
            preset = preset,
            presetLoaded = presetLoaded,
            available = routingAvailable,
            sectionState = sectionState,
            onSectionChange = onSectionChange,
            onAction = onAction,
            onAddOwnRules = onAddOwnRules,
            onAccent = onAccent,
        )
        serversSection(
            groupId = groupId,
            rows = serverRows.rows,
            sectionState = sectionState,
            onSectionChange = onSectionChange,
            selectedServerName = selectedServerName,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            selectedGuid = uiState.selectedGuid,
            confirmRemove = uiState.confirmRemove,
            dialogs = dialogs,
            onAction = onAction,
            isTesting = isTesting,
            onAccent = onAccent,
        )
    }
}

/** Routing entries in the order the rules sheet used: the custom state first when it is current. */
private fun routingEntries(preset: RoutingType?, presetLoaded: Boolean): List<RoutingEntry> {
    val entries = mutableListOf<RoutingEntry>()
    // Custom rules match no preset, so there is nothing to pick: shown as the current state only.
    if (presetLoaded && preset == null) entries += RoutingEntry(null, R.string.home_rules_custom)
    RULE_PRESETS.forEach { type -> entries += RoutingEntry(type, routingPresetLabelRes(type)) }
    return entries
}

/**
 * A section and its open rows as one item. The header must sit outside the animated block: a
 * `LazyListScope` cannot wrap items in a composable, so the section animation is expressed as an
 * animated size on a wrapping `Column`. Keys stay stable, so an open section keeps its rows, its
 * selection, its focus and its saved state.
 */
private fun LazyListScope.animatedSection(
    key: String,
    onAccent: Boolean,
    header: @Composable () -> Unit,
    rows: @Composable () -> Unit,
) {
    item(key = key) {
        SectionCard(onAccent = onAccent) {
            header()
            rows()
        }
    }
}

/**
 * The plate a section sits on: every section of the configuration area is one card.
 *
 * Only the card is tinted on the accent page. The header and the rows inside it are drawn with
 * [SectionTone.Transparent], so the section keeps one continuous tone instead of a second, slightly
 * different plate stacked on the first — the "Servers" title used to be darker than its own rows
 * for exactly that reason.
 */
@Composable
private fun SectionCard(onAccent: Boolean, content: @Composable () -> Unit) {
    FrostSurface(
        // `surface` on `background` separates a card from the page by tone, not by a border. On the
        // accent page it takes the translucent white plate of every block below the card.
        colors = sectionPlateColors(onAccent),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = HomeMotionEnterSpec),
    ) {
        Column(Modifier.padding(vertical = FrostSpacing.xs)) { content() }
    }
}

/**
 * How deep inside a section a block is. It decides the tone step: [Plate] is the section's own
 * card, [Inner] a block resting on it (a row, the search field), [Raised] the lifted tone that marks
 * the current choice, and [Transparent] a block that must not add a second plate at all.
 *
 * On the accent page only the section's plate is tinted and everything inside it is transparent —
 * the white overlay is the only separation the page has room for, and stacking it would build a
 * lighter rectangle inside a lighter rectangle. The foregrounds come from the plate's pair, so
 * [SectionTone] is only ever about the container.
 */
private enum class SectionTone { Plate, Inner, Raised, Transparent }

/** The pair of one block of a section, on the accent page and off it. */
@Composable
private fun sectionColors(tone: SectionTone, onAccent: Boolean): FrostSurfaceColors = when {
    onAccent && tone == SectionTone.Plate ->
        FrostSurfaceColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), ACCENT_ON_PAGE)
    onAccent -> FrostSurfaceColors(Color.Transparent, ACCENT_ON_PAGE)
    tone == SectionTone.Plate -> frostCardColors()
    tone == SectionTone.Raised -> frostRaisedColors()
    // Inner and Transparent keep the card's own pair off the accent: the tone separation comes from
    // the scheme's own steps, which is what the neutral page has always shown.
    else -> sectionColors(tone = SectionTone.Plate, onAccent = false)
}

/** The plate of a whole section: the only block of it that is painted. */
@Composable
private fun sectionPlateColors(onAccent: Boolean): FrostSurfaceColors =
    sectionColors(SectionTone.Plate, onAccent)

/**
 * The foreground of a section's text: `on-accent` / `on-accent-muted` on the accent page, the
 * scheme's own pair otherwise. Every label inside a section goes through one of these two, so no
 * text can fall back to an `onSurface` picked for a background the section is not drawn on.
 */
@Composable
private fun sectionContent(onAccent: Boolean): Color =
    if (onAccent) ACCENT_ON_PAGE else MaterialTheme.colorScheme.onSurface

/**
 * The second line of a section: the summary, the field placeholder and the action glyphs. On the
 * accent page it is `on-accent-muted`, the only secondary tone measured against both the accent and
 * the section's plate.
 */
@Composable
private fun sectionMuted(onAccent: Boolean): Color =
    if (onAccent) ACCENT_ON_PAGE_MUTED else MaterialTheme.colorScheme.onSurfaceVariant

private fun LazyListScope.routingSection(
    preset: RoutingType?,
    presetLoaded: Boolean,
    available: Boolean,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    onAction: (MainAction) -> Unit,
    onAddOwnRules: () -> Unit,
    onAccent: Boolean,
) {
    animatedSection(
        key = "routing",
        onAccent = onAccent,
        header = {
            RoutingSectionHeader(
                preset = preset,
                presetLoaded = presetLoaded,
                available = available,
                sectionState = sectionState,
                onSectionChange = onSectionChange,
                onAccent = onAccent,
            )
        },
    ) {
        RoutingSectionRows(
            preset = preset,
            presetLoaded = presetLoaded,
            available = available,
            sectionState = sectionState,
            onAction = onAction,
            onAddOwnRules = onAddOwnRules,
            onAccent = onAccent,
        )
    }
}

private fun LazyListScope.serversSection(
    groupId: String,
    rows: List<ServerRowUiModel>,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    selectedServerName: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedGuid: String?,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
    isTesting: Boolean,
    onAccent: Boolean,
) {
    animatedSection(
        key = "servers",
        onAccent = onAccent,
        header = {
            ServersSectionHeader(
                groupId = groupId,
                sectionState = sectionState,
                onSectionChange = onSectionChange,
                selectedServerName = selectedServerName,
                isTesting = isTesting,
                onAction = onAction,
                onMenu = dialogs::onMenu,
                onAccent = onAccent,
            )
        },
    ) {
        ServersSectionRows(
            rows = rows,
            sectionState = sectionState,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            selectedGuid = selectedGuid,
            confirmRemove = confirmRemove,
            dialogs = dialogs,
            onAction = onAction,
            onAccent = onAccent,
        )
    }
}

/** Routing header; shared by the lazy list and by the page-scrolling layout. */
@Composable
private fun RoutingSectionHeader(
    preset: RoutingType?,
    presetLoaded: Boolean,
    available: Boolean,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    onAccent: Boolean,
) {
    SectionHeader(
        title = stringResource(R.string.home_rules),
        summary = when {
            !available -> stringResource(R.string.home_rules_no_subscription)
            else -> when (routingSummaryKey(preset, presetLoaded)) {
                RoutingSummary.Loading -> stringResource(R.string.home_rules_choose)
                RoutingSummary.Custom -> stringResource(R.string.home_rules_custom)
                RoutingSummary.Preset -> stringResource(routingPresetLabelRes(preset))
            }
        },
        expanded = sectionState.isExpanded(HomeSection.Routing),
        enabled = available,
        onAccent = onAccent,
        onClick = { onSectionChange(sectionState.toggle(HomeSection.Routing)) },
    )
}

/** Routing rows when the section is open; the caller has already decided that it is. */
@Composable
private fun RoutingSectionRows(
    preset: RoutingType?,
    presetLoaded: Boolean,
    available: Boolean,
    sectionState: HomeSectionState,
    onAction: (MainAction) -> Unit,
    onAddOwnRules: () -> Unit,
    onAccent: Boolean,
) {
    if (!available || !sectionState.isExpanded(HomeSection.Routing)) return
    routingEntries(preset, presetLoaded).forEach { entry ->
        RoutingRow(
            label = stringResource(entry.labelRes),
            selected = presetLoaded && entry.type == preset,
            onClick = entry.type?.let { type -> { onAction(MainAction.ApplyRoutingPreset(type)) } },
            onAccent = onAccent,
        )
    }
    OwnRulesRow(onClick = onAddOwnRules, onAccent = onAccent)
}

/**
 * Servers header: the section title with its expansion state, and the two list-level actions that
 * used to sit on a separate tools row. Check and the list menu act on the subscription rather than
 * on the visible rows, so they stay reachable with the section collapsed.
 */
@Composable
private fun ServersSectionHeader(
    groupId: String,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    selectedServerName: String?,
    isTesting: Boolean,
    onAction: (MainAction) -> Unit,
    onMenu: (MainMoreMenuAction) -> Unit,
    onAccent: Boolean,
) {
    SectionHeader(
        title = stringResource(R.string.home_servers),
        summary = selectedServerName ?: stringResource(R.string.home_server_none),
        expanded = sectionState.isExpanded(HomeSection.Servers),
        enabled = true,
        onAccent = onAccent,
        onClick = { onSectionChange(sectionState.toggle(HomeSection.Servers)) },
        actions = {
            // Restart left this row on purpose: it already exists in the list menu, and it starts a
            // stopped service as well as restarting a running one, so the old "needs the VPN on"
            // rule and its separate button were both wrong.
            CheckTestButton(groupId = groupId, isTesting = isTesting, onAction = onAction, onAccent = onAccent)
            ServersListMenuButton(onMenu = onMenu, onAccent = onAccent)
        },
    )
}

/**
 * Server rows when the section is open. The lazy list keys them by GUID; this plain version has no
 * scroll of its own, because the page owns it in that mode.
 */
@Composable
private fun ServersSectionRows(
    rows: List<ServerRowUiModel>,
    sectionState: HomeSectionState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedGuid: String?,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
    onAccent: Boolean,
) {
    if (!sectionState.isExpanded(HomeSection.Servers)) return
    ServerSearchField(query = searchQuery, onQueryChange = onSearchQueryChange, onAccent = onAccent)
    if (rows.isEmpty()) {
        Text(
            stringResource(R.string.home_servers_empty),
            modifier = Modifier.padding(horizontal = FrostSpacing.cardPadding, vertical = FrostSpacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = sectionMuted(onAccent),
        )
        return
    }
    ServerRowList(
        rows = rows,
        selectedGuid = selectedGuid,
        confirmRemove = confirmRemove,
        dialogs = dialogs,
        onAction = onAction,
        onAccent = onAccent,
    )
}

/** Routing and servers of a page-scrolling layout: same content, no lazy list. */
@Composable
private fun RoutingSectionBlock(
    preset: RoutingType?,
    presetLoaded: Boolean,
    available: Boolean,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    onAction: (MainAction) -> Unit,
    onAddOwnRules: () -> Unit,
    onAccent: Boolean,
) {
    SectionCard(onAccent = onAccent) {
        RoutingSectionHeader(preset, presetLoaded, available, sectionState, onSectionChange, onAccent)
        RoutingSectionRows(preset, presetLoaded, available, sectionState, onAction, onAddOwnRules, onAccent)
    }
}

/** Servers of a page-scrolling layout: same content, no lazy list. */
@Composable
private fun ServersSectionBlock(
    groupId: String,
    rows: List<ServerRowUiModel>,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    selectedServerName: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedGuid: String?,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
    isTesting: Boolean,
    onAccent: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap)) {
        SectionCard(onAccent = onAccent) {
            ServersSectionHeader(
                groupId = groupId,
                sectionState = sectionState,
                onSectionChange = onSectionChange,
                selectedServerName = selectedServerName,
                isTesting = isTesting,
                onAction = onAction,
                onMenu = dialogs::onMenu,
                onAccent = onAccent,
            )
            ServersSectionRows(
                rows = rows,
                sectionState = sectionState,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedGuid = selectedGuid,
                confirmRemove = confirmRemove,
                dialogs = dialogs,
                onAction = onAction,
                onAccent = onAccent,
            )
        }
    }
}

/**
 * Section header: title, current value, an expansion state TalkBack can read, and the optional
 * actions of the section.
 *
 * The title block and [actions] are deliberately two semantic nodes. The title is the expand /
 * collapse toggle, the section's own action; a button in [actions] does something else, and merging
 * them would leave TalkBack reading one node for two different actions and would let the toggle
 * swallow the button's tap. Clearing the toggle's descendants also drops the plain-text title, so
 * the name it was read by travels in its `stateDescription` together with the expansion state and
 * the summary. With no chevron left, that state text is the only channel saying whether the
 * section is open, which is why it is mandatory rather than decorative.
 */
@Composable
private fun SectionHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    enabled: Boolean,
    onAccent: Boolean,
    onClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val toggleLabel = stringResource(if (expanded) R.string.home_section_collapse else R.string.home_section_expand)
    val expansion = stringResource(
        if (expanded) R.string.home_section_expanded else R.string.home_section_collapsed
    )
    val name = title + ", " + summary + ", " + expansion
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrostSurface(
            // Transparent, not a plate of its own: the section's card is the header's background.
            // The header used to be drawn with `frostCardColors()` on top of that card, and its
            // `surface` is a step darker than the `surfaceContainerLow` of a row inside it, so the
            // "Servers" title read as a darker strip laid over its own list.
            colors = sectionColors(SectionTone.Transparent, onAccent),
            shape = FrostRadius.tile,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = FrostSpacing.rowHeight)
                .semantics {
                    heading()
                    contentDescription = name
                    stateDescription = expansion
                },
            onClick = onClick,
            onClickLabel = toggleLabel,
            enabled = enabled,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FrostSpacing.rowHeight)
                    .padding(start = FrostSpacing.l, end = FrostSpacing.s, top = FrostSpacing.s, bottom = FrostSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        // A section with nothing to open yet keeps the muted tone; on the accent
                        // page both tones are the page's own, never an `onSurface` chosen for a
                        // background this section is not drawn on.
                        color = if (enabled) sectionContent(onAccent) else sectionMuted(onAccent),
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = sectionMuted(onAccent),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        actions()
    }
}

/**
 * "Check" / "Stop" of the servers section, in the section header where the expansion chevron used
 * to be: an icon button whose state is the label too, so it is never an unlabelled glyph.
 * The group travels with the action, so a tap measures the list on screen.
 */
@Composable
private fun CheckTestButton(
    groupId: String,
    isTesting: Boolean,
    onAction: (MainAction) -> Unit,
    onAccent: Boolean,
) {
    val label = stringResource(if (isTesting) R.string.home_check_stop else R.string.home_check)
    Box(
        Modifier
            .size(FrostSpacing.touchTarget)
            .clip(FrostRadius.chip)
            .clickable(onClickLabel = label, role = Role.Button) {
                onAction(if (isTesting) MainAction.CancelTesting else MainAction.TestGroupServers(groupId))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (isTesting) R.drawable.ic_stop_24dp else R.drawable.ic_qu_start_24dp),
            contentDescription = label,
            tint = sectionMuted(onAccent),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The list menu of the servers section: delete-all, sorting, bulk test and the subscription refresh. */
@Composable
private fun ServersListMenuButton(onMenu: (MainMoreMenuAction) -> Unit, onAccent: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert_24dp),
                contentDescription = stringResource(R.string.home_servers_menu),
                tint = sectionMuted(onAccent),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MoreMenuContent { action ->
                menuOpen = false
                onMenu(action)
            }
        }
    }
}

@Composable
private fun RoutingRow(label: String, selected: Boolean, onClick: (() -> Unit)?, onAccent: Boolean) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = FrostSpacing.touchTarget)
    // A preset is a choice and reads as a radio option; "custom rules" is the current state only,
    // so it carries no click and reports itself as a state instead.
    val rowModifier = if (onClick != null) {
        base.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        base.semantics(mergeDescendants = true) {
            // Nothing to pick here: the value is reported, and the row carries no action.
            this.selected = true
            stateDescription = label
        }
    }
    Row(
        rowModifier.padding(
            start = FrostSpacing.xl,
            end = FrostSpacing.l,
            top = FrostSpacing.xs,
            bottom = FrostSpacing.xs,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
    ) {
        // The mark is a chip rather than a bare RadioButton so the selected mode is named in text
        // as well as outlined, and the row stays one focusable node.
        //
        // The chip keeps its own pair on the accent page, as every status chip does: the unselected
        // one is the white plate the rest of the accented page uses, so an option still reads as a
        // control instead of dissolving into the section behind it.
        val idle = sectionColors(SectionTone.Inner, onAccent)
        FrostChip(
            label = label,
            container = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                idle.container
            },
            content = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                idle.content
            },
        )
    }
}

/** "Add your own rules" opens the separate add flow, which stays a modal task of its own. */
@Composable
private fun OwnRulesRow(onClick: () -> Unit, onAccent: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(
                start = FrostSpacing.xl,
                end = FrostSpacing.l,
                bottom = FrostSpacing.s,
            ),
    ) {
        val label = stringResource(R.string.home_rules_add_own)
        if (onAccent) {
            // `FrostTextButton` paints the scheme's primary, which was chosen for the neutral page
            // and reads at 1.24:1 on the teal accent. Here the button takes the section's own
            // foreground, so it can never be picked against a background it is not drawn on.
            SectionTextButton(label = label, color = sectionContent(onAccent), onClick = onClick)
        } else {
            FrostTextButton(label = label, onClick = onClick)
        }
    }
}

/** A text action coloured by the section it sits in and not by the scheme. */
@Composable
private fun SectionTextButton(label: String, color: Color, onClick: () -> Unit) {
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
 * The search field of the servers section. It shows the list the ViewModel already filters, so it
 * sends every keystroke through [MainAction.Search] and keeps its own copy for the field's text.
 */
@Composable
private fun ServerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onAccent: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = FrostSpacing.cardPadding,
                end = FrostSpacing.cardPadding,
                top = FrostSpacing.xs,
                bottom = FrostSpacing.s,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
    ) {
        SearchTextField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.weight(1f),
            onAccent = onAccent,
        )
    }
}

/**
 * A single-line search input on the section's inner surface. It carries a visible leading glyph and
 * a clear action, so it is discoverable without knowing the icon.
 */
@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAccent: Boolean = false,
) {
    val label = stringResource(R.string.home_search_servers)
    FrostSurface(
        // A field is told apart from the card by its own fill (surfaceContainerLow on surface), the
        // same rule every other block of the app follows now that decorative outlines are gone.
        // On the accent page the section's plate is the field's background — a second, lighter
        // plate inside it would be the only double-toned block on a page that now has one tone.
        colors = if (onAccent) sectionColors(SectionTone.Transparent, true) else frostInnerColors(),
        modifier = modifier,
        shape = FrostRadius.chip,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FrostSpacing.touchTarget)
                .padding(horizontal = FrostSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
        ) {
            Icon(
                painterResource(R.drawable.ic_search_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = sectionMuted(onAccent),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = sectionContent(onAccent),
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = sectionMuted(onAccent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.home_search_clear),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
