package com.brukb.zerotier.connection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkDebouncerTest {
    @Test
    fun trailingLastWins() = runTest {
        var runs = 0
        val debouncer = LinkDebouncer(this, delayMs = { 500 }) { runs++ }

        debouncer.trigger()
        runCurrent()
        advanceTimeBy(100)
        debouncer.trigger()
        runCurrent()
        advanceTimeBy(499)
        assertEquals(0, runs)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, runs)
    }

    @Test
    fun secondBurstAfterQuietRunsAgain() = runTest {
        var runs = 0
        val debouncer = LinkDebouncer(this, delayMs = { 500 }) { runs++ }

        debouncer.trigger()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, runs)

        debouncer.trigger()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, runs)
    }

    @Test
    fun cancelBeforeDelayZeroRuns() = runTest {
        var runs = 0
        val debouncer = LinkDebouncer(this, delayMs = { 500 }) { runs++ }

        debouncer.trigger()
        runCurrent()
        advanceTimeBy(100)
        debouncer.cancel()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(0, runs)
    }

    @Test
    fun delayMsReadAtTrigger() = runTest {
        var delayValue = 1000L
        var runs = 0
        val debouncer = LinkDebouncer(this, delayMs = { delayValue }) { runs++ }

        debouncer.trigger()
        runCurrent()
        delayValue = 200L
        advanceTimeBy(200)
        runCurrent()
        assertEquals(0, runs)

        debouncer.trigger()
        runCurrent()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, runs)
    }
}
