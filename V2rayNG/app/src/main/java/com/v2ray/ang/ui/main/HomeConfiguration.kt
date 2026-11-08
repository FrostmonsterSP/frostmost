package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.RoutingType

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
    colors: OnBackgroundColors,
    selectedServerName: String?,
    onAction: (MainAction) -> Unit,
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    dialogs: HomeDialogs,
    isRunning: Boolean,
    isTesting: Boolean,
    onAddOwnRules: () -> Unit,
) {
    val listState = rememberLazyListState()
    val serverRows by mainViewModel.serverGroupState(groupId).collectAsStateWithLifecycle()
    val bottomBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomBar + CONFIGURATION_END_GAP),
    ) {
        routingSection(
            preset = preset,
            presetLoaded = presetLoaded,
            available = routingAvailable,
            sectionState = sectionState,
            onSectionChange = onSectionChange,
            colors = colors,
            onAction = onAction,
            onAddOwnRules = onAddOwnRules,
        )
        serversSection(
            rows = serverRows.rows,
            sectionState = sectionState,
            onSectionChange = onSectionChange,
            colors = colors,
            selectedServerName = selectedServerName,
            onAction = onAction,
            uiState = uiState,
            dialogs = dialogs,
            isRunning = isRunning,
            isTesting = isTesting,
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

private fun LazyListScope.routingSection(
    preset: RoutingType?,
    presetLoaded: Boolean,
    available: Boolean,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    colors: OnBackgroundColors,
    onAction: (MainAction) -> Unit,
    onAddOwnRules: () -> Unit,
) {
    item(key = "routing-header") {
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
            colors = colors,
            onClick = { onSectionChange(sectionState.toggle(HomeSection.Routing)) },
        )
    }
    if (available && sectionState.isExpanded(HomeSection.Routing)) {
        routingEntries(preset, presetLoaded).forEach { entry ->
            item(key = "routing-${entry.type?.name ?: "custom"}") {
                RoutingRow(
                    label = stringResource(entry.labelRes),
                    selected = presetLoaded && entry.type == preset,
                    onClick = entry.type?.let { type -> { onAction(MainAction.ApplyRoutingPreset(type)) } },
                )
            }
        }
        item(key = "routing-own") { OwnRulesRow(onClick = onAddOwnRules) }
    }
}

private fun LazyListScope.serversSection(
    rows: List<ServerRowUiModel>,
    sectionState: HomeSectionState,
    onSectionChange: (HomeSectionState) -> Unit,
    colors: OnBackgroundColors,
    selectedServerName: String?,
    onAction: (MainAction) -> Unit,
    uiState: MainUiState,
    dialogs: HomeDialogs,
    isRunning: Boolean,
    isTesting: Boolean,
) {
    item(key = "servers-header") {
        SectionHeader(
            title = stringResource(R.string.home_servers),
            summary = selectedServerName ?: stringResource(R.string.home_server_none),
            expanded = sectionState.isExpanded(HomeSection.Servers),
            enabled = true,
            colors = colors,
            onClick = { onSectionChange(sectionState.toggle(HomeSection.Servers)) },
        )
    }
    // Check, restart and the list menu stay on screen whether or not the list is open: they act on
    // the subscription, not on the visible rows.
    item(key = "servers-tools") {
        ServersSectionTools(
            isRunning = isRunning,
            isTesting = isTesting,
            colors = colors,
            onAction = onAction,
            onMenu = dialogs::onMenu,
        )
    }
    if (!sectionState.isExpanded(HomeSection.Servers)) return
    serverRowItems(
        rows = rows,
        selectedGuid = uiState.selectedGuid,
        confirmRemove = uiState.confirmRemove,
        dialogs = dialogs,
        onAction = onAction,
    )
}

/** Section header: title, current value, and an expansion state TalkBack can read. */
@Composable
private fun SectionHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    enabled: Boolean,
    colors: OnBackgroundColors,
    onClick: () -> Unit,
) {
    val toggleLabel = stringResource(if (expanded) R.string.home_section_collapse else R.string.home_section_expand)
    val expansion = stringResource(
        if (expanded) R.string.home_section_expanded else R.string.home_section_collapsed
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClickLabel = toggleLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = summary + ", " + expansion
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.content)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.content.copy(alpha = SUMMARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // One drawable rotated 180 degrees: the chevron points up when the section is open.
        Icon(
            painter = painterResource(R.drawable.ic_expand_more_24dp),
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.rotate(if (expanded) 180f else 0f),
        )
    }
}

/** The summary line is secondary information; it stays readable but gives way to the title. */
private const val SUMMARY_ALPHA = 0.75f

@Composable
private fun RoutingRow(label: String, selected: Boolean, onClick: (() -> Unit)?) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
    // A preset is a choice and reads as a radio option; "custom rules" is the current state only,
    // so it carries no click and reports itself as a state instead.
    val rowModifier = if (onClick != null) {
        base.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        base.semantics(mergeDescendants = true) { stateDescription = label }
    }
    Row(
        rowModifier.padding(start = 32.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Add your own rules" opens the separate add flow, which stays a modal task of its own. */
@Composable
private fun OwnRulesRow(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
    ) {
        TextButton(onClick = onClick) { Text(stringResource(R.string.home_rules_add_own)) }
    }
}

/**
 * Check, restart and the list menu of the servers section. Both actions are filled tiles so they
 * read as controls rather than loose text, and they remain visible with the list collapsed.
 */
@Composable
private fun ServersSectionTools(
    isRunning: Boolean,
    isTesting: Boolean,
    colors: OnBackgroundColors,
    onAction: (MainAction) -> Unit,
    onMenu: (MainMoreMenuAction) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServerToolButton(
            label = stringResource(if (isTesting) R.string.home_check_stop else R.string.home_check),
            iconRes = if (isTesting) R.drawable.ic_stop_24dp else R.drawable.ic_qu_start_24dp,
            colors = colors,
            onClick = { onAction(if (isTesting) MainAction.CancelTesting else MainAction.TestRealAllServers) },
        )
        ServerToolButton(
            label = stringResource(R.string.home_restart),
            iconRes = R.drawable.ic_refresh_24dp,
            colors = colors,
            enabled = isRunning,
            // TalkBack must know why a disabled restart cannot be pressed; with the VPN off there is
            // nothing running to restart.
            disabledHint = stringResource(R.string.home_restart_needs_vpn),
            onClick = { onAction(MainAction.RestartService) },
        )
        Box(Modifier.weight(1f))
        var menuOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { menuOpen = true }) {
            Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = stringResource(R.string.home_servers_menu))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MoreMenuContent { action ->
                menuOpen = false
                onMenu(action)
            }
        }
    }
}

/** One small, filled tool of the servers section: icon plus label on the section's own surface. */
@Composable
private fun ServerToolButton(
    label: String,
    @DrawableRes iconRes: Int,
    colors: OnBackgroundColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledHint: String? = null,
) {
    val container = if (enabled) colors.container else colors.container.copy(alpha = DISABLED_TILE_ALPHA)
    val content = if (enabled) colors.content else colors.content.copy(alpha = DISABLED_CONTENT_ALPHA)
    Row(
        Modifier
            .clip(SERVER_TOOL_SHAPE)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics {
                if (!enabled && disabledHint != null) stateDescription = disabledHint
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** Filled, softly rounded tools: visible as controls on both the neutral and the accent surface. */
private val SERVER_TOOL_SHAPE = RoundedCornerShape(12.dp)

/** A disabled control still has to be readable, so it dims rather than disappears. */
private const val DISABLED_TILE_ALPHA = 0.45f

/** Its label dims with the tile but keeps the same tone. */
private const val DISABLED_CONTENT_ALPHA = 0.55f
