package com.brukb.zerotier.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProxyManagerTest {
    @Test
    fun decideValueToSaveOnEnable_savesUserProxy() {
        assertEquals(
            "192.168.1.1:8080",
            SystemProxyManager.decideValueToSaveOnEnable("192.168.1.1:8080", 41275),
        )
    }

    @Test
    fun decideValueToSaveOnEnable_nullOrBlank_returnsNull() {
        assertEquals(null, SystemProxyManager.decideValueToSaveOnEnable(null, 41275))
        assertEquals(null, SystemProxyManager.decideValueToSaveOnEnable("", 41275))
    }

    @Test
    fun decideValueToSaveOnEnable_ourLoopback_returnsNull() {
        assertEquals(null, SystemProxyManager.decideValueToSaveOnEnable("127.0.0.1:41275", 41275))
    }

    @Test
    fun decideValueToSaveOnEnable_staleLoopbackOtherPort_returnsNull() {
        assertEquals(null, SystemProxyManager.decideValueToSaveOnEnable("127.0.0.1:1111", 41275))
        assertEquals(null, SystemProxyManager.decideValueToSaveOnEnable(":0", 41275))
    }

    @Test
    fun decideRestoreOnDisable_restoresUserProxy() {
        assertEquals("192.168.1.1:8080", SystemProxyManager.decideRestoreOnDisable("192.168.1.1:8080"))
    }

    @Test
    fun decideRestoreOnDisable_blankOrZero_returnsColonZero() {
        assertEquals(":0", SystemProxyManager.decideRestoreOnDisable(null))
        assertEquals(":0", SystemProxyManager.decideRestoreOnDisable(""))
        assertEquals(":0", SystemProxyManager.decideRestoreOnDisable(":0"))
    }

    @Test
    fun decideRestoreOnDisable_staleLoopback_returnsColonZero() {
        assertEquals(":0", SystemProxyManager.decideRestoreOnDisable("127.0.0.1:42191"))
    }

    @Test
    fun isOurLoopback_matchesExactPort() {
        assertTrue(SystemProxyManager.isOurLoopback("127.0.0.1:41275", 41275))
    }

    @Test
    fun isOurLoopback_mismatch_returnsFalse() {
        assertFalse(SystemProxyManager.isOurLoopback("127.0.0.1:9999", 41275))
        assertFalse(SystemProxyManager.isOurLoopback(null, 41275))
        assertFalse(SystemProxyManager.isOurLoopback("127.0.0.1:41275", 0))
    }

    @Test
    fun shouldClearStale_ourLoopbackWhenNotProxyMode() {
        assertTrue(
            SystemProxyManager.shouldClearStale("127.0.0.1:41275", null, 41275, proxyModeActive = false),
        )
    }

    @Test
    fun shouldClearStale_staleLoopbackOtherPortWhenNotProxyMode() {
        assertTrue(
            SystemProxyManager.shouldClearStale("127.0.0.1:42191", null, 0, proxyModeActive = false),
        )
    }

    @Test
    fun shouldClearStale_savedValueWhenNotProxyMode() {
        assertTrue(
            SystemProxyManager.shouldClearStale(null, "192.168.1.1:8080", 0, proxyModeActive = false),
        )
    }

    @Test
    fun shouldClearStale_foreignProxyNoEvidence_returnsFalse() {
        assertFalse(
            SystemProxyManager.shouldClearStale("10.0.0.1:3128", null, 41275, proxyModeActive = false),
        )
    }

    @Test
    fun shouldClearStale_proxyModeActive_returnsFalse() {
        assertFalse(
            SystemProxyManager.shouldClearStale("127.0.0.1:41275", null, 41275, proxyModeActive = true),
        )
    }

    @Test
    fun loopbackProxy_formatsCorrectly() {
        assertEquals("127.0.0.1:41275", SystemProxyManager.loopbackProxy(41275))
    }

    @Test
    fun adbGrantCommand_formatsCorrectly() {
        assertEquals(
            "adb shell pm grant com.brukb.zerotier android.permission.WRITE_SECURE_SETTINGS",
            SystemProxyManager.adbGrantCommand("com.brukb.zerotier"),
        )
    }
}
