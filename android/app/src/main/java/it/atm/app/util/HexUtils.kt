package it.atm.app.util

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    val len = length / 2
    val bytes = ByteArray(len)
    for (i in 0 until len) {
        bytes[i] = substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bytes
}
