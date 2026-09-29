package com.myscooty.android16

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScootyProtocolTest {
    @Test
    fun c2UsesBigEndianAndDistanceFactor() {
        val frame = ByteArray(20)
        frame[2] = 0x0C
        frame[3] = 0xE4.toByte()
        frame[4] = 0x04
        frame[5] = 0xB0.toByte()
        frame[6] = 0
        frame[7] = 0
        frame[8] = 0x03
        frame[9] = 0xE8.toByte()
        frame[10] = 0
        frame[11] = 30
        frame[12] = 0
        frame[13] = 40
        frame[15] = 10
        frame[16] = 0xC2.toByte()

        val state = requireNotNull(ScootyProtocol.parse(frame))
        assertEquals(3300, state.voltageRaw)
        assertEquals(1200, state.speedRaw)
        assertEquals(1000L, state.mileageTotalRaw)
        assertEquals(0.85, state.odometerKm, 0.0001)
        assertEquals(30, state.workingCurrentRaw)
        assertEquals(40, state.systemTempRaw)
        assertEquals(10, state.wheelSize)
    }

    @Test
    fun c5DecodesBigEndianStates() {
        val frame = ByteArray(20)
        frame[2] = 0
        frame[3] = 0
        frame[4] = 0x03
        frame[5] = 0xE8.toByte()
        frame[6] = 2
        frame[7] = 1
        frame[8] = 87
        frame[9] = 25
        frame[10] = 0x12
        frame[11] = 0x34
        frame[12] = 1
        frame[13] = 3
        frame[14] = 9
        frame[15] = 4
        frame[16] = 0xC5.toByte()

        val state = requireNotNull(ScootyProtocol.parse(frame))
        assertEquals(1000L, state.mileageCurrentRaw)
        assertEquals(87, state.batteryPercent)
        assertEquals(1, state.gear)
        assertEquals(0x1234, state.malfunction)
        assertEquals(3, state.maxGears)
        assertEquals(9, state.strength)
        assertEquals(4, state.sensitivity)
    }

    @Test
    fun c9PreservesOverlappingRgbReads() {
        val frame = ByteArray(20)
        frame[2] = 3
        frame[3] = 4
        frame[4] = 5
        frame[5] = 6
        frame[6] = 7
        frame[7] = 8
        frame[8] = 9
        frame[9] = 8
        frame[10] = 1
        frame[11] = 2
        frame[12] = 3
        frame[13] = 4
        frame[14] = 5
        frame[15] = 6
        frame[16] = 0xC9.toByte()

        val state = requireNotNull(ScootyProtocol.parse(frame))
        assertEquals(0x0809, state.colorLightR)
        assertEquals(0x0908, state.colorLightG)
        assertEquals(0x0801, state.colorLightB)
        assertEquals(0x0102, state.cruiseState)
        assertEquals(0x0203, state.lightLumince)
        assertEquals(0x0304, state.lightModel)
        assertEquals(6, state.shutdown)
    }

    @Test
    fun commandMatchesOriginalAndRepeats12Times() {
        assertEquals(
            "CC 09 01 00 00 00 C4 FE",
            ScootyProtocol.hex(ScootyProtocol.command(ScootyProtocol.CMD_LIGHT, 1))
        )
        assertEquals(12, ScootyProtocol.repeatedCommand(ScootyProtocol.CMD_LIGHT, 1).size)
    }

    @Test
    fun shortOrUnknownFrameIsIgnored() {
        assertNull(ScootyProtocol.parse(ByteArray(19)))
        val unknown = ByteArray(20)
        unknown[16] = 0x77
        assertNull(ScootyProtocol.parse(unknown))
    }
}
