package com.brukb.zerotier.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LinkDebouncer(
    private val scope: CoroutineScope,
    private val delayMs: () -> Long,
    private val action: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var job: Job? = null

    fun trigger() {
        val wait = delayMs()
        scope.launch {
            mutex.withLock {
                job?.cancel()
                job = scope.launch {
                    delay(wait)
                    action()
                }
            }
        }
    }

    suspend fun cancel() {
        mutex.withLock {
            job?.cancel()
            job?.join()
            job = null
        }
    }
}
