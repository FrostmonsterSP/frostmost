package com.v2ray.ang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem

/**
 * The servers of [groupId] as rows of the configuration list: the section owns no scroll container
 * of its own, so the page keeps a single vertical scroll owner. Rows are keyed by server GUID.
 */
internal fun LazyListScope.serverRowItems(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
) {
    if (rows.isEmpty()) {
        item(key = "servers-empty") {
            Text(
                stringResource(R.string.home_servers_empty),
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    items(items = rows, key = { row -> row.guid }) { row ->
        HomeServerRow(
            row = row,
            isSelected = row.guid == selectedGuid,
            onSelect = { onAction(MainAction.SelectServer(row.guid)) },
            onShare = { dialogs.shareTarget = Triple(row.guid, row.profile, false) },
            onEdit = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
            onRemove = { guid, name -> dialogs.remove(confirmRemove, guid, name) },
        )
    }
}

/**
 * One server: the name, one calm line of technical detail, and an overflow menu for the rare
 * actions, so a row carries a single focusable node and one activation ("select this server").
 * Deletion stays behind an explicit menu entry and a confirmation, never a row tap.
 */
@Composable
private fun HomeServerRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onEdit: (String, ProfileItem) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    // Protocol and transport only: the address and port are rarely what a name is chosen by, and
    // one calm line reads better than a technical dump.
    val detail = row.typeDescription
    val latency = latencyLabel(row.testDelayMillis)
    val selectedLabel = stringResource(R.string.acc_selected_server)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = stringResource(R.string.home_server_select), role = Role.Button, onClick = onSelect)
            .padding(start = 32.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                if (isSelected) stateDescription = selectedLabel
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // An explicit content colour: on the accent background the inherited one washed the
            // names into the surface, and every row looked the same.
            Text(
                row.remarks,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            latency,
            style = MaterialTheme.typography.labelLarge,
            color = latencyColor(serverLatencyTone(row.testDelayMillis)),
            maxLines = 1,
        )
        if (isSelected) {
            Icon(
                painterResource(R.drawable.ic_action_done),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert_24dp),
                    contentDescription = stringResource(R.string.home_server_actions_named, row.remarks),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.title_configuration_share)) },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.acc_edit)) },
                    onClick = { menuOpen = false; onEdit(row.guid, row.profile) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.acc_delete)) },
                    onClick = { menuOpen = false; onRemove(row.guid, row.remarks) },
                )
            }
        }
    }
}

/** The measured delay as a short, localized label; an unmeasured server says so instead of hiding. */
@Composable
private fun latencyLabel(delayMillis: Long): String =
    if (delayMillis <= 0L) stringResource(R.string.home_server_ping_unknown)
    else stringResource(R.string.server_test_delay_value, delayMillis)

/**
 * The delay's own traffic light. Both tones are opaque and contrast-checked against the neutral
 * surface the row sits on, so a value never fades into the page.
 */
@Composable
private fun latencyColor(tone: ServerLatencyTone) = when (tone) {
    ServerLatencyTone.Fast -> PING_FAST_COLOR
    ServerLatencyTone.Medium -> PING_MEDIUM_COLOR
    ServerLatencyTone.Slow -> PING_SLOW_COLOR
    ServerLatencyTone.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Fast enough to be worth showing as good; fixed so the tone survives dynamic color. */
private val PING_FAST_COLOR = Color(0xFF0A7455)

/** Usable but not great. */
private val PING_MEDIUM_COLOR = Color(0xFF8F4C08)

/** Slow, or a measurement that failed. */
private val PING_SLOW_COLOR = Color(0xFFB3261E)
