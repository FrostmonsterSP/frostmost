package com.v2ray.ang.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionFailure
import com.v2ray.ang.handler.ImportKind
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.FrostPrimaryButton
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.util.Utils

/**
 * First run: no subscription and no server. Only a title, the import field with "Paste", the hint and a
 * quiet "Other ways" link. The keyboard never opens by itself (the field is not focused).
 * [failed] is the last failed import; its address is put back into the field with the error and a retry.
 */
@Composable
internal fun HomeFirstRun(
    failed: HomeImportState.Failed?,
    onAction: (MainAction) -> Unit,
    onOtherWays: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(failed?.url) { if (failed != null) text = failed.url }
    val kind = rememberImportKind(text)
    val failure = failed?.takeIf { it.url == text.trim() }?.reason
    val canAdd = kind != null && kind != ImportKind.Invalid
    val context = LocalContext.current
    val add = { if (canAdd) onAction(MainAction.ApplyImport(text)) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        // Scrollable form with the IME inset applied once, after the system bars are consumed.
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(horizontal = FrostSpacing.screenH, vertical = FrostSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(FrostSpacing.s, Alignment.CenterVertically),
            ) {
                Text(
                    stringResource(R.string.home_first_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FrostSpacing.s),
                    label = { Text(stringResource(R.string.home_first_field)) },
                    trailingIcon = {
                        TextButton(onClick = { text = clipboardText(context) }) {
                            Text(stringResource(R.string.home_first_paste))
                        }
                    },
                    isError = kind == ImportKind.Invalid || failure != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                val error = when {
                    kind == ImportKind.Invalid -> R.string.home_import_invalid
                    failure == SubscriptionFailure.DOWNLOAD -> R.string.home_import_download_failed
                    failure == SubscriptionFailure.NO_SERVERS -> R.string.home_import_no_servers
                    else -> null
                }
                if (error != null) {
                    Text(
                        stringResource(error),
                        modifier = Modifier
                            .padding(top = FrostSpacing.xs)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        stringResource(R.string.home_empty_hint),
                        modifier = Modifier.padding(top = FrostSpacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // One filled action at a time: retry when the last address failed, otherwise add.
                if (failure != null) {
                    FrostPrimaryButton(
                        label = stringResource(R.string.home_import_retry),
                        onClick = { onAction(MainAction.RetryImport) },
                        modifier = Modifier.padding(top = FrostSpacing.xs),
                    )
                } else if (canAdd) {
                    FrostPrimaryButton(
                        label = stringResource(R.string.home_add),
                        onClick = add,
                        modifier = Modifier.padding(top = FrostSpacing.xs),
                    )
                }
                FrostTextButton(
                    label = stringResource(R.string.home_add_other),
                    onClick = onOtherWays,
                    modifier = Modifier.padding(top = FrostSpacing.s),
                )
            }
        }
    }
}

/** Clipboard text for the Paste button; blank when empty or unreadable (Utils.getClipboard logs failures). */
private fun clipboardText(context: Context): String =
    Utils.getClipboard(context).takeIf { it != "null" }.orEmpty().trim()

/** Progress while a subscription downloads, instead of an empty home screen. */
@Composable
internal fun HomeDownloading() {
    val label = stringResource(R.string.home_import_downloading)
    Column(
        Modifier
            .fillMaxSize()
            .padding(FrostSpacing.xl)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.l, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Add the subscription «title»?" for a link that arrived while subscriptions exist. */
@Composable
internal fun HomeLinkConfirm(state: HomeImportState.Confirm, onAction: (MainAction) -> Unit) {
    ConfirmDialog(
        message = stringResource(R.string.home_link_confirm, state.title),
        confirmText = stringResource(R.string.home_add),
        onConfirm = { onAction(MainAction.ConfirmLink) },
        onDismiss = { onAction(MainAction.DismissLink) },
    )
}
