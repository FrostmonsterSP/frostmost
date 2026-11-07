package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionImportResult
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.RoutingType
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface MainDataSource : Closeable {
    val mainServiceEvent: Flow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun setSelectedSubscriptionId(id: String)

    fun getSelectServer(): String?
    /** Stores [guid] as the server the user picked in subscription [groupId]. */
    fun rememberServer(groupId: String, guid: String)
    fun setSelectServer(guid: String)

    fun getConfirmRemove(): Boolean
    fun getDoubleColumnDisplay(): Boolean
    fun isGroupAllDisplayEnabled(): Boolean

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String

    fun getSubscriptions(): List<SubscriptionCache>
    fun getSubscriptionItem(id: String): SubscriptionItem?

    fun getServerGuidList(groupId: String): List<String>
    fun decodeServerConfig(guid: String): ProfileItem?
    fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo?

    fun encodeServerList(guids: List<String>, groupId: String)

    fun removeServer(guid: String)
    fun removeAllServer(): Int
    fun removeInvalidServerByGuid(guid: String): Int
    fun removeInvalidServersInGroup(groupId: String): Int

    fun clearAllTestDelayResults(guids: List<String>)
    fun sortByTestResultsForSub(subId: String)
    fun getSubsList(): List<String>

    suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int>

    /** Blocking: downloads the subscription. Call off the main thread. */
    fun importSubscriptionUrl(url: String): SubscriptionImportResult
    fun isValidSubUrl(url: String): Boolean

    /** Replaces the rules (locked ones stay) with preset [type]. Reads assets: call off the main thread. */
    fun applyRoutingPreset(type: RoutingType)
    /** Replaces the rules (locked ones stay) with exported rules [json]; false when it holds no rules. */
    fun resetRoutingRulesets(json: String): Boolean

    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult

    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun share2QRCode(guid: String): android.graphics.Bitmap?
    fun share2Clipboard(guid: String): Boolean

    /**
     * Sends the restart command (same in VPN, proxy-only and root mode). [onResult] gets false when no
     * daemon received it, so a flag raised for the restart can be dropped.
     */
    fun restartService(onResult: (handled: Boolean) -> Unit)
    fun sendMsg2Service(msgId: Int, content: String)
    fun sendMsg2TestService(msg: TestServiceMessage, requestId: String? = null)
    fun cancelAllPing()
    fun testCurrentServerRealPing(requestId: String)

    /** Routing preset the current rules equal, or null for custom rules. Reads assets: call off the main thread. */
    fun currentRoutingPreset(): RoutingType?

    fun syncSubscriptions()
    fun initAssets()
}
