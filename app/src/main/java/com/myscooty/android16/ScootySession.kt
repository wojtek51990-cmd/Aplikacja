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

    fun initialize(context: Context) {
        if (manager == null) {
            manager = ScootyBleManager(context.applicationContext, this)
        }
    }

    fun addListener(listener: ScootyBleManager.Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ScootyBleManager.Listener) {
        listeners.remove(listener)
    }

    fun startScan() = manager?.startScan()
    fun stopScan() = manager?.stopScan()
    fun connect(device: BluetoothDevice) = manager?.connect(device)
    fun disconnect() = manager?.disconnect()
    fun sendCommand(command: Int, value: Int) = manager?.sendCommand(command, value)
    fun sendRaw(bytes: ByteArray, repeat: Int = 1) = manager?.sendRaw(bytes, repeat)

    override fun onStatus(text: String) {
        listeners.forEach { it.onStatus(text) }
    }

    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        listeners.forEach { it.onDeviceFound(device, rssi) }
    }

    override fun onConnected(device: BluetoothDevice) {
        listeners.forEach { it.onConnected(device) }
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        services: List<BluetoothGattService>
    ) {
        listeners.forEach { it.onServicesDiscovered(gatt, services) }
    }

    override fun onNotification(bytes: ByteArray) {
        val parsed = ScootyProtocol.parse(bytes, state)
        if (parsed != null) state = parsed
        listeners.forEach { it.onNotification(bytes) }
    }

    override fun onDisconnected() {
        listeners.forEach { it.onDisconnected() }
    }

    override fun onError(text: String) {
        listeners.forEach { it.onError(text) }
    }
}
