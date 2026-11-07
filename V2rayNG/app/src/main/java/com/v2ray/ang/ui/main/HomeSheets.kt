package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.ImportClassifier
import com.v2ray.ang.handler.ImportKind
import com.v2ray.ang.ui.compose.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which bottom sheet the home screen shows; null means none. */
internal enum class HomeSheet { Add, Rules, More }

/** Group id the repository uses for "update all subscriptions". */
private const val ALL_SUBSCRIPTIONS_ID = ""

/** The five presets in the order the sheet lists them. */
private val RULE_PRESETS = listOf(
    RoutingType.GLOBAL,
    RoutingType.WHITE_RUSSIA,
    RoutingType.WHITE_IRAN,
    RoutingType.WHITE,
    RoutingType.BLACK,
)

/** The three modal sheets and the rules-replacement confirmation of the home screen. */
// ModalBottomSheet and rememberModalBottomSheetState are @ExperimentalMaterial3Api; the spec wants every
// window to open from the bottom and there is no stable M3 modal bottom sheet. Nothing experimental
// leaves this function. Reevaluate (drop the opt-in) when the pinned compose BOM marks ModalBottomSheet stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeSheetHost(
    sheet: HomeSheet?,
    onSheetChange: (HomeSheet?) -> Unit,
    routingPreset: RoutingType?,
    routingPresetLoaded: Boolean,
    pendingRules: PendingRules?,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { onSheetChange(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            // Close first, then act: the action may launch another activity.
            val act: (MainAction) -> Unit = { action ->
                onSheetChange(null)
                onAction(action)
            }
            when (sheet) {
                HomeSheet.Add -> AddSheetContent(onAction = act)
                HomeSheet.Rules -> RulesSheetContent(
                    preset = routingPreset,
                    presetLoaded = routingPresetLoaded,
                    onAction = act,
                    onAddOwn = { onSheetChange(HomeSheet.Add) },
                )
                HomeSheet.More -> MoreSheetContent(
                    onAction = act,
                    onNavigate = { destination ->
                        onSheetChange(null)
                        onNavigate(destination)
                    },
                )
            }
        }
    }
    if (pendingRules != null) {
        ConfirmDialog(
            message = stringResource(R.string.routing_settings_import_rulesets_tip),
            onConfirm = { onAction(MainAction.ConfirmRules) },
            onDismiss = { onAction(MainAction.DismissRules) },
        )
    }
}

@Composable
private fun SheetTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/** One activation target: the whole row is a single semantic node named by [label]. */
@Composable
private fun SheetRow(label: String, iconRes: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Recognized type of [text], classified off the main thread; null while classifying or when blank. */
@Composable
private fun rememberImportKind(text: String): ImportKind? {
    val kind by produceState<ImportKind?>(initialValue = null, text) {
        value = if (text.isBlank()) null else withContext(Dispatchers.Default) { ImportClassifier.classify(text) }
    }
    return kind
}

@Composable
private fun AddSheetContent(onAction: (MainAction) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var showOther by rememberSaveable { mutableStateOf(false) }
    val kind = rememberImportKind(text)
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 16.dp),
    ) {
        SheetTitle(R.string.home_add)
        Column(
            Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_add_input_label)) },
                isError = kind == ImportKind.Invalid,
                maxLines = 4,
            )
            kind?.let {
                val invalid = it == ImportKind.Invalid
                Text(
                    stringResource(importKindLabel(it)),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onAction(MainAction.ApplyImport(text)) },
                enabled = kind != null && kind != ImportKind.Invalid,
                modifier = Modifier.align(Alignment.End),
            ) { Text(stringResource(R.string.home_add)) }
        }
        ImportMenuAction.entries.filter { !it.isManual }.forEach { item ->
            SheetRow(stringResource(item.labelRes)) { onAction(item.action) }
        }
        SheetRow(stringResource(R.string.home_add_other)) { showOther = !showOther }
        if (showOther) {
            ImportMenuAction.entries.filter { it.isManual }.forEach { item ->
                SheetRow(stringResource(item.labelRes)) { onAction(item.action) }
            }
        }
    }
}

@StringRes
private fun importKindLabel(kind: ImportKind): Int = when (kind) {
    is ImportKind.Subscription -> R.string.home_add_kind_subscription
    ImportKind.Config -> R.string.home_add_kind_config
    ImportKind.Routing -> R.string.home_add_kind_routing
    ImportKind.Invalid -> R.string.home_add_kind_invalid
}

@Composable
private fun RulesSheetContent(
    preset: RoutingType?,
    presetLoaded: Boolean,
    onAction: (MainAction) -> Unit,
    onAddOwn: () -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        SheetTitle(R.string.home_rules)
        // Custom rules match no preset, so there is nothing to pick: shown as the current state only.
        if (presetLoaded && preset == null) {
            RuleRow(label = stringResource(R.string.home_rules_custom), selected = true, onClick = null)
        }
        RULE_PRESETS.forEach { type ->
            RuleRow(
                label = stringResource(routingPresetLabelRes(type)),
                selected = presetLoaded && preset == type,
                onClick = { onAction(MainAction.ApplyRoutingPreset(type)) },
            )
        }
        SheetRow(stringResource(R.string.home_rules_add_own), onClick = onAddOwn)
    }
}

@Composable
private fun RuleRow(label: String, selected: Boolean, onClick: (() -> Unit)?) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
    Row(
        (if (onClick != null) base.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        else base.semantics(mergeDescendants = true) { stateDescription = label })
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MoreSheetContent(
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        SheetTitle(R.string.home_more)
        SheetRow(stringResource(R.string.home_update_all)) {
            onAction(MainAction.UpdateGroupSubscription(ALL_SUBSCRIPTIONS_ID))
        }
        HOME_MORE_ITEMS.forEach { destination ->
            SheetRow(stringResource(destination.labelRes), destination.iconRes) { onNavigate(destination) }
        }
    }
}
