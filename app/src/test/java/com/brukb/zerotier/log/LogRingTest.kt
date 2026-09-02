package com.brukb.zerotier.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LogRingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun append_writesCurrent() {
        val ring = LogRing(tmp.root, maxBytes = 1024)
        ring.append("one")
        ring.append("two")
        assertEquals("one\ntwo\n", File(tmp.root, LogRing.CURRENT_NAME).readText())
        assertEquals(1, ring.filesOldestFirst().size)
    }

    @Test
    fun rotate_keepsPreviousWhenOverMax() {
        val ring = LogRing(tmp.root, maxBytes = 8)
        ring.append("aaaa")
        ring.append("bbbb")
        val rotated = File(tmp.root, LogRing.ROTATED_NAME)
        val current = File(tmp.root, LogRing.CURRENT_NAME)
        assertTrue(rotated.exists())
        assertEquals("aaaa\n", rotated.readText())
        assertEquals("bbbb\n", current.readText())
        assertEquals(listOf(rotated, current), ring.filesOldestFirst())
    }

    @Test
    fun concat_oldestFirst() {
        val ring = LogRing(tmp.root, maxBytes = 8)
        ring.append("aaaa")
        ring.append("bbbb")
        val dest = tmp.newFile("out.txt")
        ring.concatTo(dest)
        assertEquals("aaaa\nbbbb\n", dest.readText())
    }

    @Test
    fun secondRotate_dropsOldest() {
        val ring = LogRing(tmp.root, maxBytes = 8)
        ring.append("aaaa")
        ring.append("bbbb")
        ring.append("cccc")
        assertFalse(File(tmp.root, LogRing.ROTATED_NAME).readText().contains("aaaa"))
        assertEquals("bbbb\n", File(tmp.root, LogRing.ROTATED_NAME).readText())
        assertEquals("cccc\n", File(tmp.root, LogRing.CURRENT_NAME).readText())
    }
}
