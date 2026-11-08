package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.FrostChip
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSurfaceColors
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.LocalStatusColors
import com.v2ray.ang.ui.compose.StatusColors
import com.v2ray.ang.ui.compose.frostInnerColors
import com.v2ray.ang.ui.compose.frostRaisedColors

/**
 * The servers of [groupId] as rows of the configuration section: the section owns no scroll
 * container of its own, so the page keeps a single vertical scroll owner. Rows are keyed by server
 * GUID, never by their position.
 */
@Composable
internal fun ServerRowList(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
    onAccent: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = FrostSpacing.xs)) {
        rows.forEach { row ->
            key(row.guid) {
                ServerRow(
                    row = row,
                    isSelected = row.guid == selectedGuid,
                    confirmRemove = confirmRemove,
                    dialogs = dialogs,
                    onAction = onAction,
                    onAccent = onAccent,
                )
            }
        }
    }
}

/**
 * One server: the name, one calm line of technical detail, and an overflow menu for the rare
 * actions, so a row carries a single focusable node and one activation ("select this server").
 * Deletion stays behind an explicit menu entry and a confirmation, never a row tap.
 */
@Composable
private fun ServerRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
    onAccent: Boolean,
) {
    // Protocol and transport only: the address and port are rarely what a name is chosen by, and
    // one calm line reads better than a technical dump.
    val detail = row.typeDescription
    val selectedLabel = stringResource(R.string.acc_selected_server)
    // The selected row takes the raised neutral tone of its section and states the fact in text, so
    // the mark is not carried by colour alone.
    //
    // Why the neutral tone and not the primary pair: onPrimaryContainer on primaryContainer is
    // 9.99:1 in the light theme as the pair is defined, so the text is safe. The delay chip stops
    // being safe. In the light theme the delay chips (LightStatusSuccess and friends) are pale tints
    // of about 0.93-0.94 luminance: their own text stays at 6.16-7.94:1, but on primaryContainer
    // they sit at about 1.04-1.05:1, so the chip stops reading as a control and its rounded edge
    // disappears (WCAG 1.4.11 asks for 3:1). Measured the same way, the raised neutral
    // (surfaceContainerHigh) is 1.10:1 from the surfaceContainerLow of an idle row in the light
    // theme and 1.19:1 in the dark one, and onSurface on it is 14.47:1 and 11.50:1. That tone
    // separation is why the selection no longer needs a tick, and it never fights a chip.
    //
    // On the accent page a row keeps the section's plate instead of adding its own: the accented
    // page carries exactly one translucent white tone, and painting the overlay per row would build
    // a lighter box inside a lighter box — the very "hole" this section was fixed for. The selection
    // is still stated without colour, because the name goes semi-bold below.
    val colors = when {
        onAccent -> FrostSurfaceColors(Color.Transparent, ACCENT_ON_PAGE)
        isSelected -> frostRaisedColors()
        else -> frostInnerColors()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSpacing.s)
            .heightIn(min = FrostSpacing.rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrostSurface(
            colors = colors,
            shape = FrostRadius.tile,
            modifier = Modifier.weight(1f),
            onClick = { onAction(MainAction.SelectServer(row.guid)) },
            onClickLabel = stringResource(R.string.home_server_select),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = FrostSpacing.m, end = FrostSpacing.xs, top = FrostSpacing.s, bottom = FrostSpacing.s)
                    .semantics(mergeDescendants = true) {
                        selected = isSelected
                        if (isSelected) stateDescription = selectedLabel
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.remarks,
                        style = MaterialTheme.typography.bodyLarge,
                        // The tick is gone, so the name itself carries the mark for a reader who
                        // scans the list rather than hears it: the weight changes, the size does not.
                        fontWeight = if (isSelected) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (onAccent) {
                                ACCENT_ON_PAGE_MUTED
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // The delay is a chip that brings its own background: a saturated status tone as
                // bare text is unreadable on some surfaces, while a chip carries its own pair.
                val tone = serverLatencyTone(row.testDelayMillis)
                if (tone != ServerLatencyTone.Unknown) {
                    val chip = latencyChip(tone)
                    FrostChip(
                        label = latencyLabel(row.testDelayMillis),
                        container = chip.chip,
                        content = chip.onChip,
                    )
                }
            }
        }
        ServerRowMenu(
            row = row,
            confirmRemove = confirmRemove,
            dialogs = dialogs,
            onAction = onAction,
            onAccent = onAccent,
        )
    }
}

/** The rare, destructive-ish actions of a server: share, edit and delete, never a row tap. */
@Composable
private fun ServerRowMenu(
    row: ServerRowUiModel,
    confirmRemove: Boolean,
    dialogs: HomeDialogs,
    onAction: (MainAction) -> Unit,
    onAccent: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert_24dp),
                contentDescription = stringResource(R.string.home_server_actions_named, row.remarks),
                tint = if (onAccent) ACCENT_ON_PAGE_MUTED else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_configuration_share)) },
                onClick = {
                    menuOpen = false
                    dialogs.shareTarget = Triple(row.guid, row.profile, false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.acc_edit)) },
                onClick = {
                    menuOpen = false
                    onAction(MainAction.EditServer(row.guid, row.profile))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.acc_delete)) },
                onClick = {
                    menuOpen = false
                    dialogs.remove(confirmRemove, row.guid, row.remarks)
                },
            )
        }
    }
}

/** The measured delay as a short, localized label. */
@Composable
private fun latencyLabel(delayMillis: Long): String =
    stringResource(R.string.server_test_delay_value, delayMillis)

/**
 * The delay's own traffic light. Every tone is an opaque chip with its own foreground, contrast
 * checked against the surface the row sits on, so a value never fades into the page.
 */
@Composable
private fun latencyChip(tone: ServerLatencyTone): StatusColors {
    val status = LocalStatusColors.current
    return when (tone) {
        ServerLatencyTone.Fast -> status.success
        ServerLatencyTone.Medium -> status.warning
        ServerLatencyTone.Slow -> status.danger
        ServerLatencyTone.Unknown -> status.info
    }
}
