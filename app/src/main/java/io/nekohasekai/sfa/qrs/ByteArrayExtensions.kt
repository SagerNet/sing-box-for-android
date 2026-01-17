package io.nekohasekai.sfa.qrs

fun ByteArray.readIntLE(offset: Int): Int = (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8) or
    ((this[offset + 2].toInt() and 0xFF) shl 16) or
    ((this[offset + 3].toInt() and 0xFF) shl 24)

fun ByteArray.writeIntLE(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
    this[offset + 2] = (value shr 16).toByte()
    this[offset + 3] = (value shr 24).toByte()
}

fun ByteArray.writeIntBE(offset: Int, value: Int) {
    this[offset] = (value shr 24).toByte()
    this[offset + 1] = (value shr 16).toByte()
    this[offset + 2] = (value shr 8).toByte()
    this[offset + 3] = value.toByte()
}
