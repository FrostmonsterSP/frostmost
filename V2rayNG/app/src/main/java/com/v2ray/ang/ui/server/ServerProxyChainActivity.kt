package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.FrostChip
import com.v2ray.ang.ui.compose.FrostElevation
import com.v2ray.ang.ui.compose.FrostFormSection
import com.v2ray.ang.ui.compose.FrostPrimaryButton
import com.v2ray.ang.ui.compose.FrostRadius
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.FrostSurface
import com.v2ray.ang.ui.compose.FrostTextButton
import com.v2ray.ang.ui.compose.frostCardColors
import com.v2ray.ang.ui.compose.frostInnerColors
import com.v2ray.ang.ui.compose.reorderableDragHandle
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

class ServerProxyChainActivity : BaseComponentActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private lateinit var allRemarks: List<String>
    private lateinit var initialRemarks: String
    private lateinit var initialMembers: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        allRemarks = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
        )
        val config = MmkvManager.decodeServerConfig(editGuid)
        initialRemarks = config?.remarks ?: ""
        initialMembers = config?.proxyChainProfiles?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("", "")
    }

    @Composable
    override fun ScreenContent() {
        ProxyChainScreen(
            editGuid = editGuid,
            isRunning = isRunning,
            initialRemarks = initialRemarks,
            initialMembers = initialMembers,
            allRemarks = allRemarks,
            onBackClick = { finish() },
            onSave = { remarks, members -> saveServer(remarks, members) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(
        remarks: String,
        members: List<String>
    ): Boolean {
        if (remarks.isBlank()) {
            return false
        }

        val chainMembers = members
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (chainMembers.size != members.size) {
            toast(R.string.server_proxy_chain_members_unselected)
            return false
        }

        if (chainMembers.size < 2) {
            toast(R.string.server_proxy_chain_members_insufficient)
            return false
        }

        val invalidMembers = chainMembers.filter { member ->
            val profile = SettingsManager.getServerViaRemarks(member)
            profile == null || profile.configType.isComplexType()
        }

        if (invalidMembers.isNotEmpty()) {
            toast(
                getString(
                    R.string.server_proxy_chain_members_invalid,
                    invalidMembers.joinToString(", ")
                )
            )
            return false
        }

        val config =
            MmkvManager.decodeServerConfig(editGuid)
                ?: ProfileItem.create(EConfigType.PROXYCHAIN)

        config.remarks = remarks.trim()
        config.proxyChainProfiles =
            chainMembers.joinToString(",")

        config.description =
            chainMembers.joinToString(" -> ")

        if (
            config.subscriptionId.isEmpty() &&
            !subscriptionId.isNullOrEmpty()
        ) {
            config.subscriptionId = subscriptionId.orEmpty()
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
}

@Composable
fun ProxyChainScreen(
    editGuid: String,
    isRunning: Boolean,
    initialRemarks: String,
    initialMembers: List<String>,
    allRemarks: List<String>,
    onBackClick: () -> Unit,
    onSave: (String, List<String>) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by rememberSaveable { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable { mutableStateOf(false) }
    var members by rememberSaveable { mutableStateOf(initialMembers) }
    var memberKeys by rememberSaveable { mutableStateOf(List(initialMembers.size) { UUID.randomUUID().toString() }) }
    var showProfileDeleteConfirm by remember { mutableStateOf(false) }
    var memberToDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    val showDelete = editGuid.isNotEmpty() && !isRunning

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = memberKeys.indexOf(from.key)
        val toIndex = memberKeys.indexOf(to.key)
        if (fromIndex != -1 && toIndex != -1) {
            val reordered = members.toMutableList()
            val reorderedKeys = memberKeys.toMutableList()
            if (reordered.moveItem(fromIndex, toIndex)) {
                reorderedKeys.moveItem(fromIndex, toIndex)
                members = reordered
                memberKeys = reorderedKeys
            }
        }
    }

    val onSaveClick = {
        val remarksErr = remarks.isBlank()
        isRemarksError = remarksErr

        if (!remarksErr) {
            onSave(remarks, members)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = EConfigType.PROXYCHAIN.toString(),
                onBackClick = onBackClick,
                actions = {
                    if (showDelete) {
                        IconButton(onClick = { showProfileDeleteConfirm = true }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.acc_delete))
                        }
                    }
                }
            )
        },
        bottomBar = {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FrostTextButton(
                            label = stringResource(R.string.acc_add_member),
                            onClick = {
                                members = members + ""
                                memberKeys = memberKeys + UUID.randomUUID().toString()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (showDelete) {
                            FrostTextButton(
                                label = stringResource(R.string.acc_delete),
                                onClick = { showProfileDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScrollbar(lazyListState),
            contentPadding = PaddingValues(
                start = FrostSpacing.screenH,
                end = FrostSpacing.screenH,
                top = FrostSpacing.l,
                bottom = FrostSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap),
        ) {
            item(key = "remarks_field") {
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
                }
            }

            item(key = "members_header") {
                Text(
                    text = stringResource(R.string.server_section_chain_members),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = FrostSpacing.xs),
                )
            }

            itemsIndexed(items = members, key = { index, _ -> memberKeys[index] }) { index, member ->
                val memberKey = memberKeys[index]
                ReorderableItem(reorderableState, key = memberKey) { isDragging ->
                    val elevation by animateDpAsState(
                        if (isDragging) FrostElevation.action else FrostElevation.card
                    )
                    FrostSurface(
                        colors = frostInnerColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = FrostRadius.inner,
                        elevation = elevation,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(with(this) { reorderableDragHandle() })
                                .padding(
                                    start = FrostSpacing.m,
                                    end = FrostSpacing.xs,
                                    top = FrostSpacing.xs,
                                    bottom = FrostSpacing.xs,
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The step number is the chain order, which is the whole meaning of the
                            // number, so it stays visible next to the field rather than inside it.
                            FrostChip(
                                label = "${index + 1}",
                                modifier = Modifier.padding(end = FrostSpacing.s),
                            )
                            FormDropdownField(
                                label = stringResource(R.string.server_lab_remarks),
                                placeholder = stringResource(R.string.server_proxy_chain_member_unselected),
                                value = member,
                                options = allRemarks,
                                onValueChange = { newVal ->
                                    members = members.toMutableList().also { it[index] = newVal }
                                },
                                editable = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (member.isBlank()) {
                                    val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, memberKeys, memberKey)
                                    members = remainingMembers
                                    memberKeys = remainingKeys
                                } else {
                                    memberToDeleteKey = memberKey
                                }
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.acc_remove)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_profile),
            itemName = initialRemarks,
            onConfirm = { showProfileDeleteConfirm = false; onDelete() },
            onDismiss = { showProfileDeleteConfirm = false }
        )
    }
    memberToDeleteKey?.let { memberKey ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_proxy_chain_member),
            itemName = members.getOrNull(memberKeys.indexOf(memberKey)).orEmpty(),
            onConfirm = {
                val (remainingMembers, remainingKeys) = withoutProxyChainMember(members, memberKeys, memberKey)
                members = remainingMembers
                memberKeys = remainingKeys
                memberToDeleteKey = null
            },
            onDismiss = { memberToDeleteKey = null }
        )
    }
}
