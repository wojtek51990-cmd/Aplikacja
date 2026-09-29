package com.myscooty.android16

/**
 * Pełna mapa komend znalezionych w My Scooty 1.0.13.
 * Wartości są identyczne z oryginalnym BTPackage.sendCmdData(type, data).
 */
enum class ScootyCommand(val id: Int, val labelPl: String) {
    GEAR(ScootyProtocol.CMD_GEAR, "Bieg"),
    SHUTDOWN(ScootyProtocol.CMD_SHUTDOWN, "Wyłączenie"),
    CRUISE(ScootyProtocol.CMD_CRUISE, "Tempomat"),
    HOME_LOCK(ScootyProtocol.CMD_HOME_LOCK, "Blokada"),
    UNIT(ScootyProtocol.CMD_UNIT, "Jednostki"),
    LIGHT(ScootyProtocol.CMD_LIGHT, "Światła"),
    MODE(ScootyProtocol.CMD_MODE, "Tryb"),
    POWER_OFF_TIME(ScootyProtocol.CMD_POWER_OFF_TIME, "Timer wyłączenia"),
    FACTORY_RESET(ScootyProtocol.CMD_FACTORY_RESET, "Reset fabryczny")
}

object ScootyCommands {
    val all: List<ScootyCommand> = ScootyCommand.entries

    fun find(id: Int): ScootyCommand? = all.firstOrNull { it.id == id }

    fun packet(command: ScootyCommand, value: Int): ByteArray =
        ScootyProtocol.command(command.id, value)

    fun packets(command: ScootyCommand, value: Int): List<ByteArray> =
        ScootyProtocol.repeatedCommand(command.id, value)
}
