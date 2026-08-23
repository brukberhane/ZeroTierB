package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.GlobalModeMigrate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GlobalModeMigrateTest(
    private val startOnBoot: Boolean,
    private val stored: String?,
    private val expected: GlobalMode,
) {
    @Test
    fun initial() {
        assertEquals(expected, GlobalModeMigrate.initial(startOnBoot, stored))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "boot={0} stored={1} → {2}")
        fun data(): Collection<Array<Any?>> = listOf(
            arrayOf(false, "AUTO", GlobalMode.AUTO),
            arrayOf(true, null, GlobalMode.VPN),
            arrayOf(false, null, GlobalMode.OFF),
            arrayOf(true, "garbage", GlobalMode.OFF),
            arrayOf(false, "", GlobalMode.OFF),
            arrayOf(true, "  ", GlobalMode.VPN),
            arrayOf(false, "proxy", GlobalMode.PROXY),
        )
    }
}

class GlobalModeParseTest {
    @Test
    fun unknownIsOff() {
        assertEquals(GlobalMode.OFF, GlobalMode.parse(null))
        assertEquals(GlobalMode.OFF, GlobalMode.parse(""))
        assertEquals(GlobalMode.OFF, GlobalMode.parse("nope"))
    }
}
