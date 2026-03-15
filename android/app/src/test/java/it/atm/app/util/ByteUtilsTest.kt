package it.atm.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteUtilsTest {

    @Test
    fun longToBytes_8bytes() {
        val bytes = longToBytes(0x0102030405060708L, 8)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08), bytes)
    }

    @Test
    fun longToBytes_2bytes() {
        val bytes = longToBytes(256L, 2)
        assertArrayEquals(byteArrayOf(0x01, 0x00), bytes)
    }

    @Test
    fun bytesToInt_2bytes() {
        val data = byteArrayOf(0x01, 0x00)
        assertEquals(256, bytesToInt(data, 0, 2))
    }

    @Test
    fun bytesToInt_4bytes() {
        val data = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        assertEquals(65536, bytesToInt(data, 0, 4))
    }

    @Test
    fun extract_normal() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        assertArrayEquals(byteArrayOf(2, 3), data.extract(1, 2))
    }

    @Test
    fun extract_beyondBounds() {
        val data = byteArrayOf(1, 2, 3)
        assertArrayEquals(byteArrayOf(2, 3), data.extract(1, 10))
    }

    @Test
    fun extract_emptyResult() {
        val data = byteArrayOf(1, 2, 3)
        assertArrayEquals(ByteArray(0), data.extract(3, 1))
    }
}
