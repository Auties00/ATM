package it.atm.app.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QrSignatureEngineTest {

    @Test
    fun makeSignature_producesCorrectLength() {
        val data = ByteArray(100) { it.toByte() }
        val result = QrSignatureEngine.makeSignature(data, 1, 0)
        assertEquals(data.size - 6 + 2 + 4, result.size)
    }

    @Test
    fun makeSignature_shortData() {
        val data = ByteArray(3) { it.toByte() }
        val result = QrSignatureEngine.makeSignature(data, 1, 0)
        assertEquals(data.size + 2 + 4, result.size)
    }

    @Test
    fun makeSignature_containsSigTypeAndKeyId() {
        val data = ByteArray(20) { 0x42 }
        val sigType = 2
        val keyId = 5
        val result = QrSignatureEngine.makeSignature(data, sigType, keyId)
        val baseSize = data.size - 6
        assertEquals(sigType.toByte(), result[baseSize])
        assertEquals(keyId.toByte(), result[baseSize + 1])
    }

    @Test
    fun makeSignature_deterministicForSameInput() {
        val data = ByteArray(50) { (it * 3).toByte() }
        val result1 = QrSignatureEngine.makeSignature(data, 1, 2)
        val result2 = QrSignatureEngine.makeSignature(data, 1, 2)
        assertNotNull(result1)
        assertEquals(result1.toList(), result2.toList())
    }

    @Test
    fun makeSignature_differentKeysProduceDifferentOutput() {
        val data = ByteArray(50) { it.toByte() }
        val result0 = QrSignatureEngine.makeSignature(data, 1, 0)
        val result1 = QrSignatureEngine.makeSignature(data, 1, 1)
        assertEquals(false, result0.toList() == result1.toList())
    }
}
