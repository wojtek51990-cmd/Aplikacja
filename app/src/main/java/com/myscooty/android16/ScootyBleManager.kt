package com.myscooty.android16

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class ScootyBleManager(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onStatus(text: String)
        fun onDeviceFound(device: BluetoothDevice, rssi: Int)
        fun onConnected(device: BluetoothDevice)
        fun onServicesDiscovered(gatt: BluetoothGatt, services: List<BluetoothGattService>)
        fun onDisconnected()
        fun onError(text: String)
    }

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            listener.onDeviceFound(result.device, result.rssi)
            listener.onStatus("Znaleziono BLE: " + (result.scanRecord?.deviceName ?: result.device.name ?: "Nieznane"))
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onError("Skanowanie BLE nieudane: " + errorCode)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!permissions()) { listener.onError("Brak uprawnień Bluetooth"); return }
        if (adapter?.isEnabled != true) { listener.onError("Bluetooth jest wyłączony"); return }
        if (scanning) return
        val s = scanner ?: run { listener.onError("BLE niedostępne"); return }
        scanning = true
        listener.onStatus("Skanowanie BLE...")
        s.startScan(
            listOf(ScanFilter.Builder().build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanner?.stopScan(callback)
        scanning = false
        listener.onStatus("Skanowanie zatrzymane")
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!permissions()) { listener.onError("Brak BLUETOOTH_CONNECT"); return }
        stopScan()
        gatt?.close()
        gatt = null
        listener.onStatus("Łączenie z " + (device.name ?: device.address) + "...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() { gatt?.disconnect() }

    fun close() {
        stopScan()
        gatt?.close()
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("GATT connection error: " + status)
                g.close()
                if (gatt === g) gatt = null
                listener.onDisconnected()
                return
            }
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener.onConnected(g)
                    listener.onStatus("Połączono BLE — wyszukiwanie usług...")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onStatus("Rozłączono BLE")
                    g.close()
                    if (gatt === g) gatt = null
                    listener.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onServicesDiscovered(g, g.services)
                listener.onStatus("Usługi GATT znalezione: " + g.services.size)
            } else listener.onError("Błąd discoverServices: " + status)
        }
    }

    private fun permissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
         ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
}