package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
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
    fun Show(confirmRemove: Boolean, qrBitmap: android.graphics.Bitmap?) {
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
