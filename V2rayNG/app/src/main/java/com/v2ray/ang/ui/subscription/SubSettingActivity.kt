package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostPageColors
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SubscriptionShareAction(@StringRes val labelRes: Int) {
    QRCode(R.string.share_subscription_qrcode),
    Clipboard(R.string.share_subscription_clipboard)
}

private data class SubscriptionDeleteTarget(val guid: String, val name: String)

class SubSettingActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = { subId ->
                startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
            },
            onRemoveSub = { subId -> removeSub(subId) },
            onShareQRCode = viewModel::shareQRCode,
            onShareClipboard = { url ->
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun removeSub(subId: String) {
        viewModel.remove(subId)
    }
}

@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> Unit,
    onShareClipboard: (String) -> Unit
) {
    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SubscriptionDeleteTarget?>(null) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val qrCodeBitmap by viewModel.qrCode.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.index, to.index)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = frostPageColors().container,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_sub_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add_subscription))
                    }
                    IconButton(onClick = { showUpdateDialog = true }) {
                        Icon(painterResource(R.drawable.ic_restore_24dp), contentDescription = stringResource(R.string.acc_update_subscriptions))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(lazyListState),
            contentPadding = PaddingValues(
                start = FrostSpacing.s,
                end = FrostSpacing.s,
                top = FrostSpacing.s,
                bottom = innerPadding.calculateBottomPadding() + FrostSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.xxs),
        ) {
            itemsIndexed(
                items = subscriptions,
                key = { _, item -> item.guid }
            ) { _, subCache ->
                ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        SubscriptionRow(
                            item = subCache,
                            onShare = { shareTarget = Pair(subCache.guid, subCache.subscription.url) },
                            onEdit = { onEditSub(subCache.guid) },
                            onRemove = {
                                if (confirmRemove) {
                                    removeTarget = SubscriptionDeleteTarget(
                                        subCache.guid,
                                        subCache.subscription.remarks,
                                    )
                                } else {
                                    onRemoveSub(subCache.guid)
                                }
                            },
                            onEnabledChange = { checked ->
                                val updated = subCache.subscription.copy()
                                updated.enabled = checked
                                viewModel.update(subCache.guid, updated)
                            }
                        )
                    }
                }
            }
        }
    }

    if (shareTarget != null) {
        val (_, url) = shareTarget!!
        SelectListDialog(
            options = SubscriptionShareAction.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { action ->
                shareTarget = null
                when (action) {
                    SubscriptionShareAction.QRCode -> onShareQRCode(url)
                    SubscriptionShareAction.Clipboard -> onShareClipboard(url)
                }
            },
            onDismiss = { shareTarget = null }
        )
    }

    // QR Code Dialog
    if (qrCodeBitmap != null) {
        QRCodeDialog(
            bitmap = qrCodeBitmap,
            onDismiss = viewModel::dismissQRCode
        )
    }

    removeTarget?.let { target ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            itemName = target.name,
            onConfirm = {
                removeTarget = null
                onRemoveSub(target.guid)
            },
            onDismiss = { removeTarget = null }
        )
    }

    if (showUpdateDialog) {

        var updateSubscription by rememberMmkvBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        var autoTestAfterUpdateSubscription by rememberMmkvBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
        var autoRemoveInvalidAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        var autoSortAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)

        val card = frostCardColors()
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.title_sub_update),
                    style = MaterialTheme.typography.titleLarge,
                    color = card.content,
                )
            },
            text = {
                Column {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_sub_update),
                        checked = updateSubscription,
                        onCheckedChange = { updateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                        summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                        checked = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoTestAfterUpdateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                        summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                        checked = autoRemoveInvalidAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoRemoveInvalidAfterTest = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_sort_after_test),
                        summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                        checked = autoSortAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoSortAfterTest = it }
                    )
                }
            },
            confirmButton = {
                FrostTextButton(
                    label = stringResource(R.string.action_ok),
                    onClick = {
                        showUpdateDialog = false
                        onSubUpdate()
                    }
                )
            },
            dismissButton = {
                FrostTextButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { showUpdateDialog = false }
                )
            },
            containerColor = card.container,
            textContentColor = card.content,
            titleContentColor = card.content,
            shape = FrostRadius.card,
            tonalElevation = 0.dp,
        )
    }
}

/**
 * One subscription: name, link, last update, and the three actions on the row.
 *
 * The three icons are separate focus targets with their own names ("Share subscription X"), and the
 * switch is a fourth; the text block is one merged node, so the row does not read as six fragments.
 */
@Composable
private fun SubscriptionRow(
    item: SubscriptionCache,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val remarks = item.subscription.remarks
    val url = item.subscription.url
    val shareSubscriptionLabel = stringResource(R.string.acc_share_subscription_named, remarks)
    val editSubscriptionLabel = stringResource(R.string.acc_edit_subscription_named, remarks)
    val deleteSubscriptionLabel = stringResource(R.string.acc_delete_subscription_named, remarks)
    val toggleSubscriptionLabel = stringResource(R.string.acc_toggle_subscription_named, remarks)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = FrostSpacing.l,
                end = FrostSpacing.s,
                top = FrostSpacing.m,
                bottom = FrostSpacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = remarks,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (url.isNotEmpty()) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = Utils.formatTimestamp(item.subscription.lastUpdated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (url.isNotEmpty()) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.semantics {
                        contentDescription = shareSubscriptionLabel
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share_24dp),
                        contentDescription = null
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editSubscriptionLabel }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = null
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.semantics { contentDescription = deleteSubscriptionLabel }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = null
                )
            }
        }
        Spacer(modifier = Modifier.width(FrostSpacing.xs))
        Switch(
            checked = item.subscription.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.semantics { contentDescription = toggleSubscriptionLabel },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                // Without these the unchecked track is surfaceContainerHighest, which is 1.19:1
                // against the card: an "off" switch looked like a hole in the card.
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            )
        )
    }
}
