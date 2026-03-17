package it.atm.app.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteUtilsTest {

    @Test
    fun longToBytes_skip0_full8bytes() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            longToBytes(0x0102030405060708L, 0)
        )
    }

    @Test
    fun longToBytes_skip6_2bytes() {
        assertArrayEquals(byteArrayOf(0x01, 0x00), longToBytes(256L, 6))
    }

    @Test
    fun longToBytes_skip7_1byte() {
        assertArrayEquals(byteArrayOf(0xFF.toByte()), longToBytes(255L, 7))
    }

    @Test
    fun longToBytes_skip4_4bytes() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x02, 0x20),
            longToBytes(544L, 4)
        )
    }

    @Test
    fun longToBytes_skip8_empty() {
        assertArrayEquals(ByteArray(0), longToBytes(12345L, 8))
    }

    @Test
    fun bytesToInt_2bytes() {
        assertEquals(256, bytesToInt(byteArrayOf(0x01, 0x00), 0, 2))
    }

    @Test
    fun bytesToInt_4bytes() {
        assertEquals(65536, bytesToInt(byteArrayOf(0x00, 0x01, 0x00, 0x00), 0, 4))
    }

    @Test
    fun bytesToInt_withOffset() {
        assertEquals(0xFF, bytesToInt(byteArrayOf(0x00, 0xFF.toByte()), 1, 1))
    }

    @Test
    fun extract_normal() {
        assertArrayEquals(byteArrayOf(2, 3), byteArrayOf(1, 2, 3, 4, 5).extract(1, 2))
    }

    @Test
    fun extract_beyondBounds() {
        assertArrayEquals(byteArrayOf(2, 3), byteArrayOf(1, 2, 3).extract(1, 10))
    }

    @Test
    fun extract_emptyResult() {
        assertArrayEquals(ByteArray(0), byteArrayOf(1, 2, 3).extract(3, 1))
    }

    @Test
    fun buildByteArray_empty() {
        assertArrayEquals(ByteArray(0), buildByteArray {})
    }

    @Test
    fun buildByteArray_singleByte() {
        assertArrayEquals(byteArrayOf(0x42), buildByteArray { put(0x42) })
    }

    @Test
    fun buildByteArray_multipleArrays() {
        val result = buildByteArray {
            put(byteArrayOf(1, 2))
            put(byteArrayOf(3, 4))
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), result)
    }

    @Test
    fun buildByteArray_varargBytes() {
        val result = buildByteArray { put(0x0A.toByte(), 0x0B.toByte()) }
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), result)
    }

    @Test
    fun buildByteArray_zeros() {
        val result = buildByteArray {
            put(0xFF)
            putZeros(3)
            put(0xFF)
        }
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0, 0xFF.toByte()), result)
    }

    @Test
    fun buildByteArray_slice() {
        val source = byteArrayOf(10, 20, 30, 40, 50)
        val result = buildByteArray {
            put(source, 1, 3)
        }
        assertArrayEquals(byteArrayOf(20, 30, 40), result)
    }

    @Test
    fun buildByteArray_mixed() {
        val result = buildByteArray {
            put(0x01)
            put(byteArrayOf(0x02, 0x03))
            putZeros(2)
            put(byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50), 2, 2)
            put(0xFF)
        }
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x00, 0x00, 0x30, 0x40, 0xFF.toByte()),
            result
        )
    }

    @Test
    fun buildByteArray_correctSize() {
        val result = buildByteArray {
            put(byteArrayOf(1, 2, 3))
            putZeros(5)
            put(0x42)
        }
        assertEquals(9, result.size)
    }
}
