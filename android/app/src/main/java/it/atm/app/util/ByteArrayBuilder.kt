package it.atm.app.util

inline fun buildByteArray(block: ByteArrayBuilder.() -> Unit): ByteArray {
    val builder = ByteArrayBuilder()
    builder.block()
    return builder.toByteArray()
}

class ByteArrayBuilder {
    @PublishedApi internal val parts = mutableListOf<ByteArray>()
    @PublishedApi internal var totalSize = 0

    fun put(byte: Int) {
        parts.add(byteArrayOf(byte.toByte()))
        totalSize += 1
    }

    fun put(bytes: ByteArray) {
        parts.add(bytes)
        totalSize += bytes.size
    }

    fun put(vararg bytes: Byte) {
        parts.add(bytes)
        totalSize += bytes.size
    }

    fun putZeros(count: Int) {
        parts.add(ByteArray(count))
        totalSize += count
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }
}
