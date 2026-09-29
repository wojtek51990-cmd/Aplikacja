package com.myscooty.android16

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), ScootyBleManager.Listener {
    private lateinit var ble: ScootyBleManager
    private lateinit var status: TextView
    private lateinit var devices: LinearLayout
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        status.text = if (result.values.all { it }) "Uprawnienia BLE OK — można skanować" else "Brak wymaganych uprawnień Bluetooth"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = ScootyBleManager(this, this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,32,24,24) }
        status = TextView(this).apply { text = "My Scooty PL — BLE Android 16 / ARM64"; textSize = 18f }
        root.addView(status)
        root.addView(Button(this).apply { text = "Skanuj BLE"; setOnClickListener { ble.startScan() } })
        root.addView(Button(this).apply { text = "Zatrzymaj skanowanie"; setOnClickListener { ble.stopScan() } })
        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(devices)
        setContentView(ScrollView(this).apply { addView(root) })
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
    }
    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) { runOnUiThread { devices.addView(Button(this).apply { text=(device.name ?: "Nieznane")+"\n"+device.address+" RSSI "+rssi; setOnClickListener { ble.connect(device) } }) } }
    override fun onConnected(device: BluetoothDevice) { runOnUiThread { status.text="Połączono: "+(device.name ?: device.address) } }
    override fun onServicesDiscovered(gatt: BluetoothGatt, services: List<android.bluetooth.BluetoothGattService>) { runOnUiThread { val sb=StringBuilder("GATT services:\n"); services.forEach { s -> sb.append(s.uuid).append("\n"); s.characteristics.forEach { c -> sb.append("  ↳ ").append(c.uuid).append(" props=").append(c.properties).append("\n") } }; status.text=sb.toString() } }
    override fun onDisconnected() { runOnUiThread { status.text="Rozłączono" } }
    override fun onError(text: String) { runOnUiThread { status.text=text } }
    override fun onStatus(text: String) { runOnUiThread { status.text=text } }
    override fun onDestroy() { ble.close(); super.onDestroy() }
}