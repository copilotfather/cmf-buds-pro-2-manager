package com.cmf.budscontroller.protocol

import androidx.compose.runtime.Immutable
import com.cmf.budscontroller.i18n.AppStrings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class AncMode(val value: Byte) {
    OFF(0x01.toByte()),
    TRANSPARENCY(0x02.toByte()),
    NOISE_CANCELLATION(0x03.toByte());

    val title: String
        get() = when (this) {
            OFF -> AppStrings.ancOff
            TRANSPARENCY -> AppStrings.ancTransparency
            NOISE_CANCELLATION -> AppStrings.ancCancellation
        }
}

enum class AncLevel(val value: Byte) {
    LOW(0x01.toByte()),
    MID(0x02.toByte()),
    HIGH(0x03.toByte()),
    ADAPTIVE(0x04.toByte());

    val title: String
        get() = when (this) {
            LOW -> AppStrings.ancLevelLow
            MID -> AppStrings.ancLevelMid
            HIGH -> AppStrings.ancLevelHigh
            ADAPTIVE -> AppStrings.ancLevelAdaptive
        }
}

@Immutable
data class BatteryInfo(
    val leftPercent: Int = -1,
    val rightPercent: Int = -1,
    val casePercent: Int = -1,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val isLeftConnected: Boolean = false,
    val isRightConnected: Boolean = false,
    val isCaseAvailable: Boolean = false
)

@Immutable
data class DeviceStatus(
    val battery: BatteryInfo = BatteryInfo(),
    val ancMode: AncMode = AncMode.OFF,
    val ancLevel: AncLevel = AncLevel.HIGH,
    val ultraBassLevel: Int = 3, // 0..5
    val isGameModeEnabled: Boolean = false,
    val firmwareVersion: String = "Unknown",
    val isConnected: Boolean = false,
    val deviceName: String = "CMF BUDS PRO 2",
    val deviceAddress: String = "",
    val connectionType: String = "",
    val isSppActive: Boolean = false,
    val isGattActive: Boolean = false
)

object CmfProtocol {
    // Official Nothing / CMF SPP Bluetooth UUID
    val SPP_UUID: UUID = UUID.fromString("aeac4a03-dff5-498f-843a-34487cf133eb")
    val STANDARD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // BLE GATT Service & Characteristics for CMF / Nothing
    val GATT_CMF_SERVICE_UUID: UUID = UUID.fromString("aeac4a03-dff5-498f-843a-34487cf133eb")
    val GATT_CMF_TX_CHAR_UUID: UUID = UUID.fromString("aeac4a04-dff5-498f-843a-34487cf133eb")
    val GATT_CMF_RX_CHAR_UUID: UUID = UUID.fromString("aeac4a05-dff5-498f-843a-34487cf133eb")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MAGIC_BYTE_0 = 0x55.toByte()
    const val MAGIC_BYTE_1 = 0x60.toByte()
    const val PROTOCOL_VER = 0x01.toByte()

    // 16-bit Command IDs
    const val CMD_READ_BATTERY = 0xC007
    const val CMD_READ_ANC = 0xC01E
    const val CMD_SET_ANC = 0xF00F
    const val CMD_READ_EQ = 0xC01F
    const val CMD_SET_EQ = 0xF010
    const val CMD_READ_LISTEN_MODE = 0xC050
    const val CMD_SET_LISTEN_MODE = 0xF01D
    const val CMD_READ_FIRMWARE = 0xC042
    const val CMD_SET_LATENCY = 0xF040
    const val CMD_SET_HD_AUDIO = 0xF05A // Nothing X Hi-Res LDAC toggle
    // Find My Buds / chirp (Nothing X style)
    const val CMD_FIND_MY_BUDS = 0xF050
    const val FIND_BUDS_STOP = 0x00
    const val FIND_BUDS_LEFT = 0x01
    const val FIND_BUDS_RIGHT = 0x02
    const val FIND_BUDS_BOTH = 0x03

    private var sequenceNumber: Int = 0

    @Synchronized
    fun nextSeq(): Int {
        sequenceNumber = (sequenceNumber + 1) and 0xFF
        return sequenceNumber
    }

    /**
     * Constructs a valid binary protocol packet.
     * Format: [0x55, 0x60, 0x01, cmdLo, cmdHi, len, 0x00, seq, ...payload, crcLo, crcHi]
     */
    fun buildPacket(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val seq = nextSeq()
        val totalLength = 3 + 2 + 1 + 1 + 1 + payload.size + 2
        val packet = ByteArray(totalLength)

        packet[0] = MAGIC_BYTE_0
        packet[1] = MAGIC_BYTE_1
        packet[2] = PROTOCOL_VER
        packet[3] = (command and 0xFF).toByte()
        packet[4] = ((command ushr 8) and 0xFF).toByte()
        packet[5] = payload.size.toByte()
        packet[6] = 0x00
        packet[7] = seq.toByte()

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, 8, payload.size)
        }

        val crcOffset = 8 + payload.size
        val crc = Crc16.calculate(packet, 0, crcOffset)
        packet[crcOffset] = (crc and 0xFF).toByte()
        packet[crcOffset + 1] = ((crc ushr 8) and 0xFF).toByte()

        return packet
    }

    // Packet Builders for CMF Buds Pro 2
    fun createReadBatteryPacket(): ByteArray = buildPacket(CMD_READ_BATTERY)

    fun createReadAncPacket(): ByteArray = buildPacket(CMD_READ_ANC)

    fun createSetAncPacket(mode: AncMode, level: AncLevel = AncLevel.HIGH): ByteArray {
        val payload = when (mode) {
            AncMode.OFF -> byteArrayOf(0x01, 0x01, 0x00)
            AncMode.TRANSPARENCY -> byteArrayOf(0x01, 0x02, 0x00)
            AncMode.NOISE_CANCELLATION -> byteArrayOf(0x01, 0x03, level.value)
        }
        return buildPacket(CMD_SET_ANC, payload)
    }

    fun createSetListenModeCmf(mode: AncMode, level: AncLevel = AncLevel.HIGH): ByteArray {
        val value: Byte = when (mode) {
            AncMode.OFF -> 0x00
            AncMode.TRANSPARENCY -> 0x01
            AncMode.NOISE_CANCELLATION -> (0x02 + level.value - 1).toByte()
        }
        return buildPacket(CMD_SET_LISTEN_MODE, byteArrayOf(value, 0x00))
    }

    fun createSetUltraBassPacket(level: Int): ByteArray {
        val clamped = level.coerceIn(0, 5).toByte()
        return buildPacket(CMD_SET_EQ, byteArrayOf(clamped, 0x00))
    }

    fun createSetGameModePacket(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x02
        return buildPacket(CMD_SET_LATENCY, byteArrayOf(flag, 0x00))
    }

    fun createSetHdAudioPacket(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildPacket(CMD_SET_HD_AUDIO, byteArrayOf(flag, 0x00))
    }

    fun createFindMyBudsPacket(left: Boolean, right: Boolean): ByteArray {
        val mask = (if (left) FIND_BUDS_LEFT else 0) or (if (right) FIND_BUDS_RIGHT else 0)
        val value = if (mask == 0) FIND_BUDS_STOP else mask
        return buildPacket(CMD_FIND_MY_BUDS, byteArrayOf(value.toByte(), 0x00))
    }

    fun createStopFindMyBudsPacket(): ByteArray =
        buildPacket(CMD_FIND_MY_BUDS, byteArrayOf(FIND_BUDS_STOP.toByte(), 0x00))

    fun createReadFirmwarePacket(): ByteArray = buildPacket(CMD_READ_FIRMWARE)

    /**
     * Parses incoming byte buffer and extracts status data if valid.
     */
    fun parsePacket(data: ByteArray, length: Int): PacketResult? {
        if (length < 10) return null

        // Check magic bytes
        if (data[0] != MAGIC_BYTE_0 || data[1] != MAGIC_BYTE_1 || data[2] != PROTOCOL_VER) {
            return null
        }

        val cmdLo = data[3].toInt() and 0xFF
        val cmdHi = data[4].toInt() and 0xFF
        val command = cmdLo or (cmdHi shl 8)

        val payloadLen = data[5].toInt() and 0xFF
        val seq = data[7].toInt() and 0xFF

        if (length < 8 + payloadLen + 2) return null

        val crcOffset = 8 + payloadLen
        val crcLo = data[crcOffset].toInt() and 0xFF
        val crcHi = data[crcOffset + 1].toInt() and 0xFF
        val receivedCrc = crcLo or (crcHi shl 8)

        val calculatedCrc = Crc16.calculate(data, 0, crcOffset)
        if (receivedCrc != calculatedCrc) {
            return null
        }

        val payload = ByteArray(payloadLen)
        System.arraycopy(data, 8, payload, 0, payloadLen)

        return PacketResult(command, seq, payload)
    }
}

@Immutable
data class PacketResult(
    val command: Int,
    val sequence: Int,
    val payload: ByteArray
)
