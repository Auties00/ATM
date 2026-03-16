package it.atm.app.qr

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object QrSignatureEngine {

    private val sha1 = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-1") }
    private val aesCipher = ThreadLocal.withInitial { Cipher.getInstance("AES/CBC/NoPadding") }

    fun makeSignature(data: ByteArray, sigType: Int, keyId: Int): ByteArray {
        val baseLen = if (data.size >= 6) data.size - 6 else data.size
        val signed = ByteArray(baseLen + 2)
        data.copyInto(signed, endIndex = baseLen)
        signed[baseLen] = (sigType and 0xFF).toByte()
        signed[baseLen + 1] = (keyId and 0xFF).toByte()

        val digest = sha1.get()!!
        digest.reset()
        val sha1Bytes = digest.digest(signed)
        val padded = ByteArray(32) { 0x1A.toByte() }
        sha1Bytes.copyInto(padded, endIndex = sha1Bytes.size.coerceAtMost(32))

        val cipher = aesCipher.get()!!
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(QrConstants.QR_KEYS[keyId], "AES"), IvParameterSpec(QrConstants.QR_IV))
        val encrypted = cipher.doFinal(padded)

        val checksum = ByteArray(4)
        for (i in 0 until 32) {
            checksum[i % 4] = (checksum[i % 4].toInt() xor encrypted[i].toInt()).toByte()
        }

        val result = ByteArray(signed.size + 4)
        signed.copyInto(result)
        checksum.copyInto(result, destinationOffset = signed.size)
        return result
    }
}
