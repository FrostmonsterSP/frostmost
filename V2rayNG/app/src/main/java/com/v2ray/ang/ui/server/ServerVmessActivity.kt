package com.v2ray.ang.ui.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField

class ServerVmessActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.VMESS

    @Composable
    override fun ScreenContent() {
        val options = rememberFieldOptions()
        val scope = rememberCoroutineScope()
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(
                initialConfig = initialConfig
            )
        }.apply {
            configType = EConfigType.VMESS
        }
        val securityOptions = stringArrayResource(R.array.securitys).toList()

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { saveServer(uiState) }
        ) {
            EditorSection(stringResource(R.string.server_section_main)) {
                CommonBasicFields(uiState)
            }
            EditorSection(stringResource(R.string.server_section_protocol)) {
                VmessProtocolFields(uiState, securityOptions)
            }
            EditorSection(stringResource(R.string.server_section_transport)) {
                CommonNetworkFields(uiState, options)
            }
            EditorSection(stringResource(R.string.server_section_security)) {
                CommonStreamSecurityFields(
                    state = uiState,
                    options = options,
                    scope = scope,
                    buildProfileItem = { uiState.toProfileItem(initialConfig) }
                )
            }
        }
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        if (config.password.isNullOrBlank()) {
            return false
        }
        return true
    }

    @Composable
    private fun VmessProtocolFields(
        state: ServerUiState,
        methodOptions: List<String>
    ) {
        FormTextField(
            label = stringResource(R.string.server_lab_id),
            value = state.password,
            onValueChange = { state.password = it },
            isError = state.isPasswordError,
            supportingText = if (state.isPasswordError) {
                stringResource(R.string.server_field_required)
            } else {
                null
            }
        )
        FormDropdownField(
            stringResource(R.string.server_lab_security),
            state.method,
            methodOptions,
            { state.method = it }
        )
    }
}
