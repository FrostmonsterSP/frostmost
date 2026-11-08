package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.QRCodeDialog

/** Confirmations and share dialogs of the server list, the same ones the old main screen used. */
internal class HomeDialogs(private val onAction: (MainAction) -> Unit) {
    var showDelAll by mutableStateOf(false)
    var showDelDuplicate by mutableStateOf(false)
    var showDelInvalid by mutableStateOf(false)
    var removeTarget by mutableStateOf<ServerDeleteTarget?>(null)
    var shareTarget by mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null)

    fun remove(confirm: Boolean, guid: String, name: String) {
        if (confirm) removeTarget = ServerDeleteTarget(guid, name) else onAction(MainAction.RemoveServer(guid))
    }

    fun onMenu(action: MainMoreMenuAction) = when (action) {
        MainMoreMenuAction.RestartService -> onAction(MainAction.RestartService)
        MainMoreMenuAction.DeleteAll -> showDelAll = true
        MainMoreMenuAction.DeleteDuplicate -> showDelDuplicate = true
        MainMoreMenuAction.DeleteInvalid -> showDelInvalid = true
        MainMoreMenuAction.ExportAll -> onAction(MainAction.ExportAll)
        MainMoreMenuAction.LocateSelected -> onAction(MainAction.LocateSelectedServer)
        MainMoreMenuAction.SortByTestResults -> onAction(MainAction.SortByTestResults)
        MainMoreMenuAction.TestAll -> onAction(MainAction.TestAllServers)
        MainMoreMenuAction.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
        MainMoreMenuAction.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
    }

    @Composable
    fun Show(confirmRemove: Boolean, qrBitmap: Bitmap?) {
        MainDialogs(
            showDelAllConfirm = showDelAll,
            onDismissDelAll = { showDelAll = false },
            onConfirmDelAll = { showDelAll = false; onAction(MainAction.RemoveAllServers) },
            showDelDuplicateConfirm = showDelDuplicate,
            onDismissDelDuplicate = { showDelDuplicate = false },
            onConfirmDelDuplicate = { showDelDuplicate = false; onAction(MainAction.RemoveDuplicateServers) },
            showDelInvalidConfirm = showDelInvalid,
            onDismissDelInvalid = { showDelInvalid = false },
            onConfirmDelInvalid = { showDelInvalid = false; onAction(MainAction.RemoveInvalidServers) },
            showRemoveConfirm = removeTarget,
            onDismissRemove = { removeTarget = null },
            onConfirmRemove = { guid -> removeTarget = null; onAction(MainAction.RemoveServer(guid)) },
        )
        shareTarget?.let { (guid, profile, more) ->
            ShareMethodDialog(
                guid = guid,
                profile = profile,
                more = more,
                onDismiss = { shareTarget = null },
                onAction = onAction,
                onRemove = { g, name -> remove(confirmRemove, g, name) },
            )
        }
        if (qrBitmap != null) {
            QRCodeDialog(bitmap = qrBitmap, onDismiss = { onAction(MainAction.DismissQRCodeDialog) })
        }
    }
}

/** The dialogs outlive recompositions; they always call the latest [onAction]. */
@Composable
internal fun rememberHomeDialogs(onAction: (MainAction) -> Unit): HomeDialogs {
    val latest by rememberUpdatedState(onAction)
    return remember { HomeDialogs { action -> latest(action) } }
}
