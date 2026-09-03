package com.brukb.zerotier.ztlib

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AbortablePollTest {
    @Test
    fun predicateTrueImmediately() = runTest {
        val result = pollUntil(
            timeoutMs = 1_000L,
            periodMs = 50L,
            shouldAbort = { false },
            nowMs = { 0L },
            predicate = { true },
        )
        assertEquals(PollUntilResult.Yes, result)
    }

    @Test
    fun shouldAbortReturnsAborted() = runTest {
        val result = pollUntil(
            timeoutMs = 1_000L,
            periodMs = 50L,
            shouldAbort = { true },
            nowMs = { 0L },
            predicate = { true },
        )
        assertEquals(PollUntilResult.Aborted, result)
    }

    @Test
    fun timeoutWhenPredicateNeverTrue() = runTest(StandardTestDispatcher()) {
        var now = 0L
        val result = async {
            pollUntil(
                timeoutMs = 100L,
                periodMs = 50L,
                shouldAbort = { false },
                nowMs = { now },
                predicate = { false },
            )
        }
        advanceTimeBy(50)
        now = 50
        runCurrent()
        advanceTimeBy(50)
        now = 100
        runCurrent()
        assertEquals(PollUntilResult.Timeout, result.await())
    }

    @Test
    fun predicateTrueAfterDelay() = runTest(StandardTestDispatcher()) {
        var now = 0L
        var ready = false
        val result = async {
            pollUntil(
                timeoutMs = 500L,
                periodMs = 50L,
                shouldAbort = { false },
                nowMs = { now },
                predicate = { ready },
            )
        }
        advanceTimeBy(50)
        now = 50
        runCurrent()
        ready = true
        advanceTimeBy(50)
        now = 100
        runCurrent()
        assertEquals(PollUntilResult.Yes, result.await())
    }
}
