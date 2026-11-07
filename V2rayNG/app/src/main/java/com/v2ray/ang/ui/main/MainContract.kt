package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.enums.RoutingType

/** Locale-neutral state formatted only when it reaches the main UI. */
sealed interface MainStatus {
    data object Disconnected : MainStatus
    data object Connected : MainStatus
    data object Testing : MainStatus
    data class TestProgress(val progress: String) : MainStatus
    data class ConnectionTest(val result: ConnectionTestResult) : MainStatus
}

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val status: MainStatus = MainStatus.Disconnected,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    /** Group id of a switch made while connected that can still be undone; null when none. */
    val undoTargetId: String? = null,
    val homeIssue: HomeIssue? = null,
    /** True from the user's start request until the service reports success or failure. */
    val isConnecting: Boolean = false,
    /** Subscription URL per group id, from the same load as [groups]; empty for a manual group. */
    val subscriptionUrls: Map<String, String> = emptyMap(),
    /** Routing preset the current rules equal; null means custom rules (or not loaded yet, see [routingPresetLoaded]). */
    val routingPreset: RoutingType? = null,
    val routingPresetLoaded: Boolean = false,
    /** Rules replacement waiting for the user's confirmation; null when none. */
    val pendingRules: PendingRules? = null,
)

/** A replacement of the routing rules that needs the "existing rules will be deleted" confirmation. */
sealed interface PendingRules {
    data class Preset(val type: RoutingType) : PendingRules
    data class Import(val json: String) : PendingRules
}

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data class UpdateGroupSubscription(val groupId: String) : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.v2ray.ang.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction
    data class OpenSubscriptionLink(val url: String) : MainAction

    /** Imports pasted, scanned or file text: a subscription, configurations, or routing rules (after confirmation). */
    data class ApplyImport(val text: String) : MainAction
    data class ApplyRoutingPreset(val type: RoutingType) : MainAction
    data object ConfirmRules : MainAction
    data object DismissRules : MainAction

    data object LocateHandled : MainAction

    data class SwitchSubscription(val groupId: String) : MainAction
    data object UndoSwitch : MainAction
    data object VpnPermissionDenied : MainAction
    data object ClearHomeIssue : MainAction
}
