package it.atm.app.util

@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHex(): String = toHexString()

@OptIn(ExperimentalStdlibApi::class)
fun String.hexToBytes(): ByteArray = hexToByteArray()
