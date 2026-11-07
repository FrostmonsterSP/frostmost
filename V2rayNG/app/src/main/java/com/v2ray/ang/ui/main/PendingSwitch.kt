package com.v2ray.ang.ui.main

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delayed commit of a subscription switch made while the VPN runs; the window lets the user undo it.
 * At most one switch is pending: [start] replaces the previous one and [cancel] drops it, so a stale
 * delayed commit can never fire. [scope] must be the owner's scope (the ViewModel's), so the pending
 * switch ends with its owner. [onCommit] runs on [scope] after the window and receives the group id.
 */
class PendingSwitch(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 3_000,
    private val onCommit: (String) -> Unit,
) {
    private val _pendingId = MutableStateFlow<String?>(null)
    val pendingId: StateFlow<String?> = _pendingId.asStateFlow()

    private var job: Job? = null

    fun start(targetId: String) {
        job?.cancel()
        _pendingId.value = targetId
        job = scope.launch {
            delay(delayMillis)
            // No suspension point from here on: a cancel() cannot interleave with the commit.
            _pendingId.value = null
            job = null
            onCommit(targetId)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _pendingId.value = null
    }
}

/**
 * A failed real-ping is `delayMillis <= 0`. The error text is ignored: the core keeps a non-empty
 * message after a successful fallback, so it cannot mean failure on its own.
 */
internal fun isServerUnreachable(delayMillis: Long): Boolean = delayMillis <= 0

/** Issues that describe the current connection attempt and go stale when the connection or server changes. */
internal fun HomeIssue?.isConnectionIssue(): Boolean =
    this == HomeIssue.ServerUnreachable || this == HomeIssue.VpnPermissionDenied

/**
 * A failed update raises [HomeIssue.UpdateFailed] for [groupId]. A successful one clears only a failed
 * update of the same group, or any failed update when [allGroups] (update all) succeeded.
 */
internal fun subscriptionUpdateIssue(
    groupId: String,
    failure: com.v2ray.ang.dto.SubscriptionFailure?,
    lastUpdated: Long,
    current: HomeIssue?,
    allGroups: Boolean = false,
): HomeIssue? = when {
    failure != null -> HomeIssue.UpdateFailed(groupId, lastUpdated)
    current is HomeIssue.UpdateFailed && (allGroups || current.groupId == groupId) -> null
    else -> current
}
