package it.atm.app.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class QrPayloadBuilderTest {

    private fun buildMinimalVToken(payloadSize: Int = 10): ByteArray {
        val headerSize = 57
        val payload = ByteArray(payloadSize) { (it + 1).toByte() }
        val data = ByteArray(headerSize + payloadSize)
        val buf = ByteBuffer.wrap(data)
        buf.putInt(0x56545300)
        buf.putLong(12345L)
        buf.put(1)
        buf.put(1)
        buf.putShort(0)
        buf.putInt(0)
        buf.putShort(3)
        buf.put(1)
        buf.putLong(99999L)
        buf.putLong(88888L)
        buf.put(8)
        buf.put(0)
        buf.putInt(1)
        buf.putInt(0)
        buf.putInt(payloadSize)
        buf.putShort(0)
        buf.putShort(0)
        System.arraycopy(payload, 0, data, headerSize, payloadSize)
        return data
    }

    @Test
    fun parseHeader_minimalToken() {
        val data = buildMinimalVToken()
        val header = QrPayloadBuilder.parseHeader(data)
        assertEquals(12345L, header.uid)
        assertEquals(10, header.payloadSize)
        assertEquals(3, header.systemType)
        assertEquals(1, header.systemSubType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseHeader_tooShort() {
        QrPayloadBuilder.parseHeader(ByteArray(10))
    }

    @Test
    fun buildQrData_format1_unsigned() {
        val data = buildMinimalVToken(20)
        val result = QrPayloadBuilder.buildQrData(data, 1, 0, 0)
        assertEquals(20, result.size)
    }

    @Test
    fun buildQrData_format1_signed() {
        val data = buildMinimalVToken(20)
        val result = QrPayloadBuilder.buildQrData(data, 1, 1, 0)
        assertEquals(20 - 6 + 2 + 4, result.size)
    }

    @Test
    fun buildQrData_format4() {
        val data = buildMinimalVToken(20)
        val result = QrPayloadBuilder.buildQrData(data, 4, 0, 0)
        assertNotNull(result)
        assertEquals(true, result.size > 57)
        assertEquals(0x56, result[0].toInt() and 0xFF)
        assertEquals(0x54, result[1].toInt() and 0xFF)
        assertEquals(0x53, result[2].toInt() and 0xFF)
        assertEquals(0x51, result[3].toInt() and 0xFF)
    }
}
