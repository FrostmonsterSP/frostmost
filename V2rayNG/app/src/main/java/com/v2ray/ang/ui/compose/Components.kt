package com.v2ray.ang.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.v2ray.ang.R
import com.v2ray.ang.util.AppIconFetcher
import sh.calvin.reorderable.ReorderableCollectionItemScope

/**
 * Shared chrome of FrostMost.
 *
 * The top bar is drawn as part of the page instead of on a Material `TopAppBar`: the page, the bar
 * and the search field all take `surface`/`onSurface`, so navigating to a sub-screen never paints a
 * tinted strip over a differently toned page. The signature is unchanged, because every screen in
 * the app uses it.
 */

/** Height of one bar row, excluding the status-bar inset. */
private val TOP_BAR_HEIGHT = 56.dp

/**
 * The top bar. It owns the status-bar inset: it adds that height above its own row, so the content
 * below starts at the bar and no screen pads for the status bar a second time.
 */
@Composable
fun AppTopBar(
    title: String,
    onBackClick: () -> Unit,
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    searchPlaceholder: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val surface = frostPageColors()
    Column(
        Modifier
            .fillMaxWidth()
            .background(surface.container)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TOP_BAR_HEIGHT)
                .padding(start = FrostSpacing.xs, end = FrostSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xxs),
        ) {
            // The back arrow doubles as the search close control while the search field is open,
            // because stopping the search is what the icon means in that state.
            val backLabel = stringResource(R.string.acc_back)
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = backLabel,
                        onClick = if (isSearchActive) onSearchClose else onBackClick,
                    ),
            ) {
                Row(
                    Modifier
                        .heightIn(min = FrostSpacing.touchTarget)
                        .padding(horizontal = FrostSpacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The caller's navigation icon inherits the bar's foreground like the rest of it.
                    CompositionLocalProvider(LocalContentColor provides surface.content) {
                        if (navigationIcon != null) {
                            navigationIcon()
                        } else {
                            BackGlyph()
                        }
                    }
                }
            }
            if (isSearchActive) {
                SearchInputField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = searchPlaceholder,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xxs),
                    content = actions,
                )
            }
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** The 24 dp back glyph. Decorative: the clickable row around it carries the action's name. */
@Composable
private fun BackGlyph() {
    Icon(
        painter = painterResource(R.drawable.ic_arrow_back_24dp),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

/**
 * "Clear" glyph for the search field.
 *
 * It is the platform's `ic_menu_close_clear_cancel`, which is the only cross-glyph the app already
 * relies on: the fork adds no drawable outside its own resource scope. The name is localized, which
 * the previous hardcoded `"Clear"` was not. Replace it with a project vector once one is added under
 * `res/drawable` (outside this task's scope).
 */
@Composable
private fun ClearGlyph() {
    val clearLabel = stringResource(R.string.acc_clear_search)
    Icon(
        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
        contentDescription = clearLabel,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String?,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                if (placeholder != null) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.primary,
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) { ClearGlyph() }
        }
    }
}

/**
 * One application row: icon, name, package and a checkbox.
 *
 * The row is a single focusable node — a tap anywhere, Enter, Space and the D-pad all toggle the
 * same selection — so TalkBack reads the name and the state once instead of once per child. The
 * checkbox mirrors the state and carries no semantics of its own for that reason.
 */
@Composable
fun AppListItem(
    appName: String,
    packageName: String,
    icon: Any?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toggleLabel = stringResource(
        if (checked) R.string.acc_deselect_app else R.string.acc_select_app,
        appName,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FrostRadius.tile)
            .clickable(
                role = Role.Checkbox,
                onClickLabel = toggleLabel,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = FrostSpacing.l, vertical = FrostSpacing.m)
            .semantics(mergeDescendants = true) { contentDescription = appName },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val model = remember(icon, packageName) {
            if (icon != null) {
                icon
            } else {
                val data = "appicon:$packageName"
                ImageRequest.Builder(context)
                    .data(data)
                    .fetcherFactory(AppIconFetcher.Factory(context))
                    .build()
            }
        }

        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(FrostRadius.chip)
                .clearAndSetSemantics {},
            contentScale = ContentScale.Fit,
            error = painterResource(R.drawable.ic_image_24dp),
            fallback = painterResource(R.drawable.ic_image_24dp)
        )
        Spacer(modifier = Modifier.width(FrostSpacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(FrostSpacing.s))
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            )
        )
    }
}

/** Divider between two rows of the same card group. */
@Composable
fun ItemDivider() {
    AppDivider(modifier = Modifier.padding(horizontal = FrostSpacing.l))
}

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A card group: the rows of one topic on a single card, so a long screen reads as a few plates
 * rather than as a hundred stripes. Rows inside keep their own dividers.
 *
 * The plate is separated from the page by tone — `surface` on `background` — and not by a border.
 */
@Composable
fun FrostCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    FrostSurface(
        colors = frostCardColors(),
        modifier = modifier.fillMaxWidth(),
        shape = FrostRadius.card,
    ) {
        Column(content = content)
    }
}

@Composable
fun NavigationBarsSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}

@Composable
fun NavigationBarsBottomPadding(): PaddingValues {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = bottom)
}

/** Version and package of the build: a quiet footer at the end of an informational screen. */
@Composable
fun VersionInfoBlock(
    versionText: String,
    appIdText: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FrostSpacing.screenH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(FrostSpacing.l))
        Text(
            text = versionText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (appIdText != null) {
            Spacer(modifier = Modifier.height(FrostSpacing.xs))
            Text(
                text = appIdText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun reorderableElevation(isDragging: Boolean) = animateDpAsState(
    targetValue = if (isDragging) 4.dp else 0.dp,
    label = "ReorderableElevation"
)

@Composable
fun ReorderableCollectionItemScope.reorderableDragHandle(): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    return Modifier.longPressDraggableHandle(
        onDragStarted = {
            // Platform haptics honor the user's touch-feedback setting.
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    )
}

/** A draggable row: lifted by a soft shadow only while it is being dragged. */
@Composable
fun ReorderableListItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    FrostSurface(
        colors = frostCardColors(),
        modifier = Modifier.fillMaxWidth(),
        shape = FrostRadius.chip,
        elevation = elevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(with(scope) { reorderableDragHandle() }),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun ReorderableGridItem(
    scope: ReorderableCollectionItemScope,
    isDragging: Boolean,
    content: @Composable () -> Unit
) {
    val elevation by reorderableElevation(isDragging)
    FrostSurface(
        colors = frostCardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .then(with(scope) { reorderableDragHandle() }),
        shape = FrostRadius.chip,
        elevation = elevation,
    ) {
        content()
    }
}
