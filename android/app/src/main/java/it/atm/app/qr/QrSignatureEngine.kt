package it.atm.app.qr

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Faithful Kotlin port of Python's QRCodeGenerator.make_signature.
 *
 * Algorithm:
 *  1. Strip last 6 bytes from data (if data.size >= 6).
 *  2. Append [sigType & 0xFF, keyId & 0xFF] to get "signed".
 *  3. SHA-1 hash of "signed", pad result to 32 bytes with 0x1A.
 *  4. AES-128-CBC encrypt (NoPadding) with QR_KEYS[keyId] and QR_IV.
 *  5. Take first 32 bytes of ciphertext.
 *  6. XOR-fold the 32 bytes into a 4-byte checksum (byte[i % 4] ^= encrypted[i]).
 *  7. Return signed + checksum.
 */
object QrSignatureEngine {

    /**
     * Produces the signed QR payload: the "signed" body (base data minus trailing 6 bytes,
     * plus sigType and keyId bytes) concatenated with a 4-byte AES-derived checksum.
     */
    fun makeSignature(data: ByteArray, sigType: Int, keyId: Int): ByteArray {
        // Step 1 – strip last 6 bytes when possible
        val base = if (data.size >= 6) data.copyOfRange(0, data.size - 6) else data.copyOf()

        // Step 2 – append signature-type and key-id bytes
        val signed = ByteArray(base.size + 2)
        base.copyInto(signed)
        signed[base.size] = (sigType and 0xFF).toByte()
        signed[base.size + 1] = (keyId and 0xFF).toByte()

        // Step 3 – SHA-1 then pad to 32 bytes with 0x1A
        val sha1Digest = MessageDigest.getInstance("SHA-1").digest(signed)
        val padded = ByteArray(32) { 0x1A.toByte() }
        sha1Digest.copyInto(padded, endIndex = sha1Digest.size.coerceAtMost(32))

        // Step 4 – AES-128-CBC encrypt (no padding)
        val key = SecretKeySpec(QrConstants.QR_KEYS[keyId], "AES")
        val iv = IvParameterSpec(QrConstants.QR_IV)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(padded)

        // Step 5 – take first 32 bytes of ciphertext
        val enc32 = encrypted.copyOfRange(0, 32)

        // Step 6 – XOR-fold into 4-byte checksum
        val checksum = ByteArray(4)
        for (i in 0 until 32) {
            checksum[i % 4] = (checksum[i % 4].toInt() xor enc32[i].toInt()).toByte()
        }

        // Step 7 – concatenate signed + checksum
        val result = ByteArray(signed.size + 4)
        signed.copyInto(result)
        checksum.copyInto(result, destinationOffset = signed.size)
        return result
    }

}
