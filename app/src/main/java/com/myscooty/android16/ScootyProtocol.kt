package com.myscooty.android16

/**
 * Native implementation of the communication protocol reconstructed from
 * the original My Scooty 1.0.13 application.
 *
 * Command frame used by the original app:
 * CC cmd value 00 00 00 checksum 00 FE
 * checksum = CC XOR cmd XOR value
 *
 * The original application queued each command 12 times. We preserve that
 * reliability behavior in the BLE layer.
 */
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
    val speedKmh: Double get() = speedRaw / 100.0
    val voltage: Double get() = voltageRaw / 100.0
    val odometerKm: Double get() = mileageTotalRaw / 1000.0 * 0.85
    val tripKm: Double get() = mileageCurrentRaw / 1000.0 * 0.85
    val currentRaw: Int get() = workingCurrentRaw
    val systemTemperatureRaw: Int get() = systemTempRaw
}

object ScootyProtocol {
    fun serviceUuid(): java.util.UUID = java.util.UUID.fromString(SERVICE_UUID)
    fun writeUuid(): java.util.UUID = java.util.UUID.fromString(WRITE_UUID)
    fun notifyUuid(): java.util.UUID = java.util.UUID.fromString(NOTIFY_UUID)
    fun cccdUuid(): java.util.UUID = java.util.UUID.fromString(CCCD_UUID)

    const val SERVICE_UUID = "0000AB00-0000-1000-8000-00805F9B34FB"
    const val WRITE_UUID = "0000AB01-0000-1000-8000-00805F9B34FB"
    const val NOTIFY_UUID = "0000AB02-0000-1000-8000-00805F9B34FB"
    const val CCCD_UUID = "00002902-0000-1000-8000-00805F9B34FB"

    const val CMD_GEAR = 2
    const val CMD_TEST = 3
    const val CMD_CRUISE = 4
    const val CMD_NON_ZERO_START_LOCK = 5
    const val CMD_SHUTDOWN_TIMER = 5
    const val CMD_UNIT = 6
    const val CMD_LIGHT = 9
    const val CMD_MODE = 11
    const val CMD_POWER_OFF_TIME = 12
    const val CMD_FACTORY_RESET = 24

    fun command(command: Int, value: Int): ByteArray {
        require(command in 0..255) { "command out of range" }
        require(value in 0..255) { "value out of range" }
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

    /**
     * Exact command retry behavior from the original app: 12 queued writes.
     */
    fun repeatedCommand(command: Int, value: Int, count: Int = 12): List<ByteArray> =
        List(count) { command(command, value) }

    fun parse(notification: ByteArray, previous: ScootyState = ScootyState()): ScootyState? {
        if (notification.size < 20) return null

        val type = notification[notification.size - 4].toInt() and 0xFF
        val payload = notification.copyOfRange(2, 16)

        return when (type) {
            0xC2 -> previous.copy(
                voltageRaw = u16(payload, 0),
                speedRaw = u16(payload, 2),
                mileageTotalRaw = u32(payload, 4),
                workingCurrentRaw = u16(payload, 8),
                systemTempRaw = u16(payload, 10),
                wheelSize = u8(payload, 13)
            )

            0xC5 -> previous.copy(
                mileageCurrentRaw = u32(payload, 0),
                gearSpeed = u8(payload, 4),
                gear = u8(payload, 5),
                batteryPercent = u8(payload, 6),
                maxSpeed = u8(payload, 7),
                malfunction = u16(payload, 8),
                closeTime = if (previous.closeTime == 0) u8(payload, 10) else previous.closeTime,
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
                colorLightR = u16(payload, 7),
                colorLightG = u16(payload, 8),
                colorLightB = u16(payload, 9),
                cruiseState = u16(payload, 10),
                lightLumince = u16(payload, 11),
                lightModel = u16(payload, 12),
                shutdown = u8(payload, 13)
            )

            else -> null
        }
    }

    fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun u8(bytes: ByteArray, offset: Int): Int =
        bytes[offset].toInt() and 0xFF

    private fun u16(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong().shl(16) or u16(bytes, offset + 2).toLong()
}
