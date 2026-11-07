package com.v2ray.ang.ui.main

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingSwitchTest {

    @Test
    fun commitsAfterDelay() = runTest {
        val done = mutableListOf<String>()
        val s = PendingSwitch(backgroundScope, 3_000) { done += it }
        s.start("b"); advanceTimeBy(2_999); assertTrue(done.isEmpty())
        assertEquals("b", s.pendingId.value)
        advanceTimeBy(2); assertEquals(listOf("b"), done)
        assertNull(s.pendingId.value)
    }

    @Test
    fun cancelStopsCommit() = runTest {
        val done = mutableListOf<String>()
        val s = PendingSwitch(backgroundScope, 3_000) { done += it }
        s.start("b"); s.cancel(); advanceUntilIdle()
        assertTrue(done.isEmpty()); assertNull(s.pendingId.value)
    }

    @Test
    fun newStartReplacesPrevious() = runTest {
        val done = mutableListOf<String>()
        val s = PendingSwitch(backgroundScope, 3_000) { done += it }
        s.start("b"); advanceTimeBy(2_000); s.start("c"); advanceTimeBy(3_001)
        assertEquals(listOf("c"), done)
    }

    @Test
    fun cancelInsideWindowAfterReplacementStopsEverything() = runTest {
        val done = mutableListOf<String>()
        val s = PendingSwitch(backgroundScope, 3_000) { done += it }
        s.start("b"); advanceTimeBy(1_000); s.start("c"); advanceTimeBy(1_000)
        s.cancel(); advanceUntilIdle()
        assertTrue(done.isEmpty()); assertNull(s.pendingId.value)
    }

    @Test
    fun canStartAgainAfterCommitAndAfterCancel() = runTest {
        val done = mutableListOf<String>()
        val s = PendingSwitch(backgroundScope, 3_000) { done += it }
        s.start("b"); advanceTimeBy(3_001)
        s.start("c"); s.cancel()
        s.start("d"); advanceTimeBy(3_001)
        assertEquals(listOf("b", "d"), done)
    }

    @Test
    fun cancelWithNothingPendingIsNoOp() = runTest {
        val s = PendingSwitch(backgroundScope, 3_000) { }
        s.cancel(); assertNull(s.pendingId.value)
    }
}

class HomeIssueDecisionsTest {

    @Test
    fun unreachableWhenNoPositiveDelayOrErrorPresent() {
        assertTrue(isServerUnreachable(0, ""))
        assertTrue(isServerUnreachable(-1, ""))
        assertTrue(isServerUnreachable(120, "timeout"))
        assertTrue(!isServerUnreachable(120, ""))
    }

    @Test
    fun staleConnectionIssuesAreRecognised() {
        assertTrue(HomeIssue.ServerUnreachable.isConnectionIssue())
        assertTrue(HomeIssue.VpnPermissionDenied.isConnectionIssue())
        assertTrue(!HomeIssue.Expired.isConnectionIssue())
        assertTrue(!HomeIssue.UpdateFailed(1).isConnectionIssue())
        assertTrue(!(null as HomeIssue?).isConnectionIssue())
    }

    @Test
    fun updateFailureRaisesAndSuccessClearsOnlyThatIssue() {
        val f = com.v2ray.ang.dto.SubscriptionFailure.DOWNLOAD
        assertEquals(HomeIssue.UpdateFailed(5), subscriptionUpdateIssue(f, 5, null))
        assertNull(subscriptionUpdateIssue(null, -1, HomeIssue.UpdateFailed(5)))
        assertEquals(HomeIssue.ServerUnreachable, subscriptionUpdateIssue(null, -1, HomeIssue.ServerUnreachable))
        assertNull(subscriptionUpdateIssue(null, -1, null))
    }
}
