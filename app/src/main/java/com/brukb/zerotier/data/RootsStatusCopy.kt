package com.brukb.zerotier.data

object RootsStatusCopy {
    fun waitingMessage(
        source: LivePlanetSource,
        wentOffline: Boolean,
        dummyStarting: Boolean,
        lanOk: String,
        earthOffline: String,
    ): String? {
        val dummyLan = source == LivePlanetSource.DUMMY && (wentOffline || dummyStarting)
        return when {
            dummyLan -> lanOk
            wentOffline -> earthOffline
            else -> null
        }
    }
}
