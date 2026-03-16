package it.atm.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HexUtilsTest {

    @Test
    fun toHex_empty() {
        assertEquals("", ByteArray(0).toHex())
    }

    @Test
    fun toHex_bytes() {
        assertEquals("00ff10", byteArrayOf(0x00, 0xFF.toByte(), 0x10).toHex())
    }

    @Test
    fun hexToBytes_empty() {
        assertArrayEquals(ByteArray(0), "".hexToBytes())
    }

    @Test
    fun hexToBytes_roundTrip() {
        val original = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertArrayEquals(original, original.toHex().hexToBytes())
    }
}
