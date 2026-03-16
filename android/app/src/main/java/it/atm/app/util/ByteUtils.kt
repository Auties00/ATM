package it.atm.app.util

import java.nio.ByteBuffer

fun longToBytes(value: Long, length: Int): ByteArray =
    ByteBuffer.allocate(8).putLong(value).array().copyOfRange(8 - length, 8)

fun bytesToInt(data: ByteArray, offset: Int, length: Int): Int {
    var result = 0
    for (i in 0 until length) {
        result = (result shl 8) or (data[offset + i].toInt() and 0xFF)
    }
    return result
}

fun ByteArray.extract(offset: Int, length: Int): ByteArray {
    val safeEnd = (offset + length).coerceAtMost(size)
    val safeStart = offset.coerceAtMost(safeEnd)
    return copyOfRange(safeStart, safeEnd)
}
