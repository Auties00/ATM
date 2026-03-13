package it.atm.app.qr

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object QrSignatureEngine {

    fun makeSignature(data: ByteArray, sigType: Int, keyId: Int): ByteArray {
        val base = if (data.size >= 6) data.copyOfRange(0, data.size - 6) else data.copyOf()

        val signed = ByteArray(base.size + 2)
        base.copyInto(signed)
        signed[base.size] = (sigType and 0xFF).toByte()
        signed[base.size + 1] = (keyId and 0xFF).toByte()

        val sha1Digest = MessageDigest.getInstance("SHA-1").digest(signed)
        val padded = ByteArray(32) { 0x1A.toByte() }
        sha1Digest.copyInto(padded, endIndex = sha1Digest.size.coerceAtMost(32))

        val key = SecretKeySpec(QrConstants.QR_KEYS[keyId], "AES")
        val iv = IvParameterSpec(QrConstants.QR_IV)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(padded)

        val enc32 = encrypted.copyOfRange(0, 32)

        val checksum = ByteArray(4)
        for (i in 0 until 32) {
            checksum[i % 4] = (checksum[i % 4].toInt() xor enc32[i].toInt()).toByte()
        }

        val result = ByteArray(signed.size + 4)
        signed.copyInto(result)
        checksum.copyInto(result, destinationOffset = signed.size)
        return result
    }
}
