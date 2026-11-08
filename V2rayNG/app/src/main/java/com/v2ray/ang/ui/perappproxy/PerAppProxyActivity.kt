package com.v2ray.ang.ui.perappproxy

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.v2ray.ang.R
import com.v2ray.ang.dto.AppInfo
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostScreenPadding
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostSwitch
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsCard
import com.v2ray.ang.ui.compose.SettingsDivider
import com.v2ray.ang.ui.compose.frostActionColors
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostInnerColors
import com.v2ray.ang.ui.compose.muted
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.AppIconFetcher
import com.v2ray.ang.util.Utils

private enum class PerAppMenuAction(@StringRes val labelRes: Int) {
    SelectAll(R.string.menu_item_select_all),
    InvertSelection(R.string.menu_item_invert_selection),
    SelectProxyApps(R.string.menu_item_select_proxy_app),
    ImportSelection(R.string.menu_item_import_proxy_app),
    ExportSelection(R.string.menu_item_export_proxy_app)
}

class PerAppProxyActivity : BaseComponentActivity() {

    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadApps(this)
    }

    @Composable
    override fun ScreenContent() {
        val apps by viewModel.displayedApps.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
        val perAppProxyEnabled by viewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
        val bypassApps by viewModel.bypassApps.collectAsStateWithLifecycle()
        val context = LocalContext.current

        PerAppProxyScreen(
            apps = apps,
            isLoading = isLoading,
            blacklist = blacklist,
            perAppProxyEnabled = perAppProxyEnabled,
            bypassApps = bypassApps,
            onBackClick = { finish() },
            onPerAppProxyChanged = { viewModel.setPerAppProxyEnabled(it) },
            onBypassAppsChanged = { viewModel.setBypassAppsEnabled(it) },
            onToggleApp = { viewModel.toggle(it) },
            onSearch = { viewModel.filterApps(it) },
            onSelectAll = { viewModel.selectAll() },
            onInvertSelection = { viewModel.invertSelection() },
            onSelectProxyAuto = { viewModel.selectProxyAppAuto(context) },
            onImportProxyApp = {
                val content = Utils.getClipboard(context.applicationContext)
                viewModel.importProxyApp(content, context)
            },
            onExportProxyApp = {
                val export = viewModel.exportProxyApp()
                Utils.setClipboard(context.applicationContext, export)
                context.toastSuccess(R.string.toast_success)
            }
        )
    }
}

@Composable
fun PerAppProxyScreen(
    apps: List<AppInfo>,
    isLoading: Boolean,
    blacklist: Set<String>,
    perAppProxyEnabled: Boolean,
    bypassApps: Boolean,
    onBackClick: () -> Unit,
    onPerAppProxyChanged: (Boolean) -> Unit,
    onBypassAppsChanged: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectProxyAuto: () -> Unit,
    onImportProxyApp: () -> Unit,
    onExportProxyApp: () -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    val onInfoClick = { showInfoDialog = true }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onSearch(searchQuery)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.per_app_proxy_settings),
                onBackClick = onBackClick,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    onSearch(query)
                },
                onSearchClose = {
                    searchQuery = ""
                    onSearch("")
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.acc_search)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = stringResource(R.string.acc_more)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppDropdownMenuItems(PerAppMenuAction.entries, { it.labelRes }) { action ->
                                showMenu = false
                                when (action) {
                                    PerAppMenuAction.SelectAll -> onSelectAll()
                                    PerAppMenuAction.InvertSelection -> onInvertSelection()
                                    PerAppMenuAction.SelectProxyApps -> onSelectProxyAuto()
                                    PerAppMenuAction.ImportSelection -> onImportProxyApp()
                                    PerAppMenuAction.ExportSelection -> onExportProxyApp()
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        // The mode card scrolls with the list rather than sitting in a frozen strip above it: on a
        // 360 dp phone the two controls plus the info button did not fit one row, which is what made
        // the old header crowd and truncate.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding(),
        ) {
            item(key = "mode") {
                Column(Modifier.padding(FrostScreenPadding)) {
                    SettingsCard {
                        PerAppSwitchRow(
                            title = stringResource(R.string.per_app_proxy_settings_enable),
                            checked = perAppProxyEnabled,
                            onCheckedChange = onPerAppProxyChanged,
                        )
                        SettingsDivider()
                        PerAppSwitchRow(
                            title = stringResource(R.string.switch_bypass_apps_mode),
                            checked = bypassApps,
                            onCheckedChange = onBypassAppsChanged,
                        )
                        SettingsDivider()
                        PerAppInfoRow(onClick = onInfoClick)
                    }
                }
            }
            itemsIndexed(items = apps, key = { _, app -> app.packageName }) { index, app ->
                val colors = frostInnerColors()
                FrostSurface(
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = FrostSpacing.screenH,
                            end = FrostSpacing.screenH,
                            top = if (index == 0) FrostSpacing.s else 0.dp,
                            bottom = FrostSpacing.xs,
                        ),
                    shape = FrostRadius.tile,
                ) {
                    PerAppRow(
                        app = app,
                        checked = blacklist.contains(app.packageName),
                        onToggle = { onToggleApp(app.packageName) },
                    )
                }
            }
            item(key = "listBottomGap") {
                Spacer(Modifier.size(FrostSpacing.xl))
            }
        }
    }

    if (showInfoDialog) {
        ConfirmDialog(
            message = stringResource(R.string.summary_pref_per_app_proxy),
            dismissText = null,
            onConfirm = {},
            onDismiss = { showInfoDialog = false },
        )
    }
}

/**
 * One mode switch.
 *
 * The row is the single activation: it carries the label and the switch state, and the switch is
 * drawn with `onCheckedChange = null` so it cannot become a second focus target. The old screen put
 * a `Switch` next to a `Text` with no shared node, so TalkBack reached the label and the control as
 * two unrelated stops.
 */
@Composable
private fun PerAppSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = frostCardColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FrostSpacing.rowHeight)
            .clickable(
                role = Role.Switch,
                onClickLabel = title,
                onClick = { onCheckedChange(!checked) },
            )
            .semantics(mergeDescendants = true) {
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            .padding(horizontal = FrostSpacing.cardPadding, vertical = FrostSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.content,
            modifier = Modifier.weight(1f),
        )
        FrostSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

/** The explanation row: the same weight as a toggle row, but it only opens the description. */
@Composable
private fun PerAppInfoRow(onClick: () -> Unit) {
    val colors = frostCardColors()
    val label = stringResource(R.string.acc_per_app_proxy_information)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FrostSpacing.rowHeight)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = FrostSpacing.cardPadding, vertical = FrostSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.content,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_about_24dp),
            contentDescription = null,
            tint = colors.muted(),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * One application: icon, name, package name, selection mark.
 *
 * The row is one activation with `Role.Checkbox` and the merged descendants carry its checked
 * state, so TalkBack reads "app name, package name, checked" as one item instead of walking three
 * stops. The icon and the mark are decorative inside it.
 */
@Composable
private fun PerAppRow(
    app: AppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val colors = frostInnerColors()
    val model = remember(app.packageName) {
        ImageRequest.Builder(context)
            .data("appicon:${app.packageName}")
            .fetcherFactory(AppIconFetcher.Factory(context))
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FrostSpacing.rowHeight)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            .padding(horizontal = FrostSpacing.m, vertical = FrostSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            error = painterResource(R.drawable.ic_image_24dp),
            fallback = painterResource(R.drawable.ic_image_24dp),
        )
        Spacer(Modifier.width(FrostSpacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(FrostSpacing.m))
        AppSelectionMark(checked = checked, icon = painterResource(R.drawable.ic_fab_check))
    }
}

/**
 * The selection state of a row, drawn as a filled badge with a check when on and an outlined empty
 * circle when off.
 *
 * Shape, fill and glyph all change with the state, so the selection survives a colour-blind viewer
 * and a screenshot in either theme; colour alone is never the only channel.
 */
@Composable
private fun AppSelectionMark(checked: Boolean, icon: Painter) {
    if (checked) {
        val colors = frostActionColors(true)
        FrostSurface(colors = colors, shape = FrostRadius.pill) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, FrostRadius.pill),
            contentAlignment = Alignment.Center,
        ) {}
    }
}
