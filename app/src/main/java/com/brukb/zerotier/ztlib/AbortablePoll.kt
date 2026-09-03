package com.brukb.zerotier.ztlib

import kotlinx.coroutines.delay

enum class PollUntilResult {
    Yes,
    Timeout,
    Aborted,
}

suspend fun pollUntil(
    timeoutMs: Long,
    periodMs: Long,
    shouldAbort: () -> Boolean,
    nowMs: () -> Long,
    predicate: suspend () -> Boolean,
): PollUntilResult {
    val deadline = nowMs() + timeoutMs
    while (true) {
        if (shouldAbort()) return PollUntilResult.Aborted
        if (predicate()) return PollUntilResult.Yes
        if (nowMs() >= deadline) return PollUntilResult.Timeout
        delay(periodMs)
    }
}
