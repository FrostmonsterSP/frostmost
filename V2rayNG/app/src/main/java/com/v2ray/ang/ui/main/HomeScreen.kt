package com.v2ray.ang.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme
import kotlinx.coroutines.launch

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

    var sheet by rememberSaveable { mutableStateOf<HomeSheet?>(null) }
    // A clipboard, QR or file result arrives after the Add sheet closed: reopen it with the text and its type.
    LaunchedEffect(uiState.importDraft) { if (uiState.importDraft != null) sheet = HomeSheet.Add }
    HomeSheetHost(
        sheet = sheet,
        onSheetChange = {
            sheet = it
            if (it != HomeSheet.Add) onAction(MainAction.ClearImportDraft)
        },
        routingPreset = uiState.routingPreset,
        routingPresetLoaded = uiState.routingPresetLoaded,
        pendingRules = uiState.pendingRules,
        importDraft = uiState.importDraft,
        onAction = onAction,
        onNavigate = onNavigate,
    )

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
                    onAdd = { sheet = HomeSheet.Add },
                    onMore = { sheet = HomeSheet.More },
                )
                if (card == null) {
                    HomeEmpty(contentColor = onBackground, onAdd = { sheet = HomeSheet.Add })
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
                    onClick = { sheet = HomeSheet.Rules },
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
    onAdd: () -> Unit,
    onMore: () -> Unit,
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
        AddButton(lit = lit, onClick = onAdd)
        HomeTile(
            colors = OnBackgroundColors(Color.Transparent, contentColor),
            shape = RoundedCornerShape(14.dp),
            onClick = onMore,
        ) {
            Column(
                Modifier.size(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.home_more), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** "Add" opens the Add sheet. */
@Composable
private fun AddButton(lit: Boolean, onClick: () -> Unit, filled: Boolean = false) {
    val label = stringResource(R.string.home_add)
    if (filled) {
        Button(onClick = onClick) { Text(label) }
        return
    }
    val colors = if (lit) {
        OnBackgroundColors(Color.White.copy(alpha = HOME_ON_ACCENT_OVERLAY), Color.White)
    } else {
        OnBackgroundColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    HomeTile(colors = colors, shape = CircleShape, onClick = onClick) {
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

/** No subscription yet. First run proper comes later; this only keeps the screen usable. */
@Composable
private fun HomeEmpty(contentColor: Color, onAdd: () -> Unit) {
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
        AddButton(lit = false, onClick = onAdd, filled = true)
    }
}
