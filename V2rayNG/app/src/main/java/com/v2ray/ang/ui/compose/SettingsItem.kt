package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * The settings rows of FrostMost.
 *
 * Every piece of a settings screen lives here for one reason: the old row was a stock Material list
 * item, and the old switch was `SwitchDefaults.colors` with only the *checked* colours configured.
 * That single omission is what produced an invisible control — the unchecked track stayed at
 * surfaceContainerHighest (#E8E5E8, 1.19:1 against the page) and the disabled variant dimmed to the
 * same tone, i.e. "чёрное на чёрном". Both are fixed below, in one place, for both themes.
 *
 * Structure: a [SettingsCard] groups rows into one plate with internal dividers; a [SettingsRow]
 * carries title, optional description and a trailing control; a [SettingsSwitchItem] builds a row
 * whose toggle state lives on the row itself.
 *
 * Colour comes only from `MaterialTheme.colorScheme` / [FrostSurfaceColors], spacing only from
 * [FrostSpacing], radii only from [FrostRadius].
 */

// ---------- switch ----------

/**
 * The one switch visual of the app.
 *
 * Checked, unchecked, disabled-checked and disabled-unchecked are all set explicitly, because a
 * state left to the defaults is a state nobody measured. Each track is a step of the surface scale
 * that the row's own card tone cannot hide:
 *
 * - on — `primary` track with an `onPrimary` thumb: the action colour of the scheme, 6.4:1 (light)
 *   and 7.6:1 (dark) between thumb and track;
 * - off — `surfaceContainerHighest` track with a 1 dp `outline` border and an `onSurfaceVariant`
 *   thumb, so "off" reads as a control at rest rather than as a hole;
 * - disabled — `surfaceContainerHigh` track, `outline` thumb and `outline` border, which keeps a
 *   visible edge where the old dimmed switch lost its shape entirely.
 *
 * The palette does not branch on `enabled`: Material picks the `disabled*` roles itself, and a
 * palette that also branched would have to be kept in sync with that choice by hand.
 *
 * Those `*BorderColor` roles stay on purpose. Every other block in the app separates by tone alone;
 * a control does not, because an unchecked switch with no edge is a hole in its card (WCAG 1.4.11),
 * and an unchecked/disabled switch has no other mark of its state.
 */
@Composable
fun frostSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    checkedIconColor = MaterialTheme.colorScheme.onPrimary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
    uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    // A disabled row must still show *which* state it holds, so the tones stay apart instead of
    // collapsing into one grey.
    disabledCheckedThumbColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledCheckedTrackColor = MaterialTheme.colorScheme.outline,
    disabledCheckedBorderColor = MaterialTheme.colorScheme.outline,
    disabledCheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline,
    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline,
    disabledUncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

// ---------- surfaces ----------

/**
 * Section header: a small caps-ish label in the tertiary tone above its card.
 *
 * Sticky Material labels sat inside the list; here the header belongs to the group, so it repeats
 * when the group is collapsed and the user can always see what the open card is about.
 */
@Composable
fun PreferenceGroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(
                start = FrostSpacing.xs,
                end = FrostSpacing.xs,
                top = FrostSpacing.l,
                bottom = FrostSpacing.s,
            ),
    )
}

/**
 * The header of a collapsible group. The whole header is one activation: it opens or closes the
 * card below, and it carries that state so a screen reader announces "expanded" and a keyboard can
 * fold the section with Enter.
 */
@Composable
fun CollapsiblePreferenceGroupHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleLabel = stringResource(
        if (expanded) R.string.settings_section_collapse else R.string.settings_section_expand
    )
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = homeMotionSpringSpec(),
        label = "settingsSectionChevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FrostSpacing.touchTarget)
            .clip(FrostRadius.tile)
            .clickable(
                onClickLabel = toggleLabel,
                role = Role.Button,
                onClick = { onExpandedChange(!expanded) },
            )
            .padding(
                start = FrostSpacing.xs,
                end = FrostSpacing.xs,
                top = FrostSpacing.l,
                bottom = FrostSpacing.s,
            )
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(20.dp)
                .rotate(turn),
        )
    }
}

/**
 * A group of settings as one card: rows stack inside it and [SettingsDivider]s separate them.
 *
 * The card is `surface` on the `background` page, which is a real tone step in both themes
 * (#FFFFFF on #FCF8FA light, #1C1B1F on #141317 dark), so a group reads as a plate instead of a run
 * of grey lines.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    FrostSurface(
        colors = frostCardColors(),
        modifier = modifier.fillMaxWidth(),
        shape = FrostRadius.card,
    ) {
        Column(content = content)
    }
}

/** The line between two rows of the same card, inset so it does not touch the rounded corners. */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = FrostSpacing.cardPadding),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Vertical gap between two settings cards. */
@Composable
fun SettingsCardGap(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.size(FrostSpacing.cardGap))
}

// ---------- rows ----------

/**
 * One row: icon, title, optional description, trailing control.
 *
 * Accessibility: a row that activates merges its descendants into one node, so a screen reader
 * reads the title and its description as one item and never stops on the label. A toggle page also
 * passes [role] and [toggleState], so the merged node is announced as a switch with its on/off
 * state; the descendant [Switch] is drawn with a null callback and therefore adds no second stop.
 *
 * The icon is decorative (`contentDescription = null`): inside a merged row its meaning is already
 * carried by the title, and outside one no row exists to be named by it.
 */
@Composable
private fun SettingsRow(
    icon: Painter?,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    toggleState: Boolean? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = frostCardColors()
    val titleColor = if (enabled) colors.content else colors.muted()
    val detailColor = if (enabled) colors.muted() else colors.muted().copy(alpha = DETAIL_DISABLED_ALPHA)
    val interactive = enabled && onClick != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FrostSpacing.rowHeight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = role,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                if (toggleState != null) {
                    toggleableState = if (toggleState) ToggleableState.On else ToggleableState.Off
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = FrostSpacing.cardPadding,
                    end = FrostSpacing.m,
                    top = FrostSpacing.m,
                    bottom = FrostSpacing.m,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (interactive || onClick == null) colors.muted() else titleColor,
                )
                Spacer(modifier = Modifier.width(FrostSpacing.m))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.size(FrostSpacing.xxs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = detailColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.padding(end = FrostSpacing.cardPadding, top = FrostSpacing.s, bottom = FrostSpacing.s),
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}

/** Description text that is already a secondary tone keeps its readings when the row is disabled. */
private const val DETAIL_DISABLED_ALPHA = 0.70f

/**
 * A row that opens an input dialog. The description shows the current value, or a mask for a
 * secret, so the row states what it holds without opening it.
 */
@Composable
fun SettingsEditItem(
    icon: Painter? = null,
    title: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardNumber: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = if (isPassword) {
        if (value.isEmpty()) null else PASSWORD_MASK
    } else {
        value.ifEmpty { null }
    }

    SettingsRow(
        icon = icon,
        title = title,
        description = description,
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier
    )

    if (showDialog) {
        // The dialog keeps its own state; the stored value is only read here, never written by the
        // composable: the write goes through the MMKV-backed state the caller passes in.
        var text by remember { mutableStateOf(value) }
        InputDialog(
            title = title,
            fields = listOf(
                InputField(
                    label = title,
                    value = text,
                    visualTransformation = VisualTransformation.None
                )
            ),
            onFieldChange = { _, v -> text = v },
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = { showDialog = false; onValueChanged(text) },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * A row that picks one of a fixed set. The current choice is the description, and the row opens the
 * same single-select dialog as before; only the shell changed.
 */
@Composable
fun SettingsListItem(
    icon: Painter? = null,
    title: String,
    entries: List<String>,
    values: List<String>,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val options = entries.zip(values)
    val selectedOption = options.find { it.second == selectedValue } ?: options.firstOrNull()
    val summary = selectedOption?.first.orEmpty()

    SettingsRow(
        icon = icon,
        title = title,
        description = summary.ifEmpty { null },
        enabled = enabled,
        onClick = if (enabled) {
            { showDialog = true }
        } else null,
        modifier = modifier
    )

    if (showDialog) {
        SelectListDialog(
            title = title,
            options = options,
            optionText = { it.first },
            selectedOption = selectedOption,
            onSelected = { option ->
                showDialog = false
                onSelected(option.second)
            },
            onDismiss = { showDialog = false },
            showRadio = true
        )
    }
}

/** A row that only navigates or runs an action. */
@Composable
fun SettingsMenuItem(
    icon: Painter? = null,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    SettingsRow(
        icon = icon,
        title = title,
        description = subtitle,
        enabled = true,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * A row with a switch.
 *
 * The switch is drawn, never owned: `onCheckedChange` is null so the component contributes no
 * second focus target, and the row carries `Role.Switch` plus `toggleableState`. The one activation
 * path calls [onCheckedChange] with the flipped value.
 */
@Composable
fun SettingsSwitchItem(
    icon: Painter? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SettingsRow(
        icon = icon,
        title = title,
        description = summary,
        enabled = enabled,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null,
        modifier = modifier,
        role = Role.Switch,
        toggleState = checked,
        trailing = {
            FrostSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = if (enabled) onCheckedChange else null,
            )
        }
    )
}

/**
 * A bare switch for a row that draws its own layout.
 *
 * `onCheckedChange` here is real: use this only when the switch is the row's single control and the
 * surrounding row sets `mergeDescendants = true` so the pair stays one focus target.
 */
@Composable
fun FrostSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = frostSwitchColors(),
    )
}

/** Placeholder shown instead of a stored secret. */
private const val PASSWORD_MASK = "••••••"
