package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.RectangleShape
import com.v2ray.ang.AppConfig.BUILTIN_OUTBOUND_TAGS
import com.v2ray.ang.AppConfig.TAG_PROXY
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BalancerStrategyType
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.FrostElevation
import com.v2ray.ang.ui.compose.FrostFormSection
import com.v2ray.ang.ui.compose.FrostPrimaryButton
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.frostCardColors

class ServerGroupActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }
    private val subIds = mutableListOf<String>()
    private val subDisplay = mutableListOf<String>()
    private lateinit var fallbackSuggestions: List<String>

    private lateinit var initialRemarks: String
    private lateinit var initialFilter: String
    private var initialType: Int = 0
    private var initialSubIndex: Int = 0
    private var initialTestOutbounds: Boolean = false
    private lateinit var initialFallbackTag: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid)
        populateSubscriptionSpinner()
        fallbackSuggestions = (
                BUILTIN_OUTBOUND_TAGS + SettingsManager.getProfileRemarks(
                    excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP)
                )
                ).filter { it != TAG_PROXY }

        initialRemarks = config?.remarks ?: ""
        initialFilter = config?.policyGroupFilter ?: ""
        initialType = config?.policyGroupType?.toIntOrNull() ?: 0
        initialTestOutbounds = config == null || config.policyGroupTestOutbounds != false ||
                !BalancerStrategyType.from(config.policyGroupType).supportsObservatory
        initialFallbackTag = config?.policyGroupFallbackTag.orEmpty()
        initialSubIndex = if (config != null) {
            subIds.indexOf(config.policyGroupSubscriptionId ?: "").let { if (it >= 0) it else 0 }
        } else if (subscriptionId.isNotNullEmpty()) {
            subIds.indexOf(subscriptionId).let { if (it >= 0) it else 0 }
        } else 0
    }

    @Composable
    override fun ScreenContent() {
        ServerGroupScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            subDisplay = subDisplay,
            initialRemarks = initialRemarks,
            initialFilter = initialFilter,
            initialType = initialType,
            initialSubIndex = initialSubIndex,
            initialTestOutbounds = initialTestOutbounds,
            initialFallbackTag = initialFallbackTag,
            fallbackSuggestions = fallbackSuggestions,
            onBackClick = { finish() },
            onSave = { remarks, filter, typeIdx, subIdx, testOutbounds, fallbackTag ->
                saveServer(remarks, filter, typeIdx, subIdx, testOutbounds, fallbackTag)
            },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(
        remarks: String,
        filter: String,
        typeIdx: Int,
        subIdx: Int,
        testOutbounds: Boolean,
        fallbackTag: String,
    ): Boolean {
        if (remarks.isBlank()) {
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.POLICYGROUP)

        config.remarks = remarks.trim()
        config.policyGroupFilter = filter.trim()
        config.policyGroupType = typeIdx.toString()
        config.policyGroupSubscriptionId =
            subIds.getOrNull(subIdx)
        config.policyGroupTestOutbounds = testOutbounds
        config.policyGroupFallbackTag = fallbackTag.trim().takeIf { it.isNotEmpty() }

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        val typeDisplay =
            stringArrayPolicyGroupType()
                .getOrNull(typeIdx)
                .orEmpty()

        config.description = buildString {
            append(typeDisplay)
            append(" - ")
            append(subDisplay.getOrNull(subIdx).orEmpty())
            append(" - ")
            append(config.policyGroupFilter)
        }

        val savedGuid = MmkvManager.encodeServerConfig(
            editGuid,
            config
        )

        toastSuccess(R.string.toast_success)

        ProfileEditorResult.run {
            finishSaved(
                guid = savedGuid,
                restartService = isRunning
            )
        }

        return true
    }

    private fun deleteServer(): Boolean {
        if (editGuid.isEmpty()) {
            return false
        }

        if (editGuid == MmkvManager.getSelectServer()) {
            toast(R.string.toast_action_not_allowed)
            return false
        }

        MmkvManager.removeServer(editGuid)

        ProfileEditorResult.run {
            finishDeleted(editGuid)
        }

        return true
    }

    private fun populateSubscriptionSpinner() {
        val subs = MmkvManager.decodeSubscriptions()
        subIds.clear()
        subDisplay.clear()
        subDisplay.add(getString(R.string.filter_config_all))
        subIds.add("")
        subs.forEach { sub ->
            val name = when {
                sub.subscription.remarks.isNotBlank() -> sub.subscription.remarks
                else -> sub.guid
            }
            subDisplay.add(name)
            subIds.add(sub.guid)
        }
    }

    private fun stringArrayPolicyGroupType(): Array<String> =
        resources.getStringArray(R.array.policy_group_type)
}

@Composable
fun ServerGroupScreen(
    editGuid: String,
    isRunning: Boolean,
    subDisplay: List<String>,
    initialRemarks: String,
    initialFilter: String,
    initialType: Int,
    initialSubIndex: Int,
    initialTestOutbounds: Boolean,
    initialFallbackTag: String,
    fallbackSuggestions: List<String>,
    onBackClick: () -> Unit,
    onSave: (String, String, Int, Int, Boolean, String) -> Boolean,
    onDelete: () -> Unit
) {
    val typeEntries = stringArrayResource(R.array.policy_group_type).toList()

    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var typeValue by rememberSaveable { mutableStateOf(typeEntries.getOrNull(initialType).orEmpty()) }
    var subValue by rememberSaveable { mutableStateOf(subDisplay.getOrNull(initialSubIndex).orEmpty()) }
    var testOutbounds by rememberSaveable { mutableStateOf(initialTestOutbounds) }
    var fallbackTag by rememberSaveable { mutableStateOf(initialFallbackTag) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showDelete = editGuid.isNotEmpty() && !isRunning
    val selectedType = typeEntries.indexOf(typeValue).coerceAtLeast(0).toString()
    val supportsObservatory = BalancerStrategyType.from(selectedType).supportsObservatory

    val onSaveClick = {
        val remarksErr = remarks.isBlank()
        isRemarksError = remarksErr

        if (!remarksErr) {
            val typeIdx = typeEntries.indexOf(typeValue).coerceAtLeast(0)
            val subIdx = subDisplay.indexOf(subValue).coerceAtLeast(0)
            onSave(remarks, filter, typeIdx, subIdx, testOutbounds, fallbackTag)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = EConfigType.POLICYGROUP.toString(),
                onBackClick = onBackClick,
                actions = {
                    if (showDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.acc_delete))
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Same plate as the protocol editors: one filled action, the page edge as the shape.
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
                    if (showDelete) {
                        FrostTextButton(
                            label = stringResource(R.string.acc_delete),
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FrostSpacing.screenH)
                    .padding(top = FrostSpacing.l, bottom = FrostSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap)
            ) {
                FrostFormSection(title = stringResource(R.string.server_section_main)) {
                    FormTextField(
                        label = stringResource(R.string.server_lab_remarks),
                        value = remarks,
                        onValueChange = { remarks = it },
                        isError = isRemarksError,
                        supportingText = if (isRemarksError) {
                            stringResource(R.string.server_field_required)
                        } else {
                            null
                        }
                    )
                    FormDropdownField(
                        label = stringResource(R.string.title_policy_group_type),
                        value = typeValue,
                        options = typeEntries,
                        onValueChange = { typeValue = it }
                    )
                    FormDropdownField(
                        label = stringResource(R.string.title_policy_group_subscription_id),
                        value = subValue,
                        options = subDisplay,
                        onValueChange = { subValue = it }
                    )
                    FormTextField(stringResource(R.string.title_policy_group_subscription_filter), filter, { filter = it })
                }
                if (supportsObservatory) {
                    FrostFormSection(title = stringResource(R.string.title_policy_group_test_outbounds)) {
                        SettingsSwitchItem(
                            title = stringResource(R.string.title_policy_group_test_outbounds),
                            checked = testOutbounds,
                            onCheckedChange = { testOutbounds = it }
                        )
                        if (testOutbounds) {
                            FormDropdownField(
                                label = stringResource(R.string.title_policy_group_fallback),
                                value = fallbackTag,
                                options = fallbackSuggestions,
                                onValueChange = { fallbackTag = it },
                                editable = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_policy_group),
            itemName = initialRemarks,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
