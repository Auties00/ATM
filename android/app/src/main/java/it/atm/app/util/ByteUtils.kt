package it.atm.app.util

fun longToBytes(value: Long, length: Int): ByteArray {
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        bytes[length - 1 - i] = ((value shr (i * 8)) and 0xFF).toByte()
    }
    return bytes
}

fun bytesToInt(data: ByteArray, offset: Int, length: Int): Int {
    var result = 0
    for (i in 0 until length) {
        result = (result shl 8) or (data[offset + i].toInt() and 0xFF)
    }
    return result
}

fun ByteArray.extract(offset: Int, length: Int): ByteArray {
    val safeLen = length.coerceAtMost(size - offset).coerceAtLeast(0)
    return copyOfRange(offset, offset + safeLen)
}
