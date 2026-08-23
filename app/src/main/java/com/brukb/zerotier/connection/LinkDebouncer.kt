package com.brukb.zerotier.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LinkDebouncer(
    private val scope: CoroutineScope,
    private val delayMs: suspend () -> Long,
    private val action: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var job: Job? = null

    fun trigger() {
        scope.launch {
            mutex.withLock {
                job?.cancel()
                job = scope.launch {
                    val wait = delayMs()
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
