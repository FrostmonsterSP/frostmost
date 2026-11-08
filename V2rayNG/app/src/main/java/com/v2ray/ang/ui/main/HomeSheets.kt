package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionFailure
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.ImportClassifier
import com.v2ray.ang.handler.ImportKind
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.FrostPrimaryButton
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.frostInnerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which bottom sheet the home screen shows; null means none. */
internal enum class HomeSheet { Add, More, Ways }

/** Group id the repository uses for "update all subscriptions". */
private const val ALL_SUBSCRIPTIONS_ID = ""

/** The five presets in the order the sheet lists them. */
internal val RULE_PRESETS = listOf(
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
    pendingRules: PendingRules?,
    importDraft: String?,
    importDraftFailure: SubscriptionFailure?,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { onSheetChange(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // A modal sheet is never on the accent: the accent belongs to the hero card, and their
            // fields and menus want the plain theme surface underneath.
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            // Close first, then act: the action may launch another activity.
            val act: (MainAction) -> Unit = { action ->
                onSheetChange(null)
                onAction(action)
            }
            when (sheet) {
                HomeSheet.Add -> AddSheetContent(draft = importDraft, failure = importDraftFailure, onAction = act)
                HomeSheet.Ways -> WaysSheetContent(
                    onAction = act,
                    onNavigate = { destination ->
                        onSheetChange(null)
                        onNavigate(destination)
                    },
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
        modifier = Modifier.padding(start = FrostSpacing.xl, end = FrostSpacing.xl, top = FrostSpacing.s),
        style = MaterialTheme.typography.titleLarge,
    )
}

/**
 * One activation target: the whole row is a single semantic node named by its label. The block is a
 * [FrostSurface], so its background and its text colour always come in one pair.
 */
@Composable
private fun SheetRow(
    label: String,
    iconRes: Int? = null,
    onClick: () -> Unit,
) {
    val labelPaintsItself = iconRes == null
    FrostSurface(
        colors = frostInnerColors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSpacing.l, vertical = FrostSpacing.xxs),
        shape = FrostRadius.tile,
        onClick = onClick,
        onClickLabel = label,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FrostSpacing.touchTarget)
                .padding(horizontal = FrostSpacing.l, vertical = FrostSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.l),
        ) {
            if (iconRes != null) {
                Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (labelPaintsItself) 2 else 1,
            )
        }
    }
}

/** Recognized type of [text], classified off the main thread; null while classifying or when blank. */
@Composable
internal fun rememberImportKind(text: String): ImportKind? {
    val kind by produceState<ImportKind?>(initialValue = null, text) {
        value = if (text.isBlank()) null else withContext(Dispatchers.Default) { ImportClassifier.classify(text) }
    }
    return kind
}

@Composable
private fun AddSheetContent(draft: String?, failure: SubscriptionFailure?, onAction: (MainAction) -> Unit) {
    var text by rememberSaveable(draft) { mutableStateOf(draft.orEmpty()) }
    var showOther by rememberSaveable { mutableStateOf(false) }
    val kind = rememberImportKind(text)
    // The failure belongs to the address that failed; editing the field hides it.
    val shownFailure = failure?.takeIf { text.trim() == draft?.trim() }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = FrostSpacing.l),
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.s),
    ) {
        SheetTitle(R.string.home_add)
        Column(
            Modifier.padding(horizontal = FrostSpacing.l),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.s),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_add_input_label)) },
                isError = kind == ImportKind.Invalid || shownFailure != null,
                maxLines = 4,
            )
            if (shownFailure != null) {
                Text(
                    stringResource(
                        if (shownFailure == SubscriptionFailure.DOWNLOAD) R.string.home_import_download_failed
                        else R.string.home_import_no_servers
                    ),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else kind?.let {
                val invalid = it == ImportKind.Invalid
                Text(
                    stringResource(importKindLabel(it)),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                FrostPrimaryButton(
                    label = stringResource(if (shownFailure != null) R.string.home_import_retry else R.string.home_add),
                    onClick = { onAction(MainAction.ApplyImport(text)) },
                    enabled = kind != null && kind != ImportKind.Invalid,
                )
            }
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
private fun MoreSheetContent(
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    var showOther by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = FrostSpacing.l),
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.xxs),
    ) {
        SheetTitle(R.string.home_more)
        SheetRow(stringResource(R.string.home_update_all)) {
            onAction(MainAction.UpdateGroupSubscription(ALL_SUBSCRIPTIONS_ID))
        }
        HOME_MORE_ITEMS.forEach { destination ->
            SheetRow(stringResource(destination.labelRes), destination.iconRes) { onNavigate(destination) }
        }
        // The Add ways sit under "More" now that the configuration lives on the home screen itself.
        SheetRow(stringResource(R.string.home_add_other)) { showOther = !showOther }
        if (showOther) {
            ImportMenuAction.entries.forEach { item ->
                SheetRow(stringResource(item.labelRes)) { onAction(item.action) }
            }
        }
    }
}

/** First-run "Other ways": QR code, file, backup and manual setup, so nobody with a QR code or an old copy is stuck. */
@Composable
private fun WaysSheetContent(
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = FrostSpacing.l),
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.xxs),
    ) {
        SheetTitle(R.string.home_add_other)
        ImportMenuAction.entries.filter { !it.isManual && it != ImportMenuAction.Clipboard }.forEach { item ->
            SheetRow(stringResource(item.labelRes)) { onAction(item.action) }
        }
        SheetRow(stringResource(MainDestination.BackupRestore.labelRes), MainDestination.BackupRestore.iconRes) {
            onNavigate(MainDestination.BackupRestore)
        }
        SheetRow(stringResource(R.string.home_first_manual)) { showManual = !showManual }
        if (showManual) {
            ImportMenuAction.entries.filter { it.isManual }.forEach { item ->
                SheetRow(stringResource(item.labelRes)) { onAction(item.action) }
            }
        }
    }
}
