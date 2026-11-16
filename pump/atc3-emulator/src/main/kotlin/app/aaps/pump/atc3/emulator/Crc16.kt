package app.aaps.pump.atc3.emulator

/** CRC-16/MODBUS, as the protocol description gives it; the emulator's own copy, not the driver's. */
internal object Crc16 {

    fun modbus(data: ByteArray, length: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    /** The CRC as the two bytes appended to a frame, low byte first. */
    fun modbusLeBytes(data: ByteArray, length: Int = data.size): ByteArray {
        val crc = modbus(data, length)
        return byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }
}
