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
    val earbudListeningPreset: Int = -1,
    val isEarbudListeningPresetAvailable: Boolean = false,
    val isEarbudCustomEqAvailable: Boolean = false,
    val isInEarDetectionEnabled: Boolean = false,
    val isInEarDetectionAvailable: Boolean = false,
    val isLatencyModeEnabled: Boolean = false,
    val isLatencyModeAvailable: Boolean = false,
    val spatialAudioMode: Int = -1,
    val isSpatialHeadTrackingEnabled: Boolean = false,
    val isSpatialAudioAvailable: Boolean = false,
    val leftDoubleTapAction: Int = -1,
    val leftTripleTapAction: Int = -1,
    val leftHoldAction: Int = -1,
    val rightDoubleTapAction: Int = -1,
    val rightTripleTapAction: Int = -1,
    val rightHoldAction: Int = -1,
    val isGestureConfigAvailable: Boolean = false,
    val isDualConnectionEnabled: Boolean = false,
    val isDualConnectionAvailable: Boolean = false,
    val rememberedDevices: List<DualConnectionDevice> = emptyList(),
    val isRememberedDeviceListAvailable: Boolean = false,
    val hardwareModel: String = "",
    val earbudCustomBass: Int = 0,
    val earbudCustomMid: Int = 0,
    val earbudCustomTreble: Int = 0,
    val firmwareVersion: String = "Unknown",
    val isConnected: Boolean = false,
    val deviceName: String = "CMF BUDS PRO 2",
    val deviceAddress: String = "",
    val connectionType: String = "",
    val isSppActive: Boolean = false,
    val isGattActive: Boolean = false
)

data class EarbudGestureMapping(
    val device: Int,
    val button: Int,
    val gesture: Int,
    val action: Int
)

data class DualConnectionDevice(
    val macAddress: String,
    val name: String,
    val isConnected: Boolean,
    val isCurrentDevice: Boolean
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
    const val CMD_READ_DEVICE_MODEL = 0xC01C
    const val CMD_READ_LATENCY = 0xC041
    const val CMD_SET_IN_EAR = 0xF004
    const val CMD_READ_IN_EAR = 0xC00E
    const val CMD_READ_SPATIAL = 0xC04F
    const val CMD_SET_SPATIAL = 0xF052
    const val CMD_READ_BASS = 0xC04E
    const val CMD_SET_BASS = 0xF051
    const val CMD_READ_CUSTOM_EQ = 0xC044
    const val CMD_SET_CUSTOM_EQ = 0xF041
    const val CMD_READ_GESTURES = 0xC018
    const val CMD_SET_GESTURE = 0xF003
    const val CMD_READ_DUAL_CONNECTION = 0xC027
    const val CMD_READ_DUAL_DEVICE_LIST = 0xC028
    const val CMD_SET_DUAL_CONNECTION = 0xF01A
    const val CMD_SET_DUAL_DEVICE_CONNECTION = 0xF01B
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

    fun createSetEarbudListeningPresetPacket(preset: Int): ByteArray =
        buildPacket(CMD_SET_LISTEN_MODE, byteArrayOf(preset.coerceIn(0, 6).toByte(), 0x00))

    fun createReadEarbudListeningPresetPacket(): ByteArray = buildPacket(CMD_READ_LISTEN_MODE)

    fun createReadEarbudCustomEqPacket(): ByteArray = buildPacket(CMD_READ_CUSTOM_EQ)

    fun createReadLatencyPacket(): ByteArray = buildPacket(CMD_READ_LATENCY)

    fun createSetLatencyPacket(enabled: Boolean): ByteArray =
        buildPacket(CMD_SET_LATENCY, byteArrayOf(if (enabled) 0x01 else 0x02, 0x00))

    fun createReadInEarPacket(): ByteArray = buildPacket(CMD_READ_IN_EAR)

    fun createSetInEarPacket(enabled: Boolean): ByteArray =
        buildPacket(CMD_SET_IN_EAR, byteArrayOf(0x01, 0x01, if (enabled) 0x01 else 0x00))

    fun createReadSpatialAudioPacket(): ByteArray = buildPacket(CMD_READ_SPATIAL)

    fun createSetSpatialAudioPacket(mode: Int, headTracking: Boolean): ByteArray =
        buildPacket(CMD_SET_SPATIAL, byteArrayOf(mode.coerceIn(0, 4).toByte(), if (headTracking) 0x01 else 0x00))

    fun createReadEarbudGesturesPacket(): ByteArray = buildPacket(CMD_READ_GESTURES)

    fun createReadDualConnectionPacket(): ByteArray = buildPacket(CMD_READ_DUAL_CONNECTION)

    fun createSetDualConnectionPacket(enabled: Boolean): ByteArray =
        buildPacket(CMD_SET_DUAL_CONNECTION, byteArrayOf(if (enabled) 1 else 0))

    fun createReadDualDeviceListPacket(offset: Int): ByteArray =
        buildPacket(CMD_READ_DUAL_DEVICE_LIST, byteArrayOf(offset.coerceIn(0, 255).toByte()))

    fun createSetDualDeviceConnectionPacket(macAddress: String, connect: Boolean): ByteArray {
        val bytes = macAddress.split('-', ':').map { it.toIntOrNull(16) }
        require(bytes.size == 6 && bytes.all { it != null && it in 0..255 })
        return buildPacket(CMD_SET_DUAL_DEVICE_CONNECTION, byteArrayOf(if (connect) 1 else 0) + bytes.map { it!!.toByte() }.toByteArray())
    }

    fun parseDualConnectionDeviceList(payload: ByteArray): List<DualConnectionDevice>? {
        if (payload.size < 3) return null
        val count = payload[2].toInt() and 0xFF
        if (count > 32) return null
        var offset = 3
        val devices = ArrayList<DualConnectionDevice>(count)
        repeat(count) {
            if (offset + 8 > payload.size) return null
            val flags = payload[offset].toInt() and 0xFF
            val mac = payload.copyOfRange(offset + 1, offset + 7).joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
            val nameLength = (payload[offset + 7].toInt() and 0x7F)
            if (offset + 8 + nameLength > payload.size) return null
            val name = String(payload, offset + 8, nameLength, Charsets.UTF_8)
            devices += DualConnectionDevice(
                macAddress = mac,
                name = name,
                isConnected = flags and 0x0F != 0,
                isCurrentDevice = flags and 0xF0 != 0
            )
            offset += 8 + nameLength
        }
        return devices
    }

    fun createSetEarbudGesturePacket(device: Int, gesture: Int, action: Int): ByteArray {
        require(device == 2 || device == 3)
        require(gesture in setOf(2, 3, 7))
        require(action in when (gesture) {
            2 -> setOf(1, 2, 8, 9, 11)
            3 -> setOf(1, 8, 9, 11)
            else -> setOf(1, 10, 11)
        })
        return buildPacket(CMD_SET_GESTURE, byteArrayOf(0x01, device.toByte(), 0x01, gesture.toByte(), action.toByte()))
    }

    fun parseEarbudGestures(payload: ByteArray): List<EarbudGestureMapping>? {
        if (payload.isEmpty()) return null
        val count = payload[0].toInt() and 0xFF
        if (count > 32 || payload.size < 1 + count * 4) return null
        return List(count) { index ->
            val offset = 1 + index * 4
            EarbudGestureMapping(
                device = payload[offset].toInt() and 0xFF,
                button = payload[offset + 1].toInt() and 0xFF,
                gesture = payload[offset + 2].toInt() and 0xFF,
                action = payload[offset + 3].toInt() and 0xFF
            )
        }
    }

    fun createSetEarbudCustomEqPacket(bass: Int, mid: Int, treble: Int): ByteArray {
        val bands = listOf(bass, mid, treble).map { it.coerceIn(-6, 6).toFloat() }
        val ordered = listOf(bands[1], bands[2], bands[0])
        val payload = byteArrayOf(
            0x03, 0, 0, 0, 0, 1, 0, 0, 0,
            0, 0, 0, 0x75, 0x44, 0xC3.toByte(), 0xF5.toByte(), 0x28, 0x3F,
            0x02, 0, 0, 0, 0, 0, 0xC0.toByte(), 0x5A, 0x45,
            0, 0, 0x80.toByte(), 0x3F, 0, 0, 0, 0, 0,
            0, 0, 0, 0x0C, 0x43, 0xCD.toByte(), 0xCC.toByte(), 0x4C, 0x3F,
            0, 0, 0, 0, 0, 0, 0, 0
        )
        val highest = ordered.maxOrNull() ?: 0f
        encodeCustomEqFloat(-highest, total = true).copyInto(payload, 1)
        ordered.forEachIndexed { index, value ->
            encodeCustomEqFloat(value).copyInto(payload, 6 + index * 13)
        }
        return buildPacket(CMD_SET_CUSTOM_EQ, payload)
    }

    fun parseEarbudCustomEq(payload: ByteArray): Triple<Int, Int, Int>? {
        if (payload.size < 36) return null
        fun band(offset: Int): Int = decodeCustomEqFloat(payload.copyOfRange(offset, offset + 4))
            .toInt().coerceIn(-6, 6)
        return Triple(band(32), band(6), band(19))
    }

    private fun encodeCustomEqFloat(value: Float, total: Boolean = false): ByteArray {
        if (total && value >= 0f) return byteArrayOf(0, 0, 0, 0x80.toByte())
        val bigEndian = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()
        if (value != 0f && bigEndian[0] == 0.toByte() && bigEndian[1] == 0.toByte() && bigEndian[2] == 0.toByte()) {
            bigEndian[3] = (bigEndian[3].toInt() or 0x80).toByte()
        }
        return bigEndian.reversedArray()
    }

    private fun decodeCustomEqFloat(littleEndian: ByteArray): Float {
        val big = littleEndian.reversedArray()
        if (big[0] == 0.toByte() && big[1] == 0.toByte() && big[2] == 0.toByte() && (big[3].toInt() and 0x80) != 0) {
            big[3] = (big[3].toInt() and 0x7F).toByte()
            return -ByteBuffer.wrap(big.reversedArray()).order(ByteOrder.LITTLE_ENDIAN).float
        }
        return ByteBuffer.wrap(big.reversedArray()).order(ByteOrder.LITTLE_ENDIAN).float
    }

    fun createSetUltraBassPacket(level: Int): ByteArray {
        val clamped = level.coerceIn(0, 5)
        val payload = byteArrayOf((if (clamped == 0) 0 else 1).toByte(), (clamped * 2).toByte())
        return buildPacket(CMD_SET_BASS, payload)
    }

    fun createReadBassPacket(): ByteArray = buildPacket(CMD_READ_BASS)

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

    fun createReadDeviceModelPacket(): ByteArray = buildPacket(CMD_READ_DEVICE_MODEL)

    /**
     * Parses incoming byte buffer and extracts status data if valid.
     */
    fun parsePacket(data: ByteArray, length: Int): PacketResult? {
        if (length < 10 || length > data.size) return null

        // Check magic bytes
        if (data[0] != MAGIC_BYTE_0 || data[1] != MAGIC_BYTE_1 || data[2] != PROTOCOL_VER) {
            return null
        }

        val cmdLo = data[3].toInt() and 0xFF
        val cmdHi = data[4].toInt() and 0xFF
        val rawCommand = cmdLo or (cmdHi shl 8)
        // Device responses use direction 0x40; normalize them to their GET command (0xC0xx)
        // so status handlers can match the request IDs consistently.
        val command = if (cmdHi == 0x40) cmdLo or 0xC000 else rawCommand

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
