package com.brukb.zerotier.proxy

object NodeRetryPolicy {
    const val JOIN_READY_TIMEOUT_MS = 30_000L
    const val BACKOFF_INITIAL_MS = 1_000L
    const val BACKOFF_CAP_MS = 30_000L

    /** previous=0 → 1000; then 5000, 15000, 30000, 30000… */
    fun nextBackoffMs(previousMs: Long): Long = when {
        previousMs <= 0L -> BACKOFF_INITIAL_MS
        previousMs < 5_000L -> 5_000L
        previousMs < 15_000L -> 15_000L
        else -> BACKOFF_CAP_MS
    }
}
