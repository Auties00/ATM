package it.atm.app.nfc

import it.atm.app.auth.AuthConstants
import it.atm.app.qr.QrConstants
import it.atm.app.qr.QrPayloadBuilder
import it.atm.app.util.buildByteArray
import it.atm.app.util.bytesToInt
import it.atm.app.util.extract
import it.atm.app.util.longToBytes
import it.atm.app.util.AppLogger
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ApduProtocol(private val tokenStore: NfcTokenStore) {

    companion object {
        private const val TAG = "NFC"
        @OptIn(ExperimentalStdlibApi::class)
        private val AID = "A0000007874145502E4E4643422E5654".hexToByteArray()
        private const val MAX_CHUNK_PLAINTEXT = 248
        private const val MAX_CHUNK_AES = 235
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00)
        private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00)
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69.toByte(), 0x85.toByte())
        private val SW_WRONG_INS = byteArrayOf(0x6D.toByte(), 0x00)
        private val SW_INTERNAL_ERROR = byteArrayOf(0x6F.toByte(), 0x00)

        private val SDK_VERSION_SHORT: ByteArray = run {
            val parts = AuthConstants.SDK_VERSION.split(".")
            val short = if (parts.size >= 2) "${parts[0]}.${parts[1]}" else AuthConstants.SDK_VERSION
            short.toByteArray(Charsets.US_ASCII)
        }
    }

    private var phase = -1
    private var tokenData: ByteArray? = null
    private var lastRequest: ApduRequest? = null
    private var sessionNonce = ByteArray(4)
    private var useAes = false
    private var aesKey: ByteArray? = null
    private val writeBuffer = ByteArrayOutputStream()

    fun reset() {
        phase = -1
        tokenData = null
        lastRequest = null
        sessionNonce = ByteArray(4)
        useAes = false
        aesKey = null
        writeBuffer.reset()
    }

    var exchangeCompleted = false
        private set

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
                    AppLogger.w(TAG,"Unrecognized command CLA=%02x INS=%02x", request.cla, request.ins)
                    SW_WRONG_INS
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG,"APDU processing failed: %s", e.message)
            SW_INTERNAL_ERROR
        }
    }

    private fun handlePhase0(request: ApduRequest): ByteArray {
        if (request.p1.toInt() != 0x04 || request.p2.toInt() != 0x00) return SW_WRONG_P1P2
        if (phase != -1) return SW_CONDITIONS_NOT_SATISFIED

        val data = tokenStore.loadActiveVToken()
        if (data == null || data.size < 57) {
            AppLogger.w(TAG,"Phase 0: No active VToken available")
            return SW_NOT_FOUND
        }

        tokenData = data
        lastRequest = request
        phase = 0

        val header = QrPayloadBuilder.parseHeader(data)
        val tokenSize = header.payloadSize

        val deviceUid = longToBytes(header.deviceUID, 8)
        val tokenSizeBytes = longToBytes(tokenSize.toLong(), 2)

        val fciBody = buildByteArray {
            put(0x84); put(AID.size); put(AID)
            put(0xA5); put(0x16)
            put(0xBF.toByte(), 0x0C); put(0x13)
            put(0xC7); put(0x08); put(deviceUid)
            put(0x53); put(0x07); put(0x01); put(0x01)
            put(tokenSizeBytes)
            put(0x10); put(0x01); put(SDK_VERSION_SHORT)
        }

        AppLogger.d(TAG,"Phase 0: SELECT OK, tokenSize=%d", tokenSize)
        return buildByteArray { put(0x6F); put(fciBody.size); put(fciBody); put(SW_OK) }
    }

    private fun handlePhase1(request: ApduRequest): ByteArray {
        if (request.p1.toInt() != 0x10 || request.p2.toInt() != 0x00) return SW_WRONG_P1P2
        if (phase != 0) return SW_CONDITIONS_NOT_SATISFIED

        val data = request.data
        if (data.size != 5) return SW_WRONG_LENGTH

        val deviceType = data[0].toInt() and 0xFF
        val deviceSubtype = data[1].toInt() and 0xFF
        val cipherType = data[2].toInt() and 0xFF

        if (deviceType < 1 || deviceType > 23 || deviceSubtype < 1 || deviceSubtype > 3
            || (deviceType == 2 && deviceSubtype > 2)) {
            return SW_WRONG_P1P2
        }

        if (cipherType == 0) {
            useAes = false
            aesKey = null
        } else if (cipherType == 1) {
            val keyIndex = bytesToInt(data, 3, 2)
            if (keyIndex < 0 || keyIndex >= QrConstants.QR_KEYS.size) {
                AppLogger.w(TAG,"Phase 1: Invalid AES key index %d", keyIndex)
                return SW_CONDITIONS_NOT_SATISFIED
            }
            useAes = true
            aesKey = QrConstants.QR_KEYS[keyIndex]
        } else {
            return SW_CONDITIONS_NOT_SATISFIED
        }

        lastRequest = request
        phase = 1

        AppLogger.d(TAG,"Phase 1: NEGOTIATE OK, cipher=%s", if (useAes) "AES" else "plaintext")
        return buildByteArray { put(0x80.toByte(), 0x12, 0x03, 0x01); put(SW_OK) }
    }

    private fun handlePhase2(request: ApduRequest): ByteArray {
        if (phase != 1 && phase != 2) return SW_CONDITIONS_NOT_SATISFIED

        val data = request.data
        if (data.size != 7) return SW_WRONG_LENGTH

        val operationMode = data[0].toInt() and 0xFF
        val readMode = data[1].toInt() and 0xFF
        if ((operationMode != 1 && operationMode != 2) || (readMode != 0 && readMode != 1)) {
            return SW_WRONG_P1P2
        }

        lastRequest = request
        phase = 2
        sessionNonce = data.extract(3, 4)

        val tokenBytes = tokenData ?: return SW_NOT_FOUND
        val totalSize = tokenBytes.size

        val maxChunk = if (useAes) MAX_CHUNK_AES else MAX_CHUNK_PLAINTEXT
        val offset = ((request.p1.toInt() and 0xFF) shl 8) or (request.p2.toInt() and 0xFF)

        var readSize = data[2].toInt() and 0xFF
        if (readSize == 0) readSize = maxChunk.coerceAtMost(totalSize)

        if (offset >= totalSize) return SW_WRONG_P1P2

        val chunk = tokenBytes.extract(offset, readSize.coerceAtMost(totalSize - offset))

        val inner = buildByteArray { put(chunk.size); put(sessionNonce); put(chunk) }
        var respBytes = buildByteArray { put(0x80); put(inner.size + 5); put(inner) }

        if (useAes && aesKey != null) {
            respBytes = encryptPhase2Response(respBytes)
        }

        AppLogger.d(TAG,"Phase 2: READ offset=%d size=%d/%d", offset, chunk.size, totalSize)
        return buildByteArray { put(respBytes); put(SW_OK) }
    }

    private fun handlePhase3(request: ApduRequest): ByteArray {
        if (phase != 2 && phase != 3) return SW_CONDITIONS_NOT_SATISFIED

        val reqData = request.data
        if (reqData.size < 7) return SW_WRONG_LENGTH

        val decrypted = if (useAes && aesKey != null) {
            decryptPhase3Request(request)
        } else {
            request
        }

        val ddata = decrypted.data
        val completionFlag = ddata[0].toInt() and 0xFF
        val writeMode = ddata[1].toInt() and 0xFF
        val writeSize = ddata[2].toInt() and 0xFF

        if ((completionFlag != 0 && completionFlag != 1) || (writeMode != 0 && writeMode != 1)) {
            return SW_WRONG_P1P2
        }

        lastRequest = decrypted
        phase = if (completionFlag == 1) 4 else 3
        sessionNonce = ddata.extract(3, 4)

        val writeOffset = ((decrypted.p1.toInt() and 0xFF) shl 8) or (decrypted.p2.toInt() and 0xFF)
        val writeData = ddata.extract(7, writeSize.coerceAtMost(ddata.size - 7))

        if (writeBuffer.size() != writeOffset) {
            val existing = writeBuffer.toByteArray()
            writeBuffer.reset()
            writeBuffer.write(existing.extract(0, writeOffset))
            writeBuffer.write(writeData)
            if (existing.size > writeOffset + writeData.size) {
                writeBuffer.write(existing.extract(writeOffset + writeData.size, existing.size))
            }
        } else {
            writeBuffer.write(writeData)
        }

        if (completionFlag == 1) {
            exchangeCompleted = true
            AppLogger.d(TAG,"Phase 3: WRITE complete, stamp=%d bytes", writeBuffer.size())
        } else {
            AppLogger.d(TAG,"Phase 3: WRITE partial, offset=%d, size=%d", writeOffset, writeData.size)
        }

        return buildByteArray { put(0x80.toByte(), 0x04); put(sessionNonce); put(SW_OK) }
    }

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

    private fun encryptPhase2Response(respData: ByteArray): ByteArray {
        val key = aesKey ?: return respData
        val plainPart = respData.extract(3, respData.size - 3)
        val encrypted = aesEncrypt(plainPart, key)
        return buildByteArray { put(respData[0].toInt()); put(encrypted.size + 1); put(respData[2].toInt()); put(encrypted) }
    }

    private fun decryptPhase3Request(request: ApduRequest): ApduRequest {
        val key = aesKey ?: return request
        val data = request.data
        if (data.size <= 3) return request
        val encryptedPart = data.extract(3, data.size - 3)
        val decrypted = aesDecrypt(encryptedPart, key)
        return request.copy(data = buildByteArray { put(data, 0, 3); put(decrypted) })
    }

    private fun parseApdu(raw: ByteArray): ApduRequest {
        val cla = raw[0]
        val ins = raw[1]
        val p1 = raw[2]
        val p2 = raw[3]
        val data = if (raw.size > 5) {
            val lc = raw[4].toInt() and 0xFF
            raw.extract(5, lc.coerceAtMost(raw.size - 5))
        } else {
            ByteArray(0)
        }
        return ApduRequest(cla, ins, p1, p2, data)
    }
}

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
