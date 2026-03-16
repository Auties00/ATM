package it.atm.app.qr

import it.atm.app.util.buildByteArray
import it.atm.app.util.longToBytes
import java.nio.ByteBuffer

object QrPayloadBuilder {

    data class VTokenHeader(
        val uid: Long,
        val tokenType: Int,
        val version: Int,
        val flags: Int,
        val requiresGeneration: Boolean,
        val systemType: Int,
        val systemSubType: Int,
        val deviceUID: Long,
        val objectUID: Long,
        val objectType: Int,
        val payloadSize: Int,
    )

    fun parseHeader(data: ByteArray): VTokenHeader {
        require(data.size >= 57) { "VToken data too short: ${data.size} bytes, need >= 57" }
        val buf = ByteBuffer.wrap(data)
        buf.position(4)
        val uid = buf.getLong()
        val tokenType = buf.get().toInt() and 0xFF
        val version = buf.get().toInt() and 0xFF
        val flags = buf.getShort().toInt() and 0xFFFF
        buf.position(buf.position() + 4)
        val systemType = buf.getShort().toInt() and 0xFFFF
        val systemSubType = buf.get().toInt() and 0xFF
        val deviceUID = buf.getLong()
        val objectUID = buf.getLong()
        val objectType = buf.get().toInt() and 0xFF
        buf.get()
        buf.getInt()
        buf.getInt()
        val payloadSize = buf.getInt()

        return VTokenHeader(
            uid = uid,
            tokenType = tokenType,
            version = version,
            flags = flags,
            requiresGeneration = (flags and 0x10) != 0,
            systemType = systemType,
            systemSubType = systemSubType,
            deviceUID = deviceUID,
            objectUID = objectUID,
            objectType = objectType,
            payloadSize = payloadSize,
        )
    }

    private fun extractPayload(data: ByteArray, header: VTokenHeader): ByteArray {
        val offset = 57
        val size = header.payloadSize.coerceAtMost(data.size - offset).coerceAtLeast(0)
        return data.copyOfRange(offset, offset + size)
    }

    fun buildQrData(
        data: ByteArray,
        qrCodeFormat: Int,
        sigType: Int,
        sigKey: Int,
        isRevalidation: Boolean = false
    ): ByteArray {
        val header = parseHeader(data)
        val payload = extractPayload(data, header)

        return when (qrCodeFormat) {
            4 -> buildFormat4(header, payload, sigType, sigKey, isRevalidation)
            else -> buildFormat1(payload, sigType, sigKey)
        }
    }

    private fun buildFormat1(payload: ByteArray, sigType: Int, sigKey: Int): ByteArray {
        return if (sigType > 0) {
            QrSignatureEngine.makeSignature(payload, sigType, sigKey)
        } else {
            payload
        }
    }

    private fun buildFormat4(
        header: VTokenHeader,
        payload: ByteArray,
        sigType: Int,
        sigKey: Int,
        isRevalidation: Boolean
    ): ByteArray {
        val message = buildByteArray {
            put(0x56, 0x54, 0x53, 0x51)
            put(0x01)
            put(if (isRevalidation) 0x02 else 0x00)
            putZeros(6)
            put(longToBytes(header.uid, 8))
            put(longToBytes(header.systemType.toLong(), 2))
            put(longToBytes(header.systemSubType.toLong(), 2))
            put(longToBytes(header.objectUID, 8))
            put(longToBytes(header.objectType.toLong(), 1))
            put(longToBytes(header.payloadSize.toLong(), 2))
            put(longToBytes(header.deviceUID, 8))
            put(longToBytes(System.currentTimeMillis() / 1000, 4))
            putZeros(6)
            put(payload)
        }
        return if (sigType > 0) {
            QrSignatureEngine.makeSignature(message, sigType, sigKey)
        } else {
            message
        }
    }
}
