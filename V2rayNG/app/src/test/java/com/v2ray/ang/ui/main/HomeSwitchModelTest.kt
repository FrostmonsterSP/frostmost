package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSwitchModelTest {

    private class Ports : SwitchPorts {
        var restarts = 0
        val applied = mutableListOf<String>()
        override fun restart() { restarts++ }
        override fun applyServer(guid: String) { applied += guid }
    }

    private fun TestScope.model(ports: Ports) = HomeSwitchModel(backgroundScope, 3_000, ports)

    /** Connected on group "a" / server "sa". */
    private fun HomeSwitchModel.connected() { onStartSuccess() }

    @Test
    fun swipeWhileConnectedCommitsOneRestartAfterWindow() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        assertEquals("b", m.pendingId.value)
        advanceTimeBy(2_999); assertEquals(0, p.restarts)
        advanceTimeBy(2); assertEquals(1, p.restarts)
        assertNull(m.pendingId.value)
        assertTrue(m.state.value.isConnecting)
    }

    @Test
    fun swipeWhileDisconnectedOnlySelects() = runTest {
        val p = Ports(); val m = model(p)
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        advanceUntilIdle()
        assertEquals(listOf("sb"), p.applied); assertEquals(0, p.restarts)
        assertNull(m.pendingId.value); assertNull(m.origin)
    }

    @Test
    fun turningVpnOffInsideWindowCancelsTheDelayedSwitch() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        advanceTimeBy(1_000)
        m.onStopSuccess()
        advanceUntilIdle()
        assertEquals(0, p.restarts); assertNull(m.pendingId.value); assertNull(m.origin)
        assertFalse(m.state.value.isConnecting)
    }

    @Test
    fun notRunningReportCancelsTheDelayedSwitch() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        m.onRunning(false); advanceUntilIdle()
        assertEquals(0, p.restarts); assertNull(m.pendingId.value)
    }

    @Test
    fun secondSwipeKeepsFirstOriginAndReplacesWindow() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        advanceTimeBy(2_000)
        m.beginSwitch("b", "sb")
        assertNull(m.pendingId.value) // old window dropped at once
        assertEquals(HomeSwitchModel.Origin("a", "sa"), m.origin)
        m.onSwitchResolved("c", "sc")
        advanceTimeBy(3_001)
        assertEquals(1, p.restarts)
        assertEquals(listOf("sb", "sc"), p.applied)
    }

    @Test
    fun swipeAwayAndBackNeverRestarts() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        m.beginSwitch("b", "sb"); m.onSwitchResolved("a", "sa")
        advanceUntilIdle()
        assertEquals(0, p.restarts); assertNull(m.pendingId.value); assertNull(m.origin)
        assertEquals("sa", p.applied.last()) // selection returns to the server the core runs
    }

    @Test
    fun targetGroupWithoutServersDoesNotRestart() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("empty", null)
        advanceUntilIdle()
        assertEquals(0, p.restarts); assertNull(m.pendingId.value)
        assertEquals(listOf("sa"), p.applied) // selection stays on the server the core runs
    }

    @Test
    fun swipeBackFromEmptyGroupRestoresOriginServer() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        m.beginSwitch("b", "sb"); m.onSwitchResolved("empty", null)
        advanceUntilIdle()
        assertEquals(0, p.restarts); assertEquals("sa", p.applied.last())
    }

    @Test
    fun swipeWhileConnectingIsTreatedAsRunning() = runTest {
        val p = Ports(); val m = model(p)
        m.beginConnecting()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        assertEquals("b", m.pendingId.value)
        advanceTimeBy(3_001)
        assertEquals(1, p.restarts)
    }

    @Test
    fun swipeDuringRestartSurvivesTheRestartsStopSuccess() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.restartIfLive()                       // e.g. a manual server pick
        m.onStopSuccess()                       // stop half of that restart
        assertTrue(m.state.value.isConnecting)
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        m.onStartSuccess()
        assertFalse(m.state.value.isConnecting)
        advanceTimeBy(3_001)
        assertEquals(2, p.restarts)
    }

    @Test
    fun stopSuccessWithoutRestartResetsConnecting() = runTest {
        val p = Ports(); val m = model(p)
        m.beginConnecting()
        m.onStopSuccess() // failed VPN establish reports only StopSuccess
        assertFalse(m.state.value.isConnecting)
    }

    @Test
    fun userStopAfterCommitDoesNotLeaveConnectingStuck() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        advanceTimeBy(3_001)
        m.onUserStop(); m.onStopSuccess()
        assertFalse(m.state.value.isConnecting)
    }

    @Test
    fun startRejectedResetsConnecting() = runTest {
        val m = model(Ports())
        m.beginConnecting(); m.onStartRejected()
        assertFalse(m.state.value.isConnecting)
    }

    @Test
    fun undoRestoresOriginByIdAndCancelsCommit() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        val origin = m.beginUndo { it == "a" }!!
        assertEquals(HomeSwitchModel.Origin("a", "sa"), origin)
        m.onUndoResolved(origin, "sa")
        advanceUntilIdle()
        assertEquals("sa", p.applied.last()); assertEquals(0, p.restarts); assertNull(m.pendingId.value)
    }

    @Test
    fun undoWithDeletedOriginServerFallsBackAndRestarts() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        val origin = m.beginUndo { true }!!
        m.onUndoResolved(origin, "sa2") // sa was removed; first server of group a is sa2
        assertEquals("sa2", p.applied.last()); assertEquals(1, p.restarts)
    }

    @Test
    fun undoWithDeletedOriginGroupOnlyCancels() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        assertNull(m.beginUndo { false })
        advanceUntilIdle()
        assertEquals(0, p.restarts); assertNull(m.pendingId.value); assertNull(m.origin)
        assertEquals(listOf("sb"), p.applied)
    }

    @Test
    fun undoWithNothingPendingIsIgnored() = runTest {
        val m = model(Ports())
        assertNull(m.beginUndo { true })
    }

    @Test
    fun manualSelectDropsWindowAndRestartsOnce() = runTest {
        val p = Ports(); val m = model(p); m.connected()
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        m.onManualSelect(); m.restartIfLive()
        advanceUntilIdle()
        assertEquals(1, p.restarts); assertNull(m.pendingId.value)
    }

    @Test
    fun restartIfLiveDoesNothingWhenStopped() = runTest {
        val p = Ports(); val m = model(p)
        m.restartIfLive()
        assertEquals(0, p.restarts); assertFalse(m.state.value.isConnecting)
    }

    // ---------- issues ----------

    @Test
    fun startFailureIssueSurvivesTheFollowingStopSuccess() = runTest {
        val m = model(Ports())
        m.beginConnecting()
        m.onStartFailure(); m.onStopSuccess()
        assertEquals(HomeIssue.ServerUnreachable, m.state.value.homeIssue)
    }

    @Test
    fun stopSuccessOfAnEstablishedConnectionClearsConnectionIssue() = runTest {
        val m = model(Ports()); m.connected()
        m.onDelayResult(0)
        assertEquals(HomeIssue.ServerUnreachable, m.state.value.homeIssue)
        m.onStopSuccess()
        assertNull(m.state.value.homeIssue)
    }

    @Test
    fun startSuccessClearsStaleIssueAndGoodPingKeepsItClear() = runTest {
        val m = model(Ports())
        m.onStartFailure()
        m.onStartSuccess()
        assertNull(m.state.value.homeIssue)
        m.onDelayResult(80)
        assertNull(m.state.value.homeIssue)
        m.onDelayResult(-1)
        assertEquals(HomeIssue.ServerUnreachable, m.state.value.homeIssue)
        m.onDelayResult(80)
        assertNull(m.state.value.homeIssue)
    }

    @Test
    fun permissionDeniedIssueAndClear() = runTest {
        val m = model(Ports())
        m.beginConnecting(); m.onPermissionDenied()
        assertEquals(HomeIssue.VpnPermissionDenied, m.state.value.homeIssue)
        assertFalse(m.state.value.isConnecting)
        m.clearIssue(); assertNull(m.state.value.homeIssue)
    }

    @Test
    fun pickingAnotherServerDropsStaleUnreachable() = runTest {
        val m = model(Ports()); m.connected()
        m.onDelayResult(0)
        m.beginSwitch("a", "sa"); m.onSwitchResolved("b", "sb")
        assertNull(m.state.value.homeIssue)
    }

    @Test
    fun subscriptionFailureRaisesAndSuccessClearsOnlyThatIssue() = runTest {
        val m = model(Ports())
        m.onUpdateResult(SubscriptionFailure.DOWNLOAD, 7)
        assertEquals(HomeIssue.UpdateFailed(7), m.state.value.homeIssue)
        m.onUpdateResult(null, -1)
        assertNull(m.state.value.homeIssue)
    }
}
