package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Side effects the model asks its owner to perform; implemented by the ViewModel over its data source. */
internal interface SwitchPorts {
    /** Restarts the active daemon (same message in VPN, proxy-only and root mode). */
    fun restart()

    /** Persists [guid] as the selected server and publishes it to the UI state. */
    fun applyServer(guid: String)
}

/**
 * Main-thread decisions for switching subscriptions while connected, connection progress and home
 * issues. Pure of Android so it can be tested on the JVM. [scope] must be the ViewModel's scope.
 *
 * The undo window lives only in memory: process death or ViewModel recreation inside the window
 * drops it. The new group and server are already persisted by then, so the group stays switched
 * but the running core is not restarted until the next restart.
 */
internal class HomeSwitchModel(
    scope: CoroutineScope,
    delayMillis: Long = 3_000,
    private val ports: SwitchPorts,
) {
    /** Group and server selected before the undoable switch; ids only, never list positions. */
    data class Origin(val groupId: String, val guid: String?)

    data class Snapshot(
        /** True from a start or restart request until the daemon reports success, failure or stop. */
        val isConnecting: Boolean = false,
        val homeIssue: HomeIssue? = null,
    )

    private val pending = PendingSwitch(scope, delayMillis) { onCommit() }
    val pendingId: StateFlow<String?> = pending.pendingId

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    var isRunning: Boolean = false
        private set
    var origin: Origin? = null
        private set

    /** A restart was requested and its StopSuccess has not arrived; that stop is not a user stop. */
    private var restartStopPending = false

    private val live: Boolean get() = isRunning || _state.value.isConnecting

    // ---------- Switching ----------

    /**
     * Call when the user selects another group. Drops any open window (the old delayed commit must
     * never fire) and keeps the first origin, so one undo returns to where the user started.
     */
    fun beginSwitch(currentGroupId: String, currentGuid: String?) {
        pending.cancel()
        if (live) {
            if (origin == null) origin = Origin(currentGroupId, currentGuid)
        } else {
            origin = null
        }
    }

    /** The target group's server was resolved; null means the group has no server. */
    fun onSwitchResolved(groupId: String, resolved: String?) {
        val o = origin
        if (o == null) {
            resolved?.let(::applyServer)
            return
        }
        if (resolved == null || resolved == o.guid) {
            // The core already runs this server (or there is none to run): restarting would only drop the connection.
            pending.cancel()
            origin = null
            o.guid?.let(::applyServer)
            return
        }
        applyServer(resolved)
        pending.start(groupId)
    }

    /** Returns the origin to restore, or null when there is nothing to undo or its group is gone. */
    fun beginUndo(groupExists: (String) -> Boolean): Origin? {
        val o = origin
        if (o == null || pending.pendingId.value == null) return null
        pending.cancel()
        origin = null
        return o.takeIf { groupExists(it.groupId) }
    }

    /** [resolved] is the origin group's server after re-resolving the origin guid; null if it has none. */
    fun onUndoResolved(origin: Origin, resolved: String?) {
        resolved ?: return
        applyServer(resolved)
        // The core still runs the origin server; if that server is gone the core must follow the new choice.
        if (resolved != origin.guid) restartNow()
    }

    /** A deliberate server pick replaces the window: undo would no longer mean anything. */
    fun onManualSelect() {
        pending.cancel()
        origin = null
    }

    fun restartIfLive() {
        if (live) restartNow()
    }

    fun cancelSwitch() {
        pending.cancel()
        origin = null
    }

    private fun onCommit() {
        origin = null
        restartNow()
    }

    private fun restartNow() {
        restartStopPending = true
        _state.update { it.copy(isConnecting = true) }
        ports.restart()
    }

    private fun applyServer(guid: String) {
        ports.applyServer(guid)
        clearConnectionIssue()
    }

    // ---------- Daemon state ----------

    fun onRunning(running: Boolean) {
        isRunning = running
        if (!running && !_state.value.isConnecting) cancelSwitch()
    }

    fun onStartSuccess() {
        restartStopPending = false
        isRunning = true
        _state.update { Snapshot(isConnecting = false, homeIssue = it.homeIssue.dropConnectionIssue()) }
    }

    fun onStartFailure() {
        restartStopPending = false
        isRunning = false
        cancelSwitch()
        _state.value = Snapshot(isConnecting = false, homeIssue = HomeIssue.ServerUnreachable)
    }

    fun onStopSuccess() {
        val wasRunning = isRunning
        val restartStop = restartStopPending
        restartStopPending = false
        isRunning = false
        if (!restartStop) {
            cancelSwitch()
            _state.update { it.copy(isConnecting = false) }
        }
        // StartFailure also ends in StopSuccess; its issue must survive, so clear only a connection that was up.
        if (wasRunning) clearConnectionIssue()
    }

    /** The user turned the connection off: a pending restart-stop will not be followed by a start. */
    fun onUserStop() {
        restartStopPending = false
        cancelSwitch()
        _state.update { it.copy(isConnecting = false) }
    }

    fun beginConnecting() {
        _state.update { Snapshot(isConnecting = true, homeIssue = null) }
    }

    /** The start request never reached the daemon (setup threw before the service started). */
    fun onStartRejected() {
        _state.update { it.copy(isConnecting = false) }
    }

    // ---------- Issues ----------

    fun onDelayResult(delayMillis: Long) {
        if (isServerUnreachable(delayMillis)) {
            _state.update { it.copy(homeIssue = HomeIssue.ServerUnreachable) }
        } else {
            clearConnectionIssue()
        }
    }

    fun onUpdateResult(failure: SubscriptionFailure?, lastUpdated: Long) {
        _state.update { it.copy(homeIssue = subscriptionUpdateIssue(failure, lastUpdated, it.homeIssue)) }
    }

    fun onPermissionDenied() {
        _state.update { Snapshot(isConnecting = false, homeIssue = HomeIssue.VpnPermissionDenied) }
    }

    fun clearIssue() {
        _state.update { it.copy(homeIssue = null) }
    }

    private fun clearConnectionIssue() {
        _state.update { it.copy(homeIssue = it.homeIssue.dropConnectionIssue()) }
    }

    private fun HomeIssue?.dropConnectionIssue(): HomeIssue? = takeUnless { it.isConnectionIssue() }
}
