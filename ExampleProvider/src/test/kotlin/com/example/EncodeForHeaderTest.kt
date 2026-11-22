package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression: an Arabic URL passed as a header value (Referer) crashed OkHttp
// header validation ("Unexpected char 0x641 ... in referer value"), which
// CloudStream swallowed into a generic "No Links Found". encodeForHeader must
// percent-encode non-ASCII path segments while leaving plain URLs untouched.
class EncodeForHeaderTest {

    @Test
    fun arabicPathBecomesAscii() {
        val out = encodeForHeader("https://shahed4u.here/watch/مترجم-اون-لاين-frostbite-2026-فيلم")
        assertTrue("result must be pure ASCII", out.all { it.code < 128 })
        assertTrue("Arabic segments must be percent-encoded", out.contains("%D9%85"))
        assertFalse(out.contains("ف"))
        assertTrue(out.startsWith("https://shahed4u.here/watch/"))
    }

    @Test
    fun latinUrlUnchanged() {
        val url = "https://shahed4u.here/film/frostbite-2026"
        assertEquals(url, encodeForHeader(url))
    }

    @Test
    fun queryStringPreserved() {
        val url = "https://shahed4u.here/?page=2"
        assertEquals(url, encodeForHeader(url))
    }
}
