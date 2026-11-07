package com.v2ray.ang.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.QRCodeDialog
import kotlinx.coroutines.launch

/** Rare destinations reachable from "More"; Promotion is removed in the fork. */
private val MORE_DESTINATIONS = listOf(
    MainDestination.Subscriptions,
    MainDestination.PerAppProxy,
    MainDestination.Routing,
    MainDestination.UserAssets,
    MainDestination.Settings,
    MainDestination.Logcat,
    MainDestination.CheckUpdate,
    MainDestination.BackupRestore,
    MainDestination.About,
)

/** Group id the repository uses for "update all subscriptions". */
private const val ALL_SUBSCRIPTIONS_ID = ""

/**
 * Home screen of the fork: which subscription, whether the VPN is on, and how to turn it on.
 * Durable state comes from [MainViewModel]; only expansion, menus and the search field are local.
 */
// BottomSheetScaffold, its state and SheetValue are @ExperimentalMaterial3Api; the plan requires this
// drawer and there is no stable M3 standard bottom sheet. Nothing experimental leaves this function.
// Reevaluate (drop the opt-in) when the pinned compose BOM marks BottomSheetScaffold stable.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onEditSubscription: (String) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val cards = remember(uiState.groups, uiState.subscriptionUrls) {
        buildHomeCards(uiState.groups, uiState.subscriptionUrls, System.currentTimeMillis())
    }
    val shownId = displayedCardId(cards, uiState.selectedGroupId)
    val card = cards.firstOrNull { it.id == shownId }
    val lit = uiState.isRunning && card != null
    val issue = card?.let { resolveHomeIssue(it, uiState.homeIssue) }

    val selectedRows by remember(uiState.selectedGroupId) {
        mainViewModel.serverGroupState(uiState.selectedGroupId)
    }.collectAsStateWithLifecycle()
    val serverName = selectedRows.rows.firstOrNull { it.guid == uiState.selectedGuid }?.remarks
    val delay = (uiState.status as? MainStatus.ConnectionTest)?.result?.delayMillis
    val connection = HomeConnection(
        status = homeStatus(uiState.isRunning, uiState.isConnecting, uiState.undoTargetId != null, uiState.homeIssue),
        serverName = serverName,
        delayMillis = delay,
        isRunning = uiState.isRunning,
        powerEnabled = uiState.isRunning || !uiState.isConnecting,
    )

    val accent = card?.accentColor() ?: MaterialTheme.colorScheme.primary
    val background by animateColorAsState(
        if (lit) accent else MaterialTheme.colorScheme.background,
        label = "homeBackground",
    )
    val onBackground = if (lit) Color.White else MaterialTheme.colorScheme.onBackground
    LightStatusBarIcons(onAccent = lit)

    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetState = scaffoldState.bottomSheetState
    val scope = rememberCoroutineScope()
    val sheetOpen = sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded
    BackHandler(enabled = sheetOpen) { scope.launch { sheetState.partialExpand() } }

    val dialogs = rememberHomeDialogs(onAction)
    dialogs.Show(confirmRemove = uiState.confirmRemove, qrBitmap = uiState.shareQRCodeBitmap)

    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var headerHeight by remember { mutableStateOf(96.dp) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = headerHeight + navBottom,
        sheetDragHandle = null,
        containerColor = background,
        sheetContent = {
            ServersSheet(
                mainViewModel = mainViewModel,
                groupId = uiState.selectedGroupId,
                selectedGuid = uiState.selectedGuid,
                serverName = serverName,
                open = sheetOpen,
                isRunning = uiState.isRunning,
                isTesting = uiState.isTesting,
                uiState = uiState,
                dialogs = dialogs,
                onToggle = {
                    scope.launch { if (sheetOpen) sheetState.partialExpand() else sheetState.expand() }
                },
                onExpand = { scope.launch { sheetState.expand() } },
                onHeaderHeight = { px -> headerHeight = with(density) { px.toDp() } },
                onAction = onAction,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(bottom = innerPadding.calculateBottomPadding() + 16.dp),
            ) {
                HomeTopBar(
                    contentColor = onBackground,
                    lit = lit,
                    onAction = onAction,
                    onNavigate = onNavigate,
                )
                if (card == null) {
                    HomeEmpty(contentColor = onBackground, onAction = onAction)
                } else {
                    if (cards.size > 1) {
                        HomeSubscriptionRow(
                            cards = cards,
                            shownId = card.id,
                            lit = lit,
                            onSelect = { id -> if (id != uiState.selectedGroupId) onAction(MainAction.SwitchSubscription(id)) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    HomeCardPager(
                        cards = cards,
                        shownId = card.id,
                        connection = connection,
                        lit = lit,
                        onSwitch = { id -> if (id != uiState.selectedGroupId) onAction(MainAction.SwitchSubscription(id)) },
                        onPower = { onAction(MainAction.ToggleService) },
                        onConfigure = onEditSubscription,
                    )
                    val stripModifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    HomeActionRow(
                        card = card,
                        issue = issue,
                        lit = lit,
                        updating = isLoading,
                        onUpdate = { onAction(MainAction.UpdateGroupSubscription(card.id)) },
                        onOpenLink = { url -> onAction(MainAction.OpenSubscriptionLink(url)) },
                        modifier = stripModifier,
                    )
                    issue?.let {
                        HomeIssueStrip(
                            issue = it,
                            lit = lit,
                            onOtherServer = { scope.launch { sheetState.expand() } },
                            onAllowVpn = { onAction(MainAction.ToggleService) },
                            modifier = stripModifier,
                        )
                    }
                    card.announce?.takeIf { it.isNotBlank() }?.let { text ->
                        HomeAnnounceStrip(cardId = card.id, text = text, lit = lit, modifier = stripModifier)
                    }
                }
                HomeRulesRow(
                    presetLoaded = uiState.routingPresetLoaded,
                    preset = uiState.routingPreset,
                    lit = lit,
                    onClick = { onNavigate(MainDestination.Routing) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            val undoName = uiState.undoTargetId?.let { id -> cards.firstOrNull { it.id == id }?.name }
            if (undoName != null) {
                HomeUndoBar(
                    targetName = undoName,
                    onUndo = { onAction(MainAction.UndoSwitch) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = innerPadding.calculateBottomPadding() + 8.dp),
                )
            }
        }
    }
}

/**
 * Status-bar icons are white on the subscription color; elsewhere they follow the theme, as
 * [com.v2ray.ang.ui.compose.AppTheme] sets them. Edge-to-edge itself stays with the base activity.
 */
@Composable
private fun LightStatusBarIcons(onAccent: Boolean) {
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    val latestDark by rememberUpdatedState(dark)
    // SideEffect, not an effect keyed once: AppTheme resets the icons in its own SideEffect, and a
    // child's SideEffect runs after the parent's in the same frame, so this one wins.
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !onAccent && !dark
    }
    DisposableEffect(view) {
        onDispose {
            val window = (view.context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !latestDark
        }
    }
}

@Composable
private fun HomeTopBar(
    contentColor: Color,
    lit: Boolean,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AddMenuButton(lit = lit, onAction = onAction)
        Box {
            var open by remember { mutableStateOf(false) }
            HomeTile(
                colors = OnBackgroundColors(Color.Transparent, contentColor),
                shape = RoundedCornerShape(14.dp),
                onClick = { open = true },
            ) {
                Column(
                    Modifier
                        .size(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.home_more), style = MaterialTheme.typography.labelSmall)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_update_all)) },
                    onClick = {
                        open = false
                        onAction(MainAction.UpdateGroupSubscription(ALL_SUBSCRIPTIONS_ID))
                    },
                )
                AppDropdownMenuItems(MORE_DESTINATIONS, { it.labelRes }) { destination ->
                    open = false
                    onNavigate(destination)
                }
            }
        }
    }
}

/** "Add" opens the existing import menu until the Add sheet exists. */
@Composable
private fun AddMenuButton(lit: Boolean, onAction: (MainAction) -> Unit, filled: Boolean = false) {
    Box {
        var open by remember { mutableStateOf(false) }
        val label = stringResource(R.string.home_add)
        if (filled) {
            Button(onClick = { open = true }) { Text(label) }
        } else {
            val colors = if (lit) {
                OnBackgroundColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), Color.White)
            } else {
                OnBackgroundColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }
            HomeTile(colors = colors, shape = CircleShape, onClick = { open = true }) {
                Row(
                    Modifier
                        .height(40.dp)
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ImportMenuContent { action ->
                open = false
                onAction(action)
            }
        }
    }
}

/** No subscription yet. First run proper comes later; this only keeps the screen usable. */
@Composable
private fun HomeEmpty(contentColor: Color, onAction: (MainAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_empty),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
        Text(stringResource(R.string.home_empty_hint), style = MaterialTheme.typography.bodyMedium, color = contentColor)
        AddMenuButton(lit = false, onAction = onAction, filled = true)
    }
}

/** Confirmations and share dialogs of the server list, the same ones the old main screen used. */
private class HomeDialogs(private val onAction: (MainAction) -> Unit) {
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

@Composable
private fun rememberHomeDialogs(onAction: (MainAction) -> Unit): HomeDialogs = remember { HomeDialogs(onAction) }

/**
 * Server drawer: the collapsed header shows the current server with "Check" and "Restart"; pulled
 * up it holds the existing server list of the selected group and its list menu (sort, locate,
 * delete, export), so every server-row function stays as it was.
 */
@Composable
private fun ServersSheet(
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
    val lazyListStates = remember { mutableStateMapOf<String, LazyListState>() }
    val lazyGridStates = remember { mutableStateMapOf<String, LazyGridState>() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .imePadding()
    ) {
        Column(Modifier.onSizeChanged { onHeaderHeight(it.height) }) {
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
                IconButton(onClick = { showSearch = !showSearch; if (showSearch) onExpand() }) {
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
                    IconButton(onClick = { query = ""; onAction(MainAction.Search("")); showSearch = false }) {
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
                    contentPadding = PaddingValues(bottom = navBottom + 16.dp),
                )
            }
        }
    }
}
