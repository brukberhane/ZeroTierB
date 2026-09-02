package com.brukb.zerotier.proxy

import com.brukb.zerotier.log.AppLog

/** Single logcat tag for proxy/DNS/route traces. Filter: `adb logcat -s ZtProxyDbg` */
object ProxyDebugLog {
    const val TAG = "ZtProxyDbg"

    fun i(msg: String) {
        AppLog.i(TAG, msg)
    }

    fun w(msg: String, err: Throwable? = null) {
        AppLog.w(TAG, msg, err)
    }
}
