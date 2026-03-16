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

inline fun buildByteArray(block: ByteArrayBuilder.() -> Unit): ByteArray {
    val builder = ByteArrayBuilder()
    builder.block()
    return builder.toByteArray()
}

class ByteArrayBuilder {
    @PublishedApi internal sealed class Part {
        abstract val size: Int
        abstract fun writeTo(dest: ByteArray, offset: Int)

        class Bytes(val data: ByteArray) : Part() {
            override val size get() = data.size
            override fun writeTo(dest: ByteArray, offset: Int) = data.copyInto(dest, offset)
        }

        class Zeros(override val size: Int) : Part() {
            override fun writeTo(dest: ByteArray, offset: Int) {}
        }
    }

    @PublishedApi internal val parts = mutableListOf<Part>()
    @PublishedApi internal var totalSize = 0

    fun put(byte: Int) {
        parts.add(Part.Bytes(byteArrayOf(byte.toByte())))
        totalSize += 1
    }

    fun put(bytes: ByteArray) {
        parts.add(Part.Bytes(bytes))
        totalSize += bytes.size
    }

    fun put(vararg bytes: Byte) {
        parts.add(Part.Bytes(bytes))
        totalSize += bytes.size
    }

    fun putZeros(count: Int) {
        parts.add(Part.Zeros(count))
        totalSize += count
    }

    fun toByteArray(): ByteArray {
        val result = ByteArray(totalSize)
        var offset = 0
        for (part in parts) {
            part.writeTo(result, offset)
            offset += part.size
        }
        return result
    }
}
