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

        // Status words matching original VtsExchangeError SW1/SW2 values
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_WRONG_INS = byteArrayOf(0x6D.toByte(), 0x00)              // UNRECOGNIZED_COMMAND
        private val SW_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00)             // WRONG_P1_P2_FIELDS
        private val SW_WRONG_LE = byteArrayOf(0x6C.toByte(), 0x08)               // WRONG_LE_FIELD
        private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00)           // WRONG_COMMAND_LENGTH
        private val SW_OUT_OF_ORDER = byteArrayOf(0x69.toByte(), 0x81.toByte())  // OUT_OF_ORDER_COMMAND
        private val SW_UNSUPPORTED_CIPHER = byteArrayOf(0x69.toByte(), 0x84.toByte()) // UNSUPPORTED_CYPHER
        private val SW_KEY_NOT_FOUND = byteArrayOf(0x69.toByte(), 0x89.toByte()) // KEY_NOT_FOUND
        private val SW_CRYPTO_ERROR = byteArrayOf(0x69.toByte(), 0x88.toByte())  // COULD_NOT_ENCRYPT/DECRYPT
        private val SW_NO_TICKET = byteArrayOf(0x6A.toByte(), 0x82.toByte())     // NO_TICKET_SELECTED / SDK_NOT_READY
        private val SW_EXCHANGE_FAILED = byteArrayOf(0x6F.toByte(), 0x00)        // EXCHANGE_FAILED

        // SDK version: Version.of(2,9,123).getShortString() = String.format("%d%d", 2, 9) = "29"
        // Interpreted as hex by ByteUtils.stringToBytes → [0x29]
        @OptIn(ExperimentalStdlibApi::class)
        private val SDK_VERSION_SHORT: ByteArray = run {
            val parts = AuthConstants.SDK_VERSION.split(".")
            val hexStr = if (parts.size >= 2) "${parts[0]}${parts[1]}" else "00"
            hexStr.hexToByteArray()
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
        validationStamp = null
    }

    var exchangeCompleted = false
        private set

    var validationStamp: ByteArray? = null
        private set

    val updatedFullToken: ByteArray?
        get() = validationStamp

    fun processCommand(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_EXCHANGE_FAILED
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
            SW_EXCHANGE_FAILED
        }
    }

    // Phase 0: SELECT AID (CLA=00 INS=A4 P1=04 P2=00)
    private fun handlePhase0(request: ApduRequest): ByteArray {
        if (request.p1.toInt() != 0x04 || request.p2.toInt() != 0x00) return SW_WRONG_P1P2
        if (phase != -1) return SW_OUT_OF_ORDER

        val data = tokenStore.loadActiveVToken()
        if (data == null || data.size < 57) {
            AppLogger.w(TAG,"Phase 0: No active VToken available")
            return SW_NO_TICKET
        }

        tokenData = data
        lastRequest = request
        phase = 0

        val deviceUidHex = tokenStore.getDeviceUid()
        val deviceUidLong = deviceUidHex.toULong(16).toLong()
        val deviceUidBytes = longToBytes(deviceUidLong, 0)
        val tokenSizeBytes = longToBytes(data.size.toLong(), 6)

        AppLogger.d(TAG,"Phase 0: SELECT OK, tokenSize=%d", data.size)
        return buildByteArray {
            // FCI template: 6F with hardcoded length 2A (42 bytes)
            put(0x6F); put(0x2A)
            // DF Name: 84 10 <AID>
            put(0x84); put(AID.size); put(AID)
            // FCI proprietary template: A5 16 (22 bytes)
            put(0xA5); put(0x16)
            // FCI issuer discretionary data: BF0C 13 (19 bytes)
            put(0xBF.toByte(), 0x0C); put(0x13)
            // Device UID: C7 08 <8 bytes>
            put(0xC7); put(0x08); put(deviceUidBytes)
            // VToken metadata: 53 07 (7 bytes: 01 01 <tokenLen:2B> 10 01 <sdkVer:1B>)
            put(0x53); put(0x07)
            put(0x01); put(0x01)
            put(tokenSizeBytes)
            put(0x10); put(0x01)
            put(SDK_VERSION_SHORT)
            // Status word
            put(SW_OK)
        }
    }

    // Phase 1: NEGOTIATE (CLA=80 INS=A5 P1=10 P2=00)
    private fun handlePhase1(request: ApduRequest): ByteArray {
        if (request.p1.toInt() != 0x10 || request.p2.toInt() != 0x00) return SW_WRONG_P1P2
        if (phase != 0) return SW_OUT_OF_ORDER

        val data = request.data
        if (data.size != 5) return SW_WRONG_LENGTH

        val deviceType = data[0].toInt() and 0xFF
        val deviceSubtype = data[1].toInt() and 0xFF
        val cipherType = data[2].toInt() and 0xFF

        if (deviceType < 1 || deviceType > 23 || deviceSubtype < 1 || deviceSubtype > 3
            || (deviceType == 2 && deviceSubtype > 2)) {
            return SW_WRONG_LE
        }

        when (cipherType) {
            0 -> {
                useAes = false
                aesKey = null
            }
            1 -> {
                val keyIndex = bytesToInt(data, 3, 2)
                if (keyIndex < 0 || keyIndex >= QrConstants.QR_KEYS.size) {
                    AppLogger.w(TAG, "Phase 1: AES key index %d not found", keyIndex)
                    return SW_KEY_NOT_FOUND
                }
                useAes = true
                aesKey = QrConstants.QR_KEYS[keyIndex]
            }
            else -> {
                AppLogger.w(TAG, "Phase 1: Unsupported cipher type %d", cipherType)
                return SW_UNSUPPORTED_CIPHER
            }
        }

        lastRequest = request
        phase = 1

        AppLogger.d(TAG,"Phase 1: NEGOTIATE OK, cipher=%s", if (useAes) "AES" else "plaintext")
        return buildByteArray { put(0x80.toByte(), 0x12, 0x03, 0x01); put(SW_OK) }
    }

    // Phase 2: READ TOKEN (CLA=80 INS=A6 P1/P2=offset)
    private fun handlePhase2(request: ApduRequest): ByteArray {
        if (phase != 1 && phase != 2) return SW_OUT_OF_ORDER

        val data = request.data
        if (data.size != 7) return SW_WRONG_LENGTH

        val operationMode = data[0].toInt() and 0xFF
        val readMode = data[1].toInt() and 0xFF
        if ((operationMode != 1 && operationMode != 2) || (readMode != 0 && readMode != 1)) {
            return SW_WRONG_LE
        }

        lastRequest = request
        phase = 2
        sessionNonce = data.extract(3, 4)

        val tokenBytes = tokenData ?: return SW_NO_TICKET
        val totalSize = tokenBytes.size

        val maxChunk = if (useAes) MAX_CHUNK_AES else MAX_CHUNK_PLAINTEXT
        val offset = ((request.p1.toInt() and 0xFF) shl 8) or (request.p2.toInt() and 0xFF)

        var readSize = data[2].toInt() and 0xFF
        if (readSize == 0) readSize = maxChunk.coerceAtMost(totalSize)

        if (offset >= totalSize) {
            return SW_WRONG_LE
        }

        if (readMode == 0 && readSize < totalSize) {
            return SW_WRONG_LE
        }

        // Strict validation: read size cannot exceed max chunk
        if (readSize > maxChunk) {
            return SW_WRONG_LE
        }

        val chunk = tokenBytes.extract(offset, readSize.coerceAtMost(totalSize - offset))

        val inner = buildByteArray { put(chunk.size); put(sessionNonce); put(chunk) }
        var respBytes = buildByteArray { put(0x80); put(inner.size); put(inner) }

        if (useAes && aesKey != null) {
            respBytes = encryptPhase2Response(respBytes)
        }

        AppLogger.d(TAG,"Phase 2: READ offset=%d size=%d/%d", offset, chunk.size, totalSize)
        return buildByteArray { put(respBytes); put(SW_OK) }
    }

    // Phase 3: WRITE TOKEN (CLA=80 INS=A7 P1/P2=write offset)
    private fun handlePhase3(request: ApduRequest): ByteArray {
        if (phase != 2 && phase != 3) return SW_OUT_OF_ORDER

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
            return SW_WRONG_LE
        }

        // Strict validation: data field must be long enough
        if (ddata.size < writeSize + 7) {
            return SW_WRONG_LE
        }

        lastRequest = decrypted
        sessionNonce = ddata.extract(3, 4)

        val writeOffset = ((decrypted.p1.toInt() and 0xFF) shl 8) or (decrypted.p2.toInt() and 0xFF)
        val writeData = ddata.extract(7, writeSize)

        if (writeBuffer.size() == writeOffset) {
            writeBuffer.write(writeData)
        } else {
            val existing = writeBuffer.toByteArray()
            writeBuffer.reset()
            writeBuffer.write(existing.extract(0, writeOffset))
            writeBuffer.write(writeData)
            if (existing.size > writeOffset + writeData.size) {
                writeBuffer.write(existing.extract(writeOffset + writeData.size, existing.size - (writeOffset + writeData.size)))
            }
        }

        if (completionFlag == 1) {
            phase = 4
            exchangeCompleted = true
            validationStamp = writeBuffer.toByteArray()
            AppLogger.d(TAG,"Phase 3: WRITE complete, stamp=%d bytes", writeBuffer.size())
        } else {
            phase = 2
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

    // APDU parser matching original ApduByteParser with extended-length support
    private fun parseApdu(raw: ByteArray): ApduRequest {
        val cla = raw[0]
        val ins = raw[1]
        val p1 = raw[2]
        val p2 = raw[3]

        if (raw.size <= 4) {
            // Case 1: no Lc, no Le
            return ApduRequest(cla, ins, p1, p2)
        }

        val byte4 = raw[4].toInt() and 0xFF

        if (raw.size == 5) {
            // Case 2: Le only (1 byte)
            val le = if (byte4 == 0) 256 else byte4
            return ApduRequest(cla, ins, p1, p2, le = le)
        }

        if (byte4 == 0 && raw.size == 7) {
            // Extended Le only (3 bytes: 00 + 2 bytes)
            val le = bytesToInt(raw, 5, 2)
            return ApduRequest(cla, ins, p1, p2, le = if (le == 0) 65536 else le)
        }

        if (byte4 == 0 && raw.size > 7) {
            // Extended Lc (3 bytes: 00 + 2 bytes) + data
            val lc = bytesToInt(raw, 5, 2)
            val data = raw.extract(7, lc.coerceAtMost(raw.size - 7))
            return ApduRequest(cla, ins, p1, p2, data)
        }

        // Short form: byte4 is Lc
        val lc = byte4
        val data = raw.extract(5, lc.coerceAtMost(raw.size - 5))
        val remaining = raw.size - 5 - data.size

        val le = when {
            remaining == 1 -> {
                val v = raw[5 + data.size].toInt() and 0xFF
                if (v == 0) 256 else v
            }
            remaining >= 2 -> bytesToInt(raw, 5 + data.size, remaining.coerceAtMost(2))
            else -> 0
        }

        return ApduRequest(cla, ins, p1, p2, data, le)
    }
}

data class ApduRequest(
    val cla: Byte,
    val ins: Byte,
    val p1: Byte,
    val p2: Byte,
    val data: ByteArray = ByteArray(0),
    val le: Int = 0
) {
    fun isMatch(cla: Int, ins: Int): Boolean {
        return (this.cla.toInt() and 0xFF) == cla && (this.ins.toInt() and 0xFF) == ins
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApduRequest

        if (cla != other.cla) return false
        if (ins != other.ins) return false
        if (p1 != other.p1) return false
        if (p2 != other.p2) return false
        if (le != other.le) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result: Int = 0 + cla
        result = 31 * result + ins
        result = 31 * result + p1
        result = 31 * result + p2
        result = 31 * result + le
        result = 31 * result + data.contentHashCode()
        return result
    }
}
