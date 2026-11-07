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
    fun unreachableOnlyWhenNoPositiveDelay() {
        assertTrue(isServerUnreachable(0))
        assertTrue(isServerUnreachable(-1))
        assertTrue(!isServerUnreachable(120))
    }

    @Test
    fun staleConnectionIssuesAreRecognised() {
        assertTrue(HomeIssue.ServerUnreachable.isConnectionIssue())
        assertTrue(HomeIssue.VpnPermissionDenied.isConnectionIssue())
        assertTrue(!HomeIssue.Expired.isConnectionIssue())
        assertTrue(!HomeIssue.UpdateFailed("a", 1).isConnectionIssue())
        assertTrue(!(null as HomeIssue?).isConnectionIssue())
    }

    @Test
    fun updateFailureRaisesAndSuccessClearsOnlyThatIssue() {
        val f = com.v2ray.ang.dto.SubscriptionFailure.DOWNLOAD
        assertEquals(HomeIssue.UpdateFailed("a", 5), subscriptionUpdateIssue("a", f, 5, null))
        assertNull(subscriptionUpdateIssue("a", null, -1, HomeIssue.UpdateFailed("a", 5)))
        assertEquals(HomeIssue.ServerUnreachable, subscriptionUpdateIssue("a", null, -1, HomeIssue.ServerUnreachable))
        assertNull(subscriptionUpdateIssue("a", null, -1, null))
        // Another group's success leaves A's failure; a later failure of B replaces it.
        assertEquals(HomeIssue.UpdateFailed("a", 5), subscriptionUpdateIssue("b", null, -1, HomeIssue.UpdateFailed("a", 5)))
        assertEquals(HomeIssue.UpdateFailed("b", 9), subscriptionUpdateIssue("b", f, 9, HomeIssue.UpdateFailed("a", 5)))
        // "Update all": a success clears any group's failure; a failure is attributed to the given group with no date.
        assertNull(subscriptionUpdateIssue("b", null, -1, HomeIssue.UpdateFailed("a", 5), allGroups = true))
        assertEquals(HomeIssue.UpdateFailed("b", -1), subscriptionUpdateIssue("b", f, -1, null, allGroups = true))
        assertEquals(HomeIssue.ServerUnreachable, subscriptionUpdateIssue("b", null, -1, HomeIssue.ServerUnreachable, allGroups = true))
    }
}
