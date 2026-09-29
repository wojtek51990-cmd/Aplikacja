package com.myscooty.android16

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet

private const val MAX_RECENT_FRAMES = 20
private const val PREFS = "myscooty_diagnostics"
private const val KEY_STATE = "state"
private const val KEY_LAST_FRAME = "last_frame"
private const val KEY_LAST_TYPE = "last_type"
private const val KEY_LAST_AT = "last_at"
private const val KEY_HISTORY = "history"

object ScootySession : ScootyBleManager.Listener {
    private var manager: ScootyBleManager? = null
    private val listeners = CopyOnWriteArraySet<ScootyBleManager.Listener>()

    var state: ScootyState = ScootyState()
        private set
    var connectedDevice: BluetoothDevice? = null
        private set
    var lastFrame: ByteArray? = null
        private set
    var lastPacketType: Int? = null
        private set
    var lastPacketAtMs: Long = 0L
        private set

    val recentFrames: List<String>
        get() = recentFramesBuffer.toList()

    private var packetBuffer = ByteArray(0)
    private val recentFramesBuffer = ArrayDeque<String>()
    private var preferences: SharedPreferences? = null
    private var lastPersistAtMs = 0L

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (manager == null) manager = ScootyBleManager(appContext, this)
        if (preferences == null) {
            preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            restorePersistedState()
        }
    }

    fun currentProfile(): ScootyBleProfile? = manager?.currentProfile()

    fun addListener(listener: ScootyBleManager.Listener) { listeners.add(listener) }
    fun removeListener(listener: ScootyBleManager.Listener) { listeners.remove(listener) }

    fun startScan() = manager?.startScan()
    fun stopScan() = manager?.stopScan()
    fun connect(device: BluetoothDevice) = manager?.connect(device)
    fun disconnect() = manager?.disconnect()
    fun sendCommand(command: Int, value: Int) = manager?.sendCommand(command, value)

    override fun onStatus(text: String) { listeners.forEach { it.onStatus(text) } }
    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) { listeners.forEach { it.onDeviceFound(device, rssi) } }
    override fun onConnected(device: BluetoothDevice) {
        connectedDevice = device
        listeners.forEach { it.onConnected(device) }
    }
    override fun onServicesDiscovered(gatt: BluetoothGatt, services: List<BluetoothGattService>) {
        listeners.forEach { it.onServicesDiscovered(gatt, services) }
    }

    override fun onNotification(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        packetBuffer += bytes

        while (packetBuffer.size >= ScootyProtocol.PACKET_SIZE) {
            val candidateOffset = findFrameOffset(packetBuffer)
            if (candidateOffset < 0) {
                packetBuffer = packetBuffer.takeLast(ScootyProtocol.PACKET_SIZE - 1).toByteArray()
                break
            }
            if (candidateOffset > 0) {
                packetBuffer = packetBuffer.copyOfRange(candidateOffset, packetBuffer.size)
                if (packetBuffer.size < ScootyProtocol.PACKET_SIZE) break
            }

            val packet = packetBuffer.copyOfRange(0, ScootyProtocol.PACKET_SIZE)
            packetBuffer = packetBuffer.copyOfRange(ScootyProtocol.PACKET_SIZE, packetBuffer.size)
            val parsed = ScootyProtocol.parse(packet, state)
            if (parsed != null) {
                state = parsed
                lastPacketType = ScootyProtocol.packetType(packet)
                lastFrame = packet
                lastPacketAtMs = System.currentTimeMillis()
                rememberFrame(packet)
                persistDiagnostics()
            }
        }

        listeners.forEach { it.onNotification(bytes) }
    }

    private fun findFrameOffset(buffer: ByteArray): Int {
        if (buffer.size < ScootyProtocol.PACKET_SIZE) return -1
        for (offset in 0..buffer.size - ScootyProtocol.PACKET_SIZE) {
            val type = buffer[offset + ScootyProtocol.PACKET_SIZE - 4].toInt() and 0xFF
            if (type == 0xC2 || type == 0xC5 || type == 0xC9) return offset
        }
        return -1
    }

    private fun rememberFrame(packet: ByteArray) {
        recentFramesBuffer.addFirst(ScootyProtocol.hex(packet))
        while (recentFramesBuffer.size > MAX_RECENT_FRAMES) recentFramesBuffer.removeLast()
    }

    private fun persistDiagnostics(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAtMs < 750L) return
        preferences?.edit()
            ?.putString(KEY_STATE, state.toJson().toString())
            ?.putString(KEY_LAST_FRAME, lastFrame?.let(ScootyProtocol::hex))
            ?.putInt(KEY_LAST_TYPE, lastPacketType ?: -1)
            ?.putLong(KEY_LAST_AT, lastPacketAtMs)
            ?.putString(KEY_HISTORY, recentFramesBuffer.joinToString("\n"))
            ?.apply()
        lastPersistAtMs = now
    }

    private fun restorePersistedState() {
        val p = preferences ?: return
        try {
            val saved = p.getString(KEY_STATE, null)
            if (!saved.isNullOrBlank()) state = ScootyState.fromJson(org.json.JSONObject(saved))
        } catch (_: Exception) {
            state = ScootyState()
        }
        lastFrame = p.getString(KEY_LAST_FRAME, null)?.let(::hexToBytes)
        lastPacketType = p.getInt(KEY_LAST_TYPE, -1).takeIf { it >= 0 }
        lastPacketAtMs = p.getLong(KEY_LAST_AT, 0L)
        recentFramesBuffer.clear()
        p.getString(KEY_HISTORY, null)?.lineSequence()?.filter { it.isNotBlank() }
            ?.take(MAX_RECENT_FRAMES)?.forEach(recentFramesBuffer::addLast)
    }

    private fun hexToBytes(hex: String): ByteArray? = try {
        hex.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }.toByteArray()
    } catch (_: Exception) {
        null
    }

    override fun onDisconnected() {
        connectedDevice = null
        packetBuffer = ByteArray(0)
        persistDiagnostics(force = true)
        listeners.forEach { it.onDisconnected() }
    }

    override fun onError(text: String) { listeners.forEach { it.onError(text) } }
}
