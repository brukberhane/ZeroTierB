package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoonInsertPolicyTest {
    @Test
    fun normalizeWorldId_padsTo16() {
        assertEquals("000000deadbeef00", Moon.normalizeWorldId("deadbeef00"))
    }

    @Test
    fun normalizeSeed_padsTo10() {
        assertEquals("0007d11500", Moon.normalizeSeed("7d11500"))
    }

    @Test
    fun normalizeWorldId_rejectsInvalid() {
        assertNull(Moon.normalizeWorldIdOrNull("gg"))
        assertNull(Moon.normalizeWorldIdOrNull("1".repeat(17)))
    }

    @Test
    fun rejectReason_okWhenEmpty() {
        assertNull(MoonInsertPolicy.rejectReason(emptySet(), "deadbeef00"))
    }

    @Test
    fun rejectReason_duplicateNormalized() {
        val existing = setOf("000000deadbeef00")
        assertEquals(
            MoonInsertPolicy.REJECT_DUPLICATE,
            MoonInsertPolicy.rejectReason(existing, "deadbeef00"),
        )
    }

    @Test
    fun rejectReason_capAt16() {
        val existing = (1..16).map { i ->
            String.format("%016x", i.toLong())
        }.toSet()
        assertEquals(
            MoonInsertPolicy.REJECT_CAP,
            MoonInsertPolicy.rejectReason(existing, "ffffffffffffffff"),
        )
    }

    @Test
    fun rejectReason_invalid() {
        assertEquals(
            MoonInsertPolicy.REJECT_INVALID,
            MoonInsertPolicy.rejectReason(emptySet(), "not-hex"),
        )
    }
}
