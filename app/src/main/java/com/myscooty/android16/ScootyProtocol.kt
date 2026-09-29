package com.myscooty.android16

import java.util.Locale
import java.util.UUID

data class ScootyState(
    val voltageRaw: Int = 0,
    val speedRaw: Int = 0,
    val mileageTotalRaw: Long = 0L,
    val workingCurrentRaw: Int = 0,
    val systemTempRaw: Int = 0,
    val wheelSize: Int = 0,
    val mileageCurrentRaw: Long = 0L,
    val gearSpeed: Int = 0,
    val gear: Int = 0,
    val batteryPercent: Int = 0,
    val maxSpeed: Int = 0,
    val malfunction: Int = 0,
    val closeTime: Int = 0,
    val maxGears: Int = 0,
    val strength: Int = 0,
    val sensitivity: Int = 0,
    val cruise: Int = 0,
    val mode: Int = 0,
    val gyro: Int = 0,
    val lock: Int = 0,
    val headlight: Int = 0,
    val lightState: Int = 0,
    val speedLimit: Int = 0,
    val turnLightState: Int = 0,
    val unit: Int = 0,
    val breathState: Int = 0,
    val cyclingState: Int = 0,
    val colorLightR: Int = 0,
    val colorLightG: Int = 0,
    val colorLightB: Int = 0,
    val cruiseState: Int = 0,
    val lightLumince: Int = 0,
    val lightModel: Int = 0,
    val shutdown: Int = 0
) {
    val isMph: Boolean get() = unit == 1
    val speedKmh: Double get() = speedRaw / 100.0
    val speedDisplay: Double get() = if (isMph) speedKmh * MPH_FACTOR else speedKmh
    val speedUnitLabel: String get() = if (isMph) "MP/H" else "KM/H"
    val odometerKm: Double get() = mileageTotalRaw / 1000.0 * 0.85
    val tripKm: Double get() = mileageCurrentRaw / 1000.0 * 0.85
    val odometerDisplay: Double get() = if (isMph) odometerKm * MPH_FACTOR else odometerKm
    val tripDisplay: Double get() = if (isMph) tripKm * MPH_FACTOR else tripKm

    companion object { private const val MPH_FACTOR = 0.621371192237 }
}

/**
 * The original 1.0.13 APK contains three interchangeable BLE profiles.
 * Each profile is (notify characteristic, service, write characteristic).
 */
data class ScootyBleProfile(
    val id: Int,
    val name: String,
    val notifyUuid: UUID,
    val serviceUuid: UUID,
    val writeUuid: UUID
)



object ScootyProtocol {
    const val CCCD_UUID = "00002902-0000-1000-8000-00805F9B34FB"

    const val CMD_GEAR = 2
    const val CMD_SHUTDOWN = 3
    const val CMD_CRUISE = 4
    const val CMD_HOME_LOCK = 5
    const val CMD_UNIT = 6
    const val CMD_LIGHT = 9
    const val CMD_MODE = 11
    const val CMD_POWER_OFF_TIME = 12
    const val CMD_FACTORY_RESET = 24

    // Exact value used by BTPackage.sendCmdData() in the original APK.
    const val DEFAULT_COMMAND_REPEAT = 12
    const val COMMAND_INTERVAL_MS = 3L
    const val PACKET_SIZE = 20

    val PROFILES: List<ScootyBleProfile> = listOf(
        ScootyBleProfile(1, "AB", UUID.fromString(NOTIFY_UUID), UUID.fromString(SERVICE_UUID), UUID.fromString(WRITE_UUID)),
        ScootyBleProfile(2, "FF",
            UUID.fromString("0000FF02-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000FF12-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")),
        ScootyBleProfile(3, "AD",
            UUID.fromString("0000AD02-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000AD00-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000AD01-0000-1000-8000-00805F9B34FB"))
    )

    fun profileForService(uuid: UUID): ScootyBleProfile? = PROFILES.firstOrNull { it.serviceUuid == uuid }

    val PROFILES: List<ScootyBleProfile> = listOf(
        ScootyBleProfile(
            1, "AB",
            UUID.fromString("0000AB02-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000AB00-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000AB01-0000-1000-8000-00805F9B34FB")
        ),
        ScootyBleProfile(
            2, "FF",
            UUID.fromString("0000FF02-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000FF12-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")
        ),
        ScootyBleProfile(
            3, "AD",
            UUID.fromString("0000AD02-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000AD00-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000AD01-0000-1000-8000-00805F9B34FB")
        )
    )

    const val SERVICE_UUID = "0000AB00-0000-1000-8000-00805F9B34FB"
    const val WRITE_UUID = "0000AB01-0000-1000-8000-00805F9B34FB"
    const val NOTIFY_UUID = "0000AB02-0000-1000-8000-00805F9B34FB"

    fun serviceUuid(): UUID = UUID.fromString(SERVICE_UUID)
    fun writeUuid(): UUID = UUID.fromString(WRITE_UUID)
    fun notifyUuid(): UUID = UUID.fromString(NOTIFY_UUID)
    fun cccdUuid(): UUID = UUID.fromString(CCCD_UUID)

    fun profileForService(uuid: UUID): ScootyBleProfile? =
        PROFILES.firstOrNull { it.serviceUuid == uuid }

    fun command(command: Int, value: Int): ByteArray {
        require(command in 0..255)
        require(value in 0..255)
        // Exact original pack(type,data):
        // CC command value 00 00 00 checksum 00 FE
        return byteArrayOf(
            0xCC.toByte(),
            command.toByte(),
            value.toByte(),
            0,
            0,
            0,
            (0xCC xor command xor value).toByte(),
            0xFE.toByte()
        )
    }

    fun repeatedCommand(
        command: Int,
        value: Int,
        count: Int = DEFAULT_COMMAND_REPEAT
    ): List<ByteArray> =
        List(count.coerceAtLeast(1)) { command(command, value) }

    /**
     * Original BTOTOParseImpl.unpack():
     * accepts >=20 bytes, takes type from length-4 and payload bytes 2..15.
     */
    fun parse(packet: ByteArray, previous: ScootyState = ScootyState()): ScootyState? {
        if (packet.size < PACKET_SIZE) return null

        val type = packet[packet.size - 4].toInt() and 0xFF
        if (type != 0xC2 && type != 0xC5 && type != 0xC9) return null

        val payload = packet.copyOfRange(2, 16)
        return when (type) {
            0xC2 -> previous.copy(
                voltageRaw = u16be(payload, 0),
                speedRaw = u16be(payload, 2),
                mileageTotalRaw = u32be(payload, 4),
                workingCurrentRaw = u16be(payload, 8),
                systemTempRaw = u16be(payload, 10),
                wheelSize = u8(payload, 13)
            )
            0xC5 -> previous.copy(
                mileageCurrentRaw = u32be(payload, 0),
                gearSpeed = u8(payload, 4),
                gear = u8(payload, 5),
                batteryPercent = u8(payload, 6),
                maxSpeed = u8(payload, 7),
                malfunction = u16be(payload, 8),
                closeTime = u8(payload, 10),
                maxGears = u8(payload, 11),
                strength = u8(payload, 12),
                sensitivity = u8(payload, 13),
                cruise = u8(payload, 13)
            )
            0xC9 -> previous.copy(
                mode = u8(payload, 0),
                gyro = u8(payload, 1),
                lock = u8(payload, 1),
                headlight = u8(payload, 2),
                lightState = u8(payload, 2),
                speedLimit = u8(payload, 3),
                turnLightState = u8(payload, 4),
                unit = u8(payload, 4),
                breathState = u8(payload, 5),
                cyclingState = u8(payload, 6),
                colorLightR = u16be(payload, 7),
                colorLightG = u16be(payload, 8),
                colorLightB = u16be(payload, 9),
                cruiseState = u16be(payload, 10),
                lightLumince = u16be(payload, 11),
                lightModel = u16be(payload, 12),
                shutdown = u8(payload, 13)
            )
            else -> null
        }
    }

    fun packetType(packet: ByteArray): Int? =
        if (packet.size < PACKET_SIZE) null
        else packet[packet.size - 4].toInt() and 0xFF

    fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(Locale.US, it.toInt() and 0xFF) }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF
    private fun u16be(bytes: ByteArray, offset: Int): Int =
        (u8(bytes, offset) shl 8) or u8(bytes, offset + 1)

    private fun u32be(bytes: ByteArray, offset: Int): Long =
        (u16be(bytes, offset).toLong() shl 16) or u16be(bytes, offset + 2).toLong()
}
