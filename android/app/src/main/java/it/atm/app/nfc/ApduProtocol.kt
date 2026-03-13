package it.atm.app.nfc

import android.util.Log
import it.atm.app.auth.AuthConstants
import it.atm.app.qr.QrConstants
import it.atm.app.qr.QrPayloadBuilder
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implements the AEP VTS 4-phase APDU exchange protocol.
 *
 * Matches VtsApduService + VtsExchangeHandler (core.a) in the original SDK.
 *
 * Phase 0 (SELECT):  CLA=00 INS=A4 P1=04 P2=00 — AID selection, load active VToken
 * Phase 1 (NEGOTIATE): CLA=80 INS=A5 P1=10 P2=00 — cipher setup (plaintext or AES)
 * Phase 2 (READ):    CLA=80 INS=A6             — send VToken data in chunks
 * Phase 3 (WRITE):   CLA=80 INS=A7             — receive validation stamp from reader
 */
class ApduProtocol(private val tokenStore: NfcTokenStore) {

    companion object {
        private const val TAG = "ATM_NFC"

        /** AID for AEP VTS NFC: "AEP.NFCB.VT" */
        private val AID = hexToBytes("A0000007874145502E4E4643422E5654")

        private const val MAX_CHUNK_PLAINTEXT = 248
        private const val MAX_CHUNK_AES = 235

        // SW status words
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00)
        private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00)
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
        private val SW_WRONG_INS = byteArrayOf(0x6D.toByte(), 0x00)
        private val SW_INTERNAL_ERROR = byteArrayOf(0x6F.toByte(), 0x00)

        /** SDK version short string, e.g. "2.9" from "2.9.123" */
        private val SDK_VERSION_SHORT: ByteArray = run {
            val parts = AuthConstants.SDK_VERSION.split(".")
            val short = if (parts.size >= 2) "${parts[0]}.${parts[1]}" else AuthConstants.SDK_VERSION
            short.toByteArray(Charsets.US_ASCII)
        }

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length / 2
            val bytes = ByteArray(len)
            for (i in 0 until len) {
                bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return bytes
        }
    }

    // Exchange state
    private var phase = -1
    private var tokenData: ByteArray? = null
    private var lastRequest: ApduRequest? = null
    private var sessionNonce = ByteArray(4)
    private var useAes = false
    private var aesKey: ByteArray? = null
    private val writeBuffer = ByteArrayOutputStream()

    /** Reset the protocol state for a new exchange. */
    fun reset() {
        phase = -1
        tokenData = null
        lastRequest = null
        sessionNonce = ByteArray(4)
        useAes = false
        aesKey = null
        writeBuffer.reset()
    }

    /** Returns true if the last exchange completed successfully (phase 3 with completion flag). */
    var exchangeCompleted = false
        private set

    /**
     * Process an incoming APDU command and return the response bytes.
     * Called from HostApduService.processCommandApdu on the main thread.
     */
    fun processCommand(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_INTERNAL_ERROR

        exchangeCompleted = false

        return try {
            val request = parseApdu(apdu)
            when {
                request.isMatch(0x00, 0xA4) -> handlePhase0(request)
                request.isMatch(0x80, 0xA5) -> handlePhase1(request)
                request.isMatch(0x80, 0xA6) -> handlePhase2(request)
                request.isMatch(0x80, 0xA7) -> handlePhase3(request)
                else -> {
                    Log.w(TAG, "Unrecognized command CLA=${request.cla} INS=${request.ins}")
                    SW_WRONG_INS
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "APDU processing failed: ${e.message}")
            SW_INTERNAL_ERROR
        }
    }

    // ==================== Phase 0: SELECT ====================

    private fun handlePhase0(request: ApduRequest): ByteArray {
        if (request.p1.toInt() != 0x04 || request.p2.toInt() != 0x00) return SW_WRONG_P1P2
        if (phase != -1) return SW_CONDITIONS_NOT_SATISFIED

        // Load active VToken data
        val data = tokenStore.loadActiveVToken()
        if (data == null || data.size < 57) {
            Log.w(TAG, "Phase 0: No active VToken available")
            return SW_NOT_FOUND
        }

        tokenData = data
        lastRequest = request
        phase = 0

        val header = QrPayloadBuilder.parseHeader(data)
        val tokenSize = header.payloadSize

        // Build FCI Template response matching original SDK
        // 6F [len]
        //   84 [aidLen] [AID]
        //   A5 16
        //     BF0C 13
        //       C7 08 [DeviceUID 8 bytes]
        //       53 07
        //         01 01 [tokenSize 2 bytes] 10 01 [sdkVersion]
        val deviceUid = longToBytes(header.deviceUID, 8)
        val tokenSizeBytes = longToBytes(tokenSize.toLong(), 2)

        val fciData = ByteArrayOutputStream()
        // DF Name
        fciData.write(0x84)
        fciData.write(AID.size)
        fciData.write(AID)
        // FCI Proprietary Template
        fciData.write(0xA5.toByte().toInt())
        fciData.write(0x16)
        // FCI Issuer Discretionary Data
        fciData.write(byteArrayOf(0xBF.toByte(), 0x0C))
        fciData.write(0x13)
        // Device UID tag
        fciData.write(0xC7.toByte().toInt())
        fciData.write(0x08)
        fciData.write(deviceUid)
        // Discretionary data
        fciData.write(0x53)
        fciData.write(0x07)
        fciData.write(0x01) // version
        fciData.write(0x01)
        fciData.write(tokenSizeBytes)
        fciData.write(0x10)
        fciData.write(0x01)
        fciData.write(SDK_VERSION_SHORT)

        val fciBody = fciData.toByteArray()
        val response = ByteArrayOutputStream()
        response.write(0x6F)
        response.write(fciBody.size)
        response.write(fciBody)
        response.write(SW_OK)

        Log.d(TAG, "Phase 0: SELECT OK, tokenSize=$tokenSize")
        return response.toByteArray()
    }

    // ==================== Phase 1: NEGOTIATE ====================

    private fun handlePhase1(request: ApduRequest): ByteArray {
        if (request.p1.toInt() != 0x10 || request.p2.toInt() != 0x00) return SW_WRONG_P1P2
        if (phase != 0) return SW_CONDITIONS_NOT_SATISFIED

        val data = request.data
        if (data.size != 5) return SW_WRONG_LENGTH

        val deviceType = data[0].toInt() and 0xFF
        val deviceSubtype = data[1].toInt() and 0xFF
        val cipherType = data[2].toInt() and 0xFF

        // Validate device type/subtype (matching original)
        if (deviceType < 1 || deviceType > 23 || deviceSubtype < 1 || deviceSubtype > 3
            || (deviceType == 2 && deviceSubtype > 2)) {
            return SW_WRONG_P1P2
        }

        // Setup cipher
        if (cipherType == 0) {
            useAes = false
            aesKey = null
        } else if (cipherType == 1) {
            val keyIndex = bytesToInt(data, 3, 2)
            if (keyIndex < 0 || keyIndex >= QrConstants.QR_KEYS.size) {
                Log.w(TAG, "Phase 1: Invalid AES key index $keyIndex")
                return SW_CONDITIONS_NOT_SATISFIED
            }
            useAes = true
            aesKey = QrConstants.QR_KEYS[keyIndex]
        } else {
            return SW_CONDITIONS_NOT_SATISFIED
        }

        lastRequest = request
        phase = 1

        // Build response: 80 12 03 01 + SW_OK
        val response = ByteArrayOutputStream()
        response.write(byteArrayOf(0x80.toByte(), 0x12, 0x03, 0x01))
        response.write(SW_OK)

        // Encrypt phase 1 response (both plaintext and AES pass through unmodified)
        Log.d(TAG, "Phase 1: NEGOTIATE OK, cipher=${if (useAes) "AES" else "plaintext"}")
        return response.toByteArray()
    }

    // ==================== Phase 2: READ ====================

    private fun handlePhase2(request: ApduRequest): ByteArray {
        if (phase != 1 && phase != 2) return SW_CONDITIONS_NOT_SATISFIED

        val data = request.data
        if (data.size != 7) return SW_WRONG_LENGTH

        val operationMode = data[0].toInt() and 0xFF  // 1=read, 2=?
        val readMode = data[1].toInt() and 0xFF        // 0=unpaginated, 1=paginated
        if ((operationMode != 1 && operationMode != 2) || (readMode != 0 && readMode != 1)) {
            return SW_WRONG_P1P2
        }

        // Decrypt phase 2 request (both plaintext and AES pass through for phase 2)
        lastRequest = request
        phase = 2
        sessionNonce = extract(data, 3, 4)

        val tokenBytes = tokenData ?: return SW_NOT_FOUND
        // Extract full file contents (including header) — the reader gets the raw wallet data
        val totalSize = tokenBytes.size

        val maxChunk = if (useAes) MAX_CHUNK_AES else MAX_CHUNK_PLAINTEXT
        val offset = ((request.p1.toInt() and 0xFF) shl 8) or (request.p2.toInt() and 0xFF)

        var readSize = data[2].toInt() and 0xFF
        if (readSize == 0) readSize = maxChunk.coerceAtMost(totalSize)

        if (offset >= totalSize) return SW_WRONG_P1P2

        val chunk = extract(tokenBytes, offset, readSize.coerceAtMost(totalSize - offset))

        // Build response: 80 [len] [chunkLen] [sessionNonce 4B] [chunk] + SW_OK
        val innerData = ByteArrayOutputStream()
        innerData.write(chunk.size.toByte().toInt())
        innerData.write(sessionNonce)
        innerData.write(chunk)
        val inner = innerData.toByteArray()

        val responseData = ByteArrayOutputStream()
        responseData.write(0x80.toByte().toInt())
        responseData.write(inner.size + 5) // +5 for the tag byte and chunk metadata
        responseData.write(inner)

        var respBytes = responseData.toByteArray()

        // AES encrypt phase 2 response (encrypt bytes after first 3)
        if (useAes && aesKey != null) {
            respBytes = encryptPhase2Response(respBytes)
        }

        val response = ByteArrayOutputStream()
        response.write(respBytes)
        response.write(SW_OK)

        Log.d(TAG, "Phase 2: READ offset=$offset size=${chunk.size}/${totalSize}")
        return response.toByteArray()
    }

    // ==================== Phase 3: WRITE ====================

    private fun handlePhase3(request: ApduRequest): ByteArray {
        if (phase != 2 && phase != 3) return SW_CONDITIONS_NOT_SATISFIED

        val reqData = request.data
        if (reqData.size < 7) return SW_WRONG_LENGTH

        // Decrypt phase 3 request if AES
        val decrypted = if (useAes && aesKey != null) {
            decryptPhase3Request(request)
        } else {
            request
        }

        val ddata = decrypted.data
        val completionFlag = ddata[0].toInt() and 0xFF  // 0=more, 1=done
        val writeMode = ddata[1].toInt() and 0xFF
        val writeSize = ddata[2].toInt() and 0xFF

        if ((completionFlag != 0 && completionFlag != 1) || (writeMode != 0 && writeMode != 1)) {
            return SW_WRONG_P1P2
        }

        lastRequest = decrypted
        phase = if (completionFlag == 1) 4 else 3
        sessionNonce = extract(ddata, 3, 4)

        val writeOffset = ((decrypted.p1.toInt() and 0xFF) shl 8) or (decrypted.p2.toInt() and 0xFF)
        val writeData = extract(ddata, 7, writeSize.coerceAtMost(ddata.size - 7))

        // Accumulate write data
        if (writeBuffer.size() != writeOffset) {
            val existing = writeBuffer.toByteArray()
            writeBuffer.reset()
            writeBuffer.write(extract(existing, 0, writeOffset))
            writeBuffer.write(writeData)
            if (existing.size > writeOffset + writeData.size) {
                writeBuffer.write(extract(existing, writeOffset + writeData.size, existing.size))
            }
        } else {
            writeBuffer.write(writeData)
        }

        if (completionFlag == 1) {
            exchangeCompleted = true
            Log.d(TAG, "Phase 3: WRITE complete, stamp=${writeBuffer.size()} bytes")
            // The validation stamp is in writeBuffer.toByteArray()
            // In a full implementation, save it back to the wallet/storage
        } else {
            Log.d(TAG, "Phase 3: WRITE partial, offset=$writeOffset, size=${writeData.size}")
        }

        // Build response: 80 04 [sessionNonce 4B] + SW_OK
        // Phase 3 response is NOT encrypted (even in AES mode)
        val response = ByteArrayOutputStream()
        response.write(byteArrayOf(0x80.toByte(), 0x04))
        response.write(sessionNonce)
        response.write(SW_OK)
        return response.toByteArray()
    }

    // ==================== AES Cipher Helpers ====================

    /**
     * AES/CBC/PKCS5Padding with zero IV, matching CryptoUtils in the original SDK.
     */
    private fun aesEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val iv = IvParameterSpec(ByteArray(key.size))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
        return cipher.doFinal(data)
    }

    private fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val iv = IvParameterSpec(ByteArray(key.size))
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)
        return cipher.doFinal(data)
    }

    /**
     * Encrypt phase 2 response: encrypt data bytes after offset 3.
     * Matches VtsAesMessageCipher.encryptPhase2Response.
     */
    private fun encryptPhase2Response(respData: ByteArray): ByteArray {
        val key = aesKey ?: return respData
        val plainPart = extract(respData, 3, respData.size - 3)
        val encrypted = aesEncrypt(plainPart, key)
        val out = ByteArrayOutputStream()
        out.write(respData[0].toInt()) // tag 0x80
        out.write(encrypted.size + 1)  // new length
        out.write(respData[2].toInt()) // chunk length byte
        out.write(encrypted)
        return out.toByteArray()
    }

    /**
     * Decrypt phase 3 request: decrypt data bytes after offset 3.
     * Matches VtsAesMessageCipher.decryptPhase3Request.
     */
    private fun decryptPhase3Request(request: ApduRequest): ApduRequest {
        val key = aesKey ?: return request
        val data = request.data
        if (data.size <= 3) return request
        val encryptedPart = extract(data, 3, data.size - 3)
        val decrypted = aesDecrypt(encryptedPart, key)
        val newData = ByteArray(3 + decrypted.size)
        System.arraycopy(data, 0, newData, 0, 3)
        System.arraycopy(decrypted, 0, newData, 3, decrypted.size)
        return request.copy(data = newData)
    }

    // ==================== APDU Parsing ====================

    private fun parseApdu(raw: ByteArray): ApduRequest {
        val cla = raw[0]
        val ins = raw[1]
        val p1 = raw[2]
        val p2 = raw[3]
        val data = if (raw.size > 5) {
            val lc = raw[4].toInt() and 0xFF
            extract(raw, 5, lc.coerceAtMost(raw.size - 5))
        } else {
            ByteArray(0)
        }
        return ApduRequest(cla, ins, p1, p2, data)
    }

    // ==================== Utilities ====================

    private fun extract(data: ByteArray, offset: Int, length: Int): ByteArray {
        val safeLen = length.coerceAtMost(data.size - offset).coerceAtLeast(0)
        return data.copyOfRange(offset, offset + safeLen)
    }

    private fun longToBytes(value: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        for (i in 0 until length) {
            bytes[length - 1 - i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
        return bytes
    }

    private fun bytesToInt(data: ByteArray, offset: Int, length: Int): Int {
        var result = 0
        for (i in 0 until length) {
            result = (result shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        return result
    }
}

/**
 * Parsed APDU request message.
 * Matches ApduRequestMessage in the original SDK (fields: CLA, INS, P1, P2, data).
 */
data class ApduRequest(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val data: ByteArray = ByteArray(0)
) {
    fun isMatch(cla: Int, ins: Int): Boolean {
        return (this.cla.toInt() and 0xFF) == cla && (this.ins.toInt() and 0xFF) == ins
    }
}
