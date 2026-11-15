package com.cmf.budscontroller.protocol

/**
 * High-performance CRC-16 ARC implementation with precomputed lookup table.
 * Polynomial: 0xA001 (reflected 0x8005)
 * Initial Value: 0xFFFF
 * Optimized for zero runtime allocation and maximum throughput.
 */
object Crc16 {
    private val CRC_TABLE = IntArray(256) { i ->
        var crc = i
        for (j in 0 until 8) {
            crc = if ((crc and 1) != 0) {
                (crc ushr 1) xor 0xA001
            } else {
                crc ushr 1
            }
        }
        crc and 0xFFFF
    }

    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0xFFFF
        val end = offset + length
        for (i in offset until end) {
            val tableIndex = (crc xor (data[i].toInt() and 0xFF)) and 0xFF
            crc = (crc ushr 8) xor CRC_TABLE[tableIndex]
        }
        return crc and 0xFFFF
    }
}
