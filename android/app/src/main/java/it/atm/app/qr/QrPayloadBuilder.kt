package it.atm.app.qr

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Parses VToken binary data and builds QR code payloads
 * matching the original AEP VTS SDK (VtsWallet.getVTokenQRData).
 *
 * The original SDK extracts the payload starting at byte offset 57
 * (after the VToken header) and encodes it according to the QRCodeFormat
 * parameter from server config.
 */
object QrPayloadBuilder {

    /**
     * Parsed fields from the 57-byte VToken binary header.
     * Layout matches VtsVTokenByteParser.a() in the original SDK.
     */
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

    /**
     * Parses the 57-byte VToken header.
     *
     * Binary layout:
     * ```
     * Offset  Size  Field
     * 0       4     Magic (skipped)
     * 4       8     UID (big-endian long)
     * 12      1     TokenType
     * 13      1     Version
     * 14      2     Flags (big-endian short)
     * 16      4     Reserved
     * 20      2     SystemType (big-endian short)
     * 22      1     SystemSubType
     * 23      8     DeviceUID (big-endian long)
     * 31      8     ObjectUID (big-endian long)
     * 39      1     ObjectType
     * 40      1     ObjectTypeFormat
     * 41      4     SignatureCount (big-endian int)
     * 45      4     GroupUID (big-endian int)
     * 49      4     PayloadSize (big-endian int)
     * 53      2     ValidationSize
     * 55      2     Reserved
     * 57+     N     Payload data
     * ```
     */
    fun parseHeader(data: ByteArray): VTokenHeader {
        require(data.size >= 57) { "VToken data too short: ${data.size} bytes, need >= 57" }
        val buf = ByteBuffer.wrap(data)
        buf.position(4)                                         // skip magic
        val uid = buf.getLong()                                  // offset 4
        val tokenType = buf.get().toInt() and 0xFF               // offset 12
        val version = buf.get().toInt() and 0xFF                 // offset 13
        val flags = buf.getShort().toInt() and 0xFFFF            // offset 14
        buf.position(buf.position() + 4)                         // skip reserved (offset 16)
        val systemType = buf.getShort().toInt() and 0xFFFF       // offset 20
        val systemSubType = buf.get().toInt() and 0xFF           // offset 22
        val deviceUID = buf.getLong()                             // offset 23
        val objectUID = buf.getLong()                             // offset 31
        val objectType = buf.get().toInt() and 0xFF              // offset 39
        buf.get() // skip objectTypeFormat                       // offset 40
        buf.getInt() // skip signatureCount                      // offset 41
        buf.getInt() // skip groupUID                            // offset 45
        val payloadSize = buf.getInt()                           // offset 49

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

    /**
     * Extracts the payload from VToken binary data (starting at offset 57).
     * Matches ByteUtils.extract(fileContents, 57, payloadSize) in the original.
     */
    private fun extractPayload(data: ByteArray, header: VTokenHeader): ByteArray {
        val offset = 57
        val size = header.payloadSize.coerceAtMost(data.size - offset).coerceAtLeast(0)
        return data.copyOfRange(offset, offset + size)
    }

    /**
     * Builds the raw QR code byte payload matching the original VTS SDK.
     *
     * The result should be encoded into a QR code as an ISO-8859-1 string,
     * matching BitmapUtils.generateQrCode in the original.
     *
     * @param data Full VToken binary data (DataOutBin decoded from base64)
     * @param qrCodeFormat QR format (1-4) from server config (default 1)
     * @param sigType Signature type from server config
     * @param sigKey Signature key index from server config
     * @param isRevalidation Whether this is a revalidation QR code
     * @return Raw bytes to be encoded into the QR code
     */
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
            1 -> buildFormat1(payload, sigType, sigKey)
            2 -> buildFormat1(payload, sigType, sigKey)
            3 -> buildFormat1(payload, sigType, sigKey)
            4 -> buildFormat4(header, payload, sigType, sigKey, isRevalidation)
            else -> buildFormat1(payload, sigType, sigKey)
        }
    }

    /**
     * Format 1 (default): Raw payload bytes, optionally signed.
     * Matches: `if (p15 > 0) v7 = makeSignature(v7, p15, p17); return new String(v7, ISO-8859-1);`
     */
    private fun buildFormat1(payload: ByteArray, sigType: Int, sigKey: Int): ByteArray {
        return if (sigType > 0) {
            QrSignatureEngine.makeSignature(payload, sigType, sigKey)
        } else {
            payload
        }
    }

    /**
     * Format 4: Full VTSQ structured message with token metadata and timestamp.
     *
     * Structure:
     * ```
     * 12 bytes: VTSQ header ("VTSQ" + version + flags + 6 zero padding)
     *  8 bytes: Token UID
     *  2 bytes: System Type
     *  2 bytes: System SubType
     *  8 bytes: Object UID
     *  1 byte:  Object Type
     *  2 bytes: Payload Size
     *  8 bytes: Device UID
     *  4 bytes: Current Unix timestamp (seconds)
     *  6 bytes: Reserved (zeros)
     *  N bytes: Payload
     * ```
     *
     * Matches VtsWallet.a(VtsVToken, byte[], boolean) in the original SDK.
     */
    private fun buildFormat4(
        header: VTokenHeader,
        payload: ByteArray,
        sigType: Int,
        sigKey: Int,
        isRevalidation: Boolean
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // VTSQ header (12 bytes)
        out.write(byteArrayOf(0x56, 0x54, 0x53, 0x51)) // "VTSQ" magic
        out.write(0x01)                                   // version
        out.write(if (isRevalidation) 0x02 else 0x00)     // flags
        out.write(ByteArray(6))                            // padding

        // Token metadata fields (big-endian, matching longToBytes from original)
        out.write(longToBytes(header.uid, 8))
        out.write(longToBytes(header.systemType.toLong(), 2))
        out.write(longToBytes(header.systemSubType.toLong(), 2))
        out.write(longToBytes(header.objectUID, 8))
        out.write(longToBytes(header.objectType.toLong(), 1))
        out.write(longToBytes(header.payloadSize.toLong(), 2))
        out.write(longToBytes(header.deviceUID, 8))
        out.write(longToBytes(System.currentTimeMillis() / 1000, 4))
        out.write(ByteArray(6)) // reserved

        // Payload
        out.write(payload)

        val message = out.toByteArray()
        return if (sigType > 0) {
            QrSignatureEngine.makeSignature(message, sigType, sigKey)
        } else {
            message
        }
    }

    /**
     * Converts a long value to big-endian bytes of the specified length.
     * Matches ByteUtils.longToBytes(value, 8 - length) in the original SDK.
     */
    private fun longToBytes(value: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        for (i in 0 until length) {
            bytes[length - 1 - i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
        return bytes
    }
}
