package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.v2ray.ang.AppConfig
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
import com.v2ray.ang.ui.compose.VersionInfoBlock
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostPageColors
import com.v2ray.ang.util.Utils

class AboutActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        AboutScreen(
            onBackClick = { finish() },
            onTranslatorsClick = {
                startActivity(Intent(this, TranslatorsActivity::class.java))
            }
        )
    }
}

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onTranslatorsClick: () -> Unit
) {
    val context = LocalContext.current
    var showOssDialog by remember { mutableStateOf(false) }

    val libVersion = CoreNativeManager.getLibVersion()
    val versionText = "v${BuildConfig.VERSION_NAME} ($libVersion)"
    val appIdText = BuildConfig.APPLICATION_ID

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = frostPageColors().container,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_about),
                onBackClick = onBackClick
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
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_source_code_24dp),
                    title = stringResource(R.string.title_source_code),
                    onClick = { Utils.openUri(context, AppConfig.APP_URL) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.license_24px),
                    title = stringResource(R.string.title_oss_license),
                    onClick = { showOssDialog = true }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_translate_24dp),
                    title = stringResource(R.string.title_translators),
                    onClick = onTranslatorsClick
                )
            }
            FrostCardGroup(Modifier.padding(horizontal = FrostSpacing.screenH)) {
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_feedback_24dp),
                    title = stringResource(R.string.title_pref_feedback),
                    onClick = { Utils.openUri(context, AppConfig.APP_ISSUES_URL) }
                )
                SettingsMenuItem(
                    icon = painterResource(R.drawable.ic_privacy_24dp),
                    title = stringResource(R.string.title_privacy_policy),
                    onClick = { Utils.openUri(context, AppConfig.APP_PRIVACY_POLICY) }
                )
            }
            FrostCardGroup(Modifier.padding(horizontal = FrostSpacing.screenH)) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = FrostSpacing.l,
                        vertical = FrostSpacing.m,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FrostSpacing.s),
                ) {
                    // States plainly what this build is. Without it the screen names a version and
                    // a repository that look like upstream v2rayNG's, which misleads the user about
                    // who made the build and where its bugs belong.
                    Text(
                        text = stringResource(R.string.about_fork_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VersionInfoBlock(
                versionText = versionText,
                appIdText = appIdText
            )
            NavigationBarsSpacer()
        }
    }

    if (showOssDialog) {
        val card = frostCardColors()
        AlertDialog(
            onDismissRequest = { showOssDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.title_oss_license),
                    style = MaterialTheme.typography.titleLarge,
                    color = card.content,
                )
            },
            text = {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            loadUrl("file:///android_asset/open_source_licenses.html")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            },
            confirmButton = {
                FrostTextButton(
                    label = stringResource(R.string.action_ok),
                    onClick = { showOssDialog = false }
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
