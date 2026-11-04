package com.skrypko.dicefamiliar.domain.lan

import com.skrypko.dicefamiliar.data.lan.transport.readCappedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanAddressTest {
    @Test
    fun parsesWhatUsersType() {
        assertEquals(LanAddress("192.168.1.20", LanAddress.DEFAULT_PORT), LanAddress.parse(" 192.168.1.20 "))
        assertEquals(LanAddress("100.101.2.3", 5000), LanAddress.parse("100.101.2.3:5000"))
        assertEquals(LanAddress("fd7a::1", 47321), LanAddress.parse("[fd7a::1]:47321"))
        assertEquals(LanAddress("fd7a::1", LanAddress.DEFAULT_PORT), LanAddress.parse("fd7a::1"))
        assertEquals(LanAddress("my-phone.tailnet.ts.net", LanAddress.DEFAULT_PORT), LanAddress.parse("my-phone.tailnet.ts.net"))
        assertEquals("[fd7a::1]:47321", LanAddress("fd7a::1", 47321).toString())
    }

    @Test
    fun rejectsNonsense() {
        assertNull(LanAddress.parse(""))
        assertNull(LanAddress.parse("1.2.3.4:0"))
        assertNull(LanAddress.parse("1.2.3.4:99999"))
        assertNull(LanAddress.parse("1.2.3.4:abc"))
        assertNull(LanAddress.parse("two words"))
        assertNull(LanAddress.parse("http://x/y"))
    }
}

class LineFramingTest {
    @Test
    fun linesAreCappedAndCrlfTolerated() {
        val reader = ("one\r\n" + "x".repeat(20) + "\ntwo\nlast").reader().buffered()
        assertEquals("one", reader.readCappedLine(10))
        assertEquals("", reader.readCappedLine(10))
        assertEquals("two", reader.readCappedLine(10))
        assertEquals("last", reader.readCappedLine(10))
        assertNull(reader.readCappedLine(10))
    }
}
