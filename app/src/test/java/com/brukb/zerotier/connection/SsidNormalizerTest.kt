package com.brukb.zerotier.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SsidNormalizerTest(
    private val raw: String?,
    private val expected: String?,
) {
    @Test
    fun normalize() {
        assertEquals(expected, SsidNormalizer.normalize(raw))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} → {1}")
        fun data(): Collection<Array<Any?>> = listOf(
            arrayOf("\"HomeWifi\"", "HomeWifi"),
            arrayOf("HomeWifi", "HomeWifi"),
            arrayOf("<unknown ssid>", null),
            arrayOf("\"<unknown ssid>\"", null),
            arrayOf("0x", null),
            arrayOf("0xdeadbeef", null),
            arrayOf("", null),
            arrayOf("   ", null),
            arrayOf(null, null),
            arrayOf("\"  Cafe Wi-Fi  \"", "Cafe Wi-Fi"),
            arrayOf("home", "home"),
            arrayOf("Home", "Home"),
        )
    }
}

class SsidNormalizerCaseSensitiveTest {
    @Test
    fun doesNotFoldCase() {
        assertEquals("home", SsidNormalizer.normalize("home"))
        assertEquals("Home", SsidNormalizer.normalize("Home"))
        assertNull(SsidNormalizer.normalize("<UNKNOWN SSID>"))
    }
}
