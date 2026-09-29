package com.myscooty.android16

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

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

    private var packetBuffer = ByteArray(0)

    fun initialize(context: Context) {
        if (manager == null) manager = ScootyBleManager(context.applicationContext, this)
    }

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
            val packet = packetBuffer.copyOfRange(0, ScootyProtocol.PACKET_SIZE)
            packetBuffer = packetBuffer.copyOfRange(ScootyProtocol.PACKET_SIZE, packetBuffer.size)
            val parsed = ScootyProtocol.parse(packet, state)
            if (parsed != null) {
                state = parsed
                lastPacketType = ScootyProtocol.packetType(packet)
                lastFrame = packet
                lastPacketAtMs = System.currentTimeMillis()
            }
        }

        listeners.forEach { it.onNotification(bytes) }
    }

    override fun onDisconnected() {
        connectedDevice = null
        packetBuffer = ByteArray(0)
        listeners.forEach { it.onDisconnected() }
    }

    override fun onError(text: String) { listeners.forEach { it.onError(text) } }
}
