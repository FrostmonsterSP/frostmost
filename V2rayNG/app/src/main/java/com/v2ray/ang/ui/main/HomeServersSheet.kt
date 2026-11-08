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

/** Air between the last server row and the bar that covers the bottom of the list. */
private val LIST_END_GAP = 16.dp

/**
 * Server drawer: the collapsed header shows the current server with "Check" and "Restart"; pulled
 * up it holds the existing server list of the selected group and its list menu (sort, locate,
 * delete, export), so every server-row function stays as it was.
 */
@Composable
internal fun ServersSheet(
    mainViewModel: MainViewModel,
    groupId: String,
    selectedGuid: String?,
    serverName: String?,
    open: Boolean,
    isRunning: Boolean,
    isTesting: Boolean,
    uiState: MainUiState,
    dialogs: HomeDialogs,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAction: (MainAction) -> Unit,
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Hiding the field always drops the filter, so the list never stays filtered by an invisible query.
    val closeSearch = {
        query = ""
        onAction(MainAction.Search(""))
        showSearch = false
    }
    // After process death the field comes back but the ViewModel filter does not: send it again.
    // (With a surviving ViewModel the same query is ignored by filterConfig.)
    LaunchedEffect(Unit) { if (query.isNotEmpty()) onAction(MainAction.Search(query)) }
    // The filter is global across cached groups, so a query typed for one subscription must not
    // survive a switch to another: drop it with the group that owned it.
    LaunchedEffect(groupId) { if (query.isNotEmpty()) closeSearch() }
    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }
    // The bottom of the sheet is under two layers this list does not size itself: the bars the
    // Column below consumes on the window (navigation bar, or the IME while the search field is
    // open) and the collapsed header that stays on screen on top of the list. Measure both and pad
    // the list by them, so its last row clears both however far the sheet is dragged and whether or
    // not a keyboard is up; the list then needs no system-bar padding of its own.
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var sheetSize by remember { mutableStateOf(IntSize.Zero) }
    val bottomBarsPx = with(density) {
        WindowInsets.safeDrawing
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
            .roundToPx()
    }
    // Ceiling at the sheet height: with the IME open on an expanded sheet the consumed bars can be
    // taller than the sheet, and content padding larger than that would push the list out of view.
    val listBottomPx = (headerHeightPx + bottomBarsPx).coerceIn(0, sheetSize.height)
    val listBottom = with(density) { listBottomPx.toDp() }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .imePadding()
            .onSizeChanged { sheetSize = it }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    headerHeightPx = it.height
                    onHeaderHeight(it.height)
                }
        ) {
            val toggleLabel = stringResource(if (open) R.string.home_servers_hide else R.string.home_servers_show)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                )
            }
            Row(
                Modifier.padding(start = 6.dp, end = 2.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.home_servers), style = MaterialTheme.typography.titleSmall)
                    Text(
                        serverName ?: stringResource(R.string.home_server_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { onAction(if (isTesting) MainAction.CancelTesting else MainAction.TestRealAllServers) }) {
                    Text(stringResource(if (isTesting) R.string.home_check_stop else R.string.home_check))
                }
                TextButton(enabled = isRunning, onClick = { onAction(MainAction.RestartService) }) {
                    Text(stringResource(R.string.home_restart))
                }
                IconButton(onClick = { if (showSearch) closeSearch() else { showSearch = true; onExpand() } }) {
                    Icon(painterResource(R.drawable.ic_search_24dp), contentDescription = stringResource(R.string.acc_search))
                }
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert_24dp), contentDescription = stringResource(R.string.home_servers_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MoreMenuContent { action ->
                            menuOpen = false
                            dialogs.onMenu(action)
                        }
                    }
                }
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onAction(MainAction.Search(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.menu_item_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    IconButton(onClick = closeSearch) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = stringResource(R.string.acc_back))
                    }
                },
            )
        }
        // Collapsed, the list sits below the screen edge: keep it out of TalkBack and D-pad focus.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .let { if (open) it else it.clearAndSetSemantics {} }
                .focusProperties { onEnter = { if (!open) cancelFocusChange() } }
                .focusGroup()
        ) {
            run {
                GroupPagerPage(
                    groupId = groupId,
                    mainViewModel = mainViewModel,
                    selectedGuid = selectedGuid,
                    locateTarget = uiState.locateTarget,
                    doubleColumnDisplay = uiState.doubleColumnDisplay,
                    searchQuery = query,
                    lazyListStates = lazyListStates,
                    lazyGridStates = lazyGridStates,
                    onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                    onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                    onShareServer = { guid, profile -> dialogs.shareTarget = Triple(guid, profile, false) },
                    onMoreServer = { guid, profile -> dialogs.shareTarget = Triple(guid, profile, true) },
                    onRemoveServer = { guid, name -> dialogs.remove(uiState.confirmRemove, guid, name) },
                    usageInfo = null,
                    groupTitle = "",
                    onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                    contentPadding = PaddingValues(bottom = listBottom + LIST_END_GAP),
                )
            }
        }
    }
}
