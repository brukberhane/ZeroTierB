package com.brukb.zerotier.proxy

import android.util.Log

/** Single logcat tag for proxy/DNS/route traces. Filter: `adb logcat -s ZtProxyDbg` */
object ProxyDebugLog {
    const val TAG = "ZtProxyDbg"

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun w(msg: String, err: Throwable? = null) {
        if (err != null) Log.w(TAG, msg, err) else Log.w(TAG, msg)
    }
}
