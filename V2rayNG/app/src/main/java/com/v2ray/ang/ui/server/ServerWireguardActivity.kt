package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormTextField

class ServerWireguardActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.WIREGUARD

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.WIREGUARD
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            EditorSection(stringResource(R.string.server_section_main)) {
                CommonBasicFields(uiState)
            }
            EditorSection(stringResource(R.string.server_section_protocol)) {
                WireguardKeysFields(uiState)
            }
            EditorSection(stringResource(R.string.server_section_transport)) {
                WireguardTunnelFields(uiState)
            }
            EditorSection(stringResource(R.string.server_section_extra)) {
                WireguardExtraFields(uiState)
            }
        }
    }

    /** Keys the peer must present. Nothing else on the screen works without them. */
    @Composable
    private fun WireguardKeysFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_secret_key),
            state.secretKey,
            { state.secretKey = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_public_key),
            state.publicKey,
            { state.publicKey = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_preshared_key),
            state.preSharedKey,
            { state.preSharedKey = it }
        )
    }

    /** The tunnel itself: addresses and MTU. */
    @Composable
    private fun WireguardTunnelFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_reserved),
            state.reserved,
            { state.reserved = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_local_address),
            state.localAddress,
            { state.localAddress = it }
        )
        FormTextField(
            stringResource(R.string.server_lab_local_mtu),
            state.mtu,
            { state.mtu = it },
            keyboardType = KeyboardType.Number
        )
    }

    /** DNS and mask, which only matter once the tunnel is up. */
    @Composable
    private fun WireguardExtraFields(state: ServerUiState) {
        FormTextField(
            stringResource(R.string.server_lab_remote_dns),
            state.remoteDNS,
            { state.remoteDNS = it },
            supportingText = stringResource(
                R.string.server_lab_remote_dns_hint,
                AppConfig.WIREGUARD_LOCAL_REMOTE_DNS
            )
        )
        FormTextField(
            stringResource(R.string.server_lab_final_mask),
            state.finalMask,
            { state.finalMask = it }
        )
    }
}
