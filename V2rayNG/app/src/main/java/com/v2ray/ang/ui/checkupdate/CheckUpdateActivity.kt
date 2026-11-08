package com.v2ray.ang.ui.checkupdate

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FrostCardGroup
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.VersionInfoBlock
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostPageColors
import com.v2ray.ang.ui.compose.muted
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils

class CheckUpdateActivity : BaseComponentActivity() {

    private val viewModel: CheckUpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            viewModel.checkForUpdates()
        }
    }

    @Composable
    override fun ScreenContent() {
        CheckUpdateScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
fun CheckUpdateScreen(
    viewModel: CheckUpdateViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val checkPreRelease by viewModel.checkPreRelease.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()

    val libVersion = CoreNativeManager.getLibVersion()
    val versionText = "v${BuildConfig.VERSION_NAME} ($libVersion)"

    // The check-update screen had no surface of its own: it drew its rows straight onto the window
    // background, which is why it looked like settings without settings' page tone.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = frostPageColors().container,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.update_check_for_update),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = FrostSpacing.s,
                    bottom = innerPadding.calculateBottomPadding() + FrostSpacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap),
        ) {
            FrostCardGroup(Modifier.padding(horizontal = FrostSpacing.screenH)) {
                SettingsSwitchItem(
                    icon = painterResource(R.drawable.ic_source_code_24dp),
                    title = stringResource(R.string.update_check_pre_release),
                    checked = checkPreRelease,
                    onCheckedChange = { viewModel.toggleCheckPreRelease(it) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_check_update_24dp),
                    title = stringResource(R.string.update_check_for_update),
                    onClick = { viewModel.checkForUpdates() }
                )
            }
            VersionInfoBlock(versionText = versionText)
            NavigationBarsSpacer()
        }
    }

    if (showUpdateDialog && updateResult != null) {
        val result = updateResult!!
        val card = frostCardColors()
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(
                    text = stringResource(R.string.update_new_version_found, result.latestVersion ?: ""),
                    style = MaterialTheme.typography.titleLarge,
                    color = card.content,
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Text(
                    text = result.releaseNotes.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = card.muted(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .verticalScrollbar(scrollState)
                )
            },
            confirmButton = {
                FrostTextButton(
                    label = stringResource(R.string.update_now),
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        result.downloadUrl?.let { Utils.openUri(context, it) }
                    }
                )
            },
            dismissButton = {
                FrostTextButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { viewModel.dismissUpdateDialog() }
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
