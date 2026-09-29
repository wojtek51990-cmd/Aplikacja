package com.myscooty.android16

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.ArrayDeque

class ScootyBleManager(context: Context, private val listener: Listener) {
    interface Listener {
        fun onStatus(text: String)
        fun onDeviceFound(device: BluetoothDevice, rssi: Int)
        fun onConnected(device: BluetoothDevice)
        fun onServicesDiscovered(gatt: BluetoothGatt, services: List<BluetoothGattService>)
        fun onNotification(bytes: ByteArray)
        fun onDisconnected()
        fun onError(text: String)
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var activeProfile: ScootyBleProfile? = null
    private var scanning = false
    private var notificationsReady = false

    private val handler = Handler(Looper.getMainLooper())
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInProgress = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener.onDeviceFound(result.device, result.rssi)
            listener.onStatus(
                "Znaleziono BLE: " +
                    (result.scanRecord?.deviceName ?: result.device.name ?: "Nieznane")
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onError("Skanowanie BLE nieudane: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!permissions()) {
            listener.onError("Brak wymaganych uprawnień Bluetooth")
            return
        }
        if (adapter?.isEnabled != true) {
            listener.onError("Bluetooth jest wyłączony")
            return
        }
        if (scanning) return

        val bleScanner = scanner ?: run {
            listener.onError("BLE jest niedostępne")
            return
        }

        scanning = true
        listener.onStatus("Skanowanie urządzeń BLE…")
        bleScanner.startScan(
            listOf(ScanFilter.Builder().build()),
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback
        )

        handler.postDelayed({
            if (scanning) stopScan()
        }, 15_000L)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanner?.stopScan(scanCallback)
        scanning = false
        listener.onStatus("Skanowanie zatrzymane")
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!permissions()) {
            listener.onError("Brak BLUETOOTH_CONNECT")
            return
        }

        stopScan()
        handler.removeCallbacksAndMessages(null)
        writeQueue.clear()
        writeInProgress = false
        notificationsReady = false
        writeCharacteristic = null
        notifyCharacteristic = null
        activeProfile = null

        gatt?.close()
        gatt = null

        val displayName = device.name ?: device.address
        listener.onStatus("Łączenie z " + displayName + "…")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.disconnect()
    }

    fun sendCommand(command: Int, value: Int) {
        ScootyProtocol.repeatedCommand(command, value).forEach(::enqueueWrite)
        listener.onStatus("Wysyłam komendę " + command + " = " + value + " (12×)")
    }

    fun sendRaw(bytes: ByteArray, repeat: Int = 1) {
        repeat(repeat.coerceAtLeast(1)) {
            enqueueWrite(bytes.copyOf())
        }
    }

    @SuppressLint("MissingPermission")
    private fun enqueueWrite(bytes: ByteArray) {
        if (!notificationsReady) {
            listener.onError("Kanał BLE nie jest jeszcze gotowy do zapisu")
            return
        }
        writeQueue.addLast(bytes)
        drainWriteQueue()
    }

    @SuppressLint("MissingPermission")
    private fun drainWriteQueue() {
        if (writeInProgress || writeQueue.isEmpty()) return

        val g = gatt ?: run {
            writeQueue.clear()
            listener.onError("Brak połączenia GATT")
            return
        }

        val characteristic = writeCharacteristic ?: run {
            writeQueue.clear()
            listener.onError("Nie znaleziono charakterystyki zapisu")
            return
        }

        val data = writeQueue.removeFirst()
        writeInProgress = true

        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = data
            if (g.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS else 1
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            writeInProgress = false
            listener.onError("Błąd zapisu BLE: $status")
            handler.postDelayed({ drainWriteQueue() }, ScootyProtocol.COMMAND_INTERVAL_MS)
            return
        }

        writeInProgress = false
        handler.postDelayed({ drainWriteQueue() }, ScootyProtocol.COMMAND_INTERVAL_MS)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScan()
        handler.removeCallbacksAndMessages(null)
        writeQueue.clear()
        writeInProgress = false
        gatt?.close()
        gatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        activeProfile = null
        notificationsReady = false
    }

    fun currentProfile(): ScootyBleProfile? = activeProfile

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("Błąd połączenia GATT: $status")
                g.close()
                if (gatt === g) gatt = null
                listener.onDisconnected()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener.onConnected(g.device)
                    listener.onStatus("Połączono BLE — wyszukiwanie usług…")
                    @SuppressLint("MissingPermission")
                    val started = g.discoverServices()
                    if (!started) listener.onError("Nie udało się rozpocząć discoverServices")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    notificationsReady = false
                    writeCharacteristic = null
                    notifyCharacteristic = null
                    activeProfile = null
                    writeQueue.clear()
                    writeInProgress = false
                    listener.onStatus("Rozłączono BLE")
                    g.close()
                    if (gatt === g) gatt = null
                    listener.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("Błąd wyszukiwania usług GATT: $status")
                return
            }

            listener.onServicesDiscovered(g, g.services)

            var selectedService: BluetoothGattService? = null
            var selected: ScootyBleProfile? = null
            for (service in g.services) {
                val profile = ScootyProtocol.profileForService(service.uuid) ?: continue
                val notify = service.getCharacteristic(profile.notifyUuid)
                val write = service.getCharacteristic(profile.writeUuid)
                if (notify != null && write != null) {
                    selectedService = service
                    selected = profile
                    break
                }
            }

            if (selectedService == null || selected == null) {
                listener.onError("Nie znaleziono zgodnego profilu My Scooty (AB/FF/AD)")
                return
            }

            activeProfile = selected
            writeCharacteristic = selectedService.getCharacteristic(selected.writeUuid)
            notifyCharacteristic = selectedService.getCharacteristic(selected.notifyUuid)

            val notify = notifyCharacteristic ?: run {
                listener.onError("Brak charakterystyki powiadomień " + selected.notifyUuid)
                return
            }
            val write = writeCharacteristic ?: run {
                listener.onError("Brak charakterystyki zapisu " + selected.writeUuid)
                return
            }

            @SuppressLint("MissingPermission")
            val localNotify = g.setCharacteristicNotification(notify, true)
            if (!localNotify) {
                listener.onError("Nie udało się włączyć powiadomień BLE")
                return
            }

            val cccd = notify.getDescriptor(ScootyProtocol.cccdUuid())
            if (cccd == null) {
                notificationsReady = true
                listener.onStatus(
                    "BLE gotowe — profil " + selected.name + ": " + selected.serviceUuid
                )
                return
            }

            @SuppressLint("MissingPermission")
            val descriptorStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(
                    cccd,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
            } else {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (g.writeDescriptor(cccd)) BluetoothGatt.GATT_SUCCESS else 1
            }

            if (descriptorStatus != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("Błąd włączania powiadomień: $descriptorStatus")
            } else {
                listener.onStatus(
                    "Wybrano profil " + selected.name +
                        " — RX " + notify.uuid + ", TX " + write.uuid
                )
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == ScootyProtocol.cccdUuid()) {
                notificationsReady = status == BluetoothGatt.GATT_SUCCESS
                if (notificationsReady) {
                    val p = activeProfile
                    listener.onStatus(
                        if (p == null) "BLE gotowe"
                        else "BLE gotowe — profil " + p.name + " / zapis NO RESPONSE"
                    )
                } else {
                    listener.onError("CCCD nie został zapisany: $status")
                }
            }
        }

        @Deprecated("Deprecated on API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == notifyCharacteristic?.uuid) {
                listener.onNotification(characteristic.value?.copyOf() ?: return)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == notifyCharacteristic?.uuid) {
                listener.onNotification(value.copyOf())
            }
        }
    }

    private fun permissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            )
}
