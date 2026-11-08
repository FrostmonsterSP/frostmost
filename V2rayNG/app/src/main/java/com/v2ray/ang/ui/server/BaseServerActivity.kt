package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.AppConfig.REALITY
import com.v2ray.ang.AppConfig.TLS
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.CertificateFingerprintManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.FrostFormSection
import com.v2ray.ang.ui.compose.FrostPrimaryButton
import com.v2ray.ang.ui.compose.FrostElevation
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared shell of the protocol editors.
 *
 * The screen is one scrolling column of [FrostFormSection] cards with a pinned bottom bar that holds
 * the primary action. The bottom bar is inside the same `imePadding()` chain as the scroll container,
 * so with the keyboard open it rides above the keyboard and the last field stays reachable — the
 * form contract the UI guide requires.
 *
 * Nothing here reads or writes a profile: the screens hand their `ServerUiState` to
 * [saveServer], which is unchanged from the previous implementation.
 */
abstract class BaseServerActivity : BaseComponentActivity() {

    protected abstract val serverConfigType: EConfigType

    protected val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    protected val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    protected val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    protected lateinit var initialConfig: ProfileItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existingConfig = MmkvManager.decodeServerConfig(editGuid)
        initialConfig = existingConfig ?: ProfileItem.create(serverConfigType)
    }

    @Composable
    protected fun rememberFieldOptions(): FieldOptions =
        FieldOptions(
            networkOptions = stringArrayResource(R.array.networks).toList(),
            tcpHeaderOptions = stringArrayResource(R.array.header_type_tcp).toList(),
            kcpHeaderOptions = stringArrayResource(R.array.header_type_kcp_and_quic).toList(),
            grpcModeOptions = stringArrayResource(R.array.mode_type_grpc).toList(),
            xhttpModeOptions = stringArrayResource(R.array.xhttp_mode).toList(),
            streamSecurityOptions = stringArrayResource(R.array.streamsecurityxs).toList(),
            uTlsOptions = stringArrayResource(R.array.streamsecurity_utls).toList(),
            alpnOptions = stringArrayResource(R.array.streamsecurity_alpn).toList(),
            browserDialerOptions = stringArrayResource(R.array.browser_dialer_mode_value).toList()
        )

    data class FieldOptions(
        val networkOptions: List<String>,
        val tcpHeaderOptions: List<String>,
        val kcpHeaderOptions: List<String>,
        val grpcModeOptions: List<String>,
        val xhttpModeOptions: List<String>,
        val streamSecurityOptions: List<String>,
        val uTlsOptions: List<String>,
        val alpnOptions: List<String>,
        val browserDialerOptions: List<String>
    )

    /** "Основное": the three fields without which a profile cannot exist. */
    @Composable
    protected fun CommonBasicFields(
        state: ServerUiState
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
            FormTextField(
                label = stringResource(R.string.server_lab_remarks),
                value = state.remarks,
                onValueChange = { state.remarks = it },
                isError = state.isRemarksError,
                supportingText = if (state.isRemarksError) {
                    stringResource(R.string.server_field_required)
                } else {
                    null
                }
            )
            FormTextField(
                label = stringResource(R.string.server_lab_address),
                value = state.address,
                onValueChange = { state.address = it },
                isError = state.isAddressError,
                supportingText = if (state.isAddressError) {
                    stringResource(R.string.server_field_required)
                } else {
                    null
                }
            )
            FormTextField(
                label = stringResource(R.string.server_lab_port),
                value = state.port,
                onValueChange = { state.port = it },
                keyboardType = KeyboardType.Number,
                isError = state.isPortError,
                supportingText = if (state.isPortError) {
                    stringResource(R.string.server_field_required)
                } else {
                    null
                }
            )
        }
    }

    /** "Транспорт": everything that describes how the connection is carried. */
    @Composable
    protected fun CommonNetworkFields(
        state: ServerUiState,
        options: FieldOptions
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
            FormDropdownField(
                stringResource(R.string.server_lab_network),
                state.network,
                options.networkOptions,
                { state.network = it }
            )

            val headerOptions = when (state.network) {
                NetworkType.TCP.type -> options.tcpHeaderOptions
                NetworkType.KCP.type -> options.kcpHeaderOptions
                NetworkType.GRPC.type -> options.grpcModeOptions
                NetworkType.XHTTP.type -> options.xhttpModeOptions
                else -> listOf("---")
            }
            if (headerOptions.size > 1) {
                FormDropdownField(
                    stringResource(
                        when (state.network) {
                            NetworkType.GRPC.type -> R.string.server_lab_mode_type
                            NetworkType.XHTTP.type -> R.string.server_lab_xhttp_mode
                            else -> R.string.server_lab_head_type
                        }
                    ),
                    when (state.network) {
                        NetworkType.GRPC.type -> state.mode
                        NetworkType.XHTTP.type -> state.xhttpMode
                        else -> state.headerType
                    },
                    headerOptions,
                    {
                        when (state.network) {
                            NetworkType.GRPC.type -> state.mode = it
                            NetworkType.XHTTP.type -> state.xhttpMode = it
                            else -> state.headerType = it
                        }
                    }
                )
            }

            FormTextField(
                stringResource(
                    when (state.network) {
                        NetworkType.TCP.type,
                        NetworkType.HTTP_UPGRADE.type,
                        NetworkType.XHTTP.type,
                        NetworkType.H2.type -> R.string.server_lab_request_host_http

                        NetworkType.WS.type -> R.string.server_lab_request_host_ws
                        NetworkType.GRPC.type -> R.string.server_lab_request_host_grpc
                        else -> R.string.server_lab_request_host6
                    }
                ),
                if (state.network == NetworkType.GRPC.type) state.authority else state.host,
                { if (state.network == NetworkType.GRPC.type) state.authority = it else state.host = it }
            )

            if (state.network != NetworkType.KCP.type) {
                FormTextField(
                    stringResource(
                        when (state.network) {
                            NetworkType.WS.type -> R.string.server_lab_path_ws
                            NetworkType.HTTP_UPGRADE.type -> R.string.server_lab_path_httpupgrade
                            NetworkType.XHTTP.type -> R.string.server_lab_path_xhttp
                            NetworkType.H2.type -> R.string.server_lab_path_h2
                            NetworkType.GRPC.type -> R.string.server_lab_path_grpc
                            else -> R.string.server_lab_path
                        }
                    ),
                    if (state.network == NetworkType.GRPC.type) state.serviceName else state.path,
                    { if (state.network == NetworkType.GRPC.type) state.serviceName = it else state.path = it }
                )
            }

            if (state.network == NetworkType.XHTTP.type) {
                FormTextField(
                    stringResource(R.string.server_lab_xhttp_extra),
                    state.xhttpExtra,
                    { state.xhttpExtra = it }
                )
            }
            if (state.network == NetworkType.KCP.type) {
                FormTextField(
                    stringResource(R.string.server_lab_path_kcp),
                    state.seed,
                    { state.seed = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_kcp_mtu),
                    state.kcpMtu,
                    { state.kcpMtu = it },
                    keyboardType = KeyboardType.Number
                )
                FormTextField(
                    stringResource(R.string.server_lab_kcp_tti),
                    state.kcpTti,
                    { state.kcpTti = it },
                    keyboardType = KeyboardType.Number
                )
            }
            FormTextField(
                stringResource(R.string.server_lab_final_mask),
                state.finalMask,
                { state.finalMask = it }
            )
            if (state.network == NetworkType.WS.type || state.network == NetworkType.XHTTP.type) {
                FormDropdownField(
                    stringResource(R.string.server_lab_browser_dialer),
                    state.browserDialerMode,
                    options.browserDialerOptions,
                    { state.browserDialerMode = it }
                )
            }
        }
    }

    /**
     * "TLS" / "Дополничительно": the security layer. The section header itself stays on screen even
     * when the transport is `none`, because the security dropdown is the control that opens the rest
     * and must remain reachable.
     */
    @Composable
    protected fun CommonStreamSecurityFields(
        state: ServerUiState,
        options: FieldOptions,
        scope: CoroutineScope,
        buildProfileItem: () -> ProfileItem
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs)) {
            FormDropdownField(
                stringResource(R.string.server_lab_stream_security),
                state.streamSecurity,
                options.streamSecurityOptions,
                { state.streamSecurity = it }
            )

            if (state.streamSecurity.isBlank()) {
                return@Column
            }

            FormTextField(
                stringResource(R.string.server_lab_sni),
                state.sni,
                { state.sni = it }
            )
            FormDropdownField(
                stringResource(R.string.server_lab_stream_fingerprint),
                state.fingerPrint,
                options.uTlsOptions,
                { state.fingerPrint = it }
            )

            if (state.streamSecurity == TLS) {
                SettingsSwitchItem(
                    title = stringResource(R.string.server_lab_allow_insecure),
                    checked = state.allowInsecure,
                    onCheckedChange = { state.allowInsecure = it }
                )
                FormDropdownField(
                    stringResource(R.string.server_lab_stream_alpn),
                    state.alpn,
                    options.alpnOptions,
                    { state.alpn = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_ech_config_list),
                    state.echConfigList,
                    { state.echConfigList = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_verify_peer_cert_by_name),
                    state.verifyPeerCertByName,
                    { state.verifyPeerCertByName = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_pinned_ca256),
                    state.pinnedCA256,
                    { state.pinnedCA256 = it }
                )
                CertificateFetchAction(
                    state = state,
                    scope = scope,
                    buildProfileItem = buildProfileItem
                )
            } else if (state.streamSecurity == REALITY) {
                FormTextField(
                    stringResource(R.string.server_lab_public_key),
                    state.publicKeyReality,
                    { state.publicKeyReality = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_short_id),
                    state.shortId,
                    { state.shortId = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_spider_x),
                    state.spiderX,
                    { state.spiderX = it }
                )
                FormTextField(
                    stringResource(R.string.server_lab_mldsa65_verify),
                    state.mldsa65Verify,
                    { state.mldsa65Verify = it }
                )
            }
        }
    }

    /** "Получить отпечаток сертификата": a secondary action, so it is a text action, not a filled one. */
    @Composable
    private fun CertificateFetchAction(
        state: ServerUiState,
        scope: CoroutineScope,
        buildProfileItem: () -> ProfileItem
    ) {
        val context = LocalContext.current
        // FrostTextButton has no disabled state, so a fetch in flight is guarded in the handler
        // instead of silently dropping the tap.
        FrostTextButton(
            label = stringResource(R.string.pinned_ca256_action_fetch),
            onClick = {
                if (state.isFetchingCert) return@FrostTextButton
                if (state.address.isBlank()) {
                    context.toast(R.string.server_lab_address)
                    return@FrostTextButton
                }
                if (
                    state.configType != EConfigType.HYSTERIA2 &&
                    (state.port.toIntOrNull() ?: 0) <= 0
                ) {
                    context.toast(R.string.server_lab_port)
                    return@FrostTextButton
                }
                val temp = buildProfileItem()
                scope.launch {
                    state.isFetchingCert = true
                    try {
                        val sha256 = withContext(Dispatchers.IO) {
                            CertificateFingerprintManager.fetchForManualFill(temp)
                        }
                        if (sha256.isNullOrBlank()) {
                            context.toast(R.string.toast_fetch_cert_sha256_failed)
                        } else {
                            state.pinnedCA256 = sha256
                            context.toastSuccess(R.string.toast_fetch_cert_sha256_success)
                        }
                    } finally {
                        state.isFetchingCert = false
                    }
                }
            },
            modifier = Modifier.padding(start = FrostSpacing.xs),
        )
    }

    protected fun validateBasicConfig(state: ServerUiState): Boolean {
        val remarksErr = state.remarks.isBlank()
        val addressErr = state.address.isBlank()
        val portErr = state.configType != EConfigType.HYSTERIA2 && (state.port.toIntOrNull() ?: 0) <= 0

        state.isRemarksError = remarksErr
        state.isAddressError = addressErr
        state.isPortError = portErr

        val hasError = remarksErr || addressErr || portErr
        return !hasError
    }

    protected open fun validateProtocolConfig(config: ProfileItem): Boolean = true

    protected open fun validateCommonConfig(state: ServerUiState, config: ProfileItem): Boolean {

        if (config.password.isNullOrBlank()) {
            state.isPasswordError = true
            if (config.configType == EConfigType.VMESS ||
                config.configType == EConfigType.VLESS
            ) {
                return false
            }

            if (config.configType == EConfigType.TROJAN ||
                config.configType == EConfigType.SHADOWSOCKS ||
                config.configType == EConfigType.HYSTERIA2
            ) {
                return false
            }
        }

        if (
            config.configType == EConfigType.TROJAN &&
            config.security.isNullOrBlank()
        ) {
            toast(R.string.server_lab_stream_security)
            return false
        }
        if (!config.xhttpExtra.isNullOrBlank() && JsonUtil.parseString(config.xhttpExtra) == null) {
            toast(R.string.server_lab_xhttp_extra)
            return false
        }
        if (!config.finalMask.isNullOrBlank() && JsonUtil.parseString(config.finalMask) == null) {
            toast(R.string.server_lab_final_mask)
            return false
        }
        return true
    }

    protected fun saveServer(state: ServerUiState): Boolean {
        if (!validateBasicConfig(state)) return false
        val config = state.toProfileItem(initialConfig)
        if (!validateCommonConfig(state, config)) return false
        if (!validateProtocolConfig(config)) return false

        config.description = AngConfigManager.generateDescription(config)
        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        val savedGuid = MmkvManager.encodeServerConfig(editGuid, config)
        toastSuccess(R.string.toast_success)
        ProfileEditorResult.run {
            finishSaved(savedGuid, isRunning)
        }
        return true
    }

    /**
     * The editor shell: a title bar, a scrolling column of sections, and a bottom bar with the
     * primary action ([FrostPrimaryButton]) plus a delete action when the profile can be removed.
     *
     * The scroll container keeps `imePadding()`, and the bottom bar sits outside the scrolling
     * column in the same inset chain, so the last field can always be scrolled above the keyboard.
     */
    @Composable
    protected fun ServerEditorScaffold(
        title: String,
        onSaveClick: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
    ) {
        var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        val showDelete = editGuid.isNotEmpty() && !isRunning
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppTopBar(
                    title = title,
                    onBackClick = { finish() },
                    actions = {
                        if (showDelete) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24dp),
                                    stringResource(R.string.acc_delete)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                EditorBottomBar(
                    onSaveClick = onSaveClick,
                    onDeleteClick = if (showDelete) {
                        { showDeleteDialog = true }
                    } else {
                        null
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .verticalScrollbar(scrollState)
                        .padding(horizontal = FrostSpacing.screenH)
                        .padding(top = FrostSpacing.l, bottom = FrostSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap)
                ) {
                    content()
                }
            }
        }
        if (showDeleteDialog) {
            DeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_profile),
                itemName = initialConfig.remarks,
                onConfirm = {
                    showDeleteDialog = false
                    deleteServer(editGuid)
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }

    /** A section card inside [ServerEditorScaffold]. */
    @Composable
    protected fun EditorSection(
        title: String,
        content: @Composable ColumnScope.() -> Unit
    ) {
        FrostFormSection(title = title, content = content)
    }

    /** Pinned action bar. Save is the only filled action on the screen. */
    @Composable
    private fun EditorBottomBar(
        onSaveClick: () -> Unit,
        onDeleteClick: (() -> Unit)?,
    ) {
        // A full-bleed plate: `RectangleShape` rather than a Frost radius, because this bar is the
        // screen edge itself, not a card resting on it.
        FrostSurface(
            colors = frostCardColors(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RectangleShape,
            elevation = FrostElevation.card,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FrostSpacing.screenH,
                        end = FrostSpacing.screenH,
                        top = FrostSpacing.m,
                        bottom = FrostSpacing.m,
                    ),
                verticalArrangement = Arrangement.spacedBy(FrostSpacing.s),
            ) {
                FrostPrimaryButton(
                    label = stringResource(R.string.acc_save),
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth(),
                    icon = painterResource(R.drawable.ic_fab_check),
                )
                if (onDeleteClick != null) {
                    FrostTextButton(
                        label = stringResource(R.string.acc_delete),
                        onClick = onDeleteClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private fun deleteServer(guid: String) {
        if (guid.isEmpty() || guid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        MmkvManager.removeServer(guid)
        ProfileEditorResult.run {
            finishDeleted(guid)
        }
    }
}
