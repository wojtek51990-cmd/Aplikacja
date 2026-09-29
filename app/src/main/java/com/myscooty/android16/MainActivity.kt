package com.myscooty.android16

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), ScootyBleManager.Listener {
    private lateinit var status: TextView
    private lateinit var deviceText: TextView
    private lateinit var devices: LinearLayout
    private lateinit var speed: TextView
    private lateinit var unit: TextView
    private lateinit var voltage: TextView
    private lateinit var battery: TextView
    private lateinit var odometer: TextView
    private lateinit var trip: TextView
    private lateinit var gear: TextView
    private lateinit var maxSpeed: TextView
    private lateinit var mode: TextView
    private lateinit var temperature: TextView
    private lateinit var error: TextView
    private lateinit var lights: TextView
    private lateinit var cruise: TextView
    private lateinit var lock: TextView
    private lateinit var rawFrame: TextView
    private var requestedScanAfterPermission = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val bluetoothOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false) &&
                    result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false))
            status.text = if (bluetoothOk) getString(R.string.bluetooth_ready)
            else getString(R.string.bluetooth_permission_missing)

            if (bluetoothOk && requestedScanAfterPermission) {
                requestedScanAfterPermission = false
                ScootySession.startScan()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("myscooty_native")
        require(nativeAbiLevel() == 64) { "Wymagana biblioteka ARM64" }

        ScootySession.initialize(applicationContext)
        ScootySession.addListener(this)
        setContentView(buildUi())
        updateDashboard()

        if (!hasRequiredPermissions()) {
            permissionLauncher.launch(requiredPermissions())
        } else if (BleForegroundService.autoReconnectEnabled(this)) {
            BleForegroundService.start(this)
        }
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 22.dp(), 18.dp(), 24.dp())
            setBackgroundColor(Color.rgb(9, 16, 26))
        }

        root.addView(text(getString(R.string.app_title), 28f, Color.WHITE, true), lp(b = 4))
        root.addView(text(getString(R.string.app_subtitle), 13f, Color.LTGRAY), lp(b = 12))

        status = text(getString(R.string.status_disconnected), 15f, Color.WHITE, true)
        root.addView(status, lp(b = 4))
        deviceText = text(getString(R.string.no_device), 12f, Color.GRAY)
        root.addView(deviceText, lp(b = 12))

        root.addView(row(
            button(getString(R.string.scan)) { requestAndScan() },
            button(getString(R.string.stop_scan)) { ScootySession.stopScan() },
            button(getString(R.string.disconnect)) {
                ScootySession.disconnect()
                BleForegroundService.stop(this)
            }
        ), lp(b = 8))

        root.addView(sectionTitle(getString(R.string.ble_devices)), lp(t = 6, b = 6))
        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(devices, lp(b = 8))
        root.addView(text(getString(R.string.ble_service_info), 12f, Color.GRAY), lp(b = 12))

        root.addView(buildDashboard(), lp(b = 8))
        root.addView(buildControls(), lp(b = 10))

        root.addView(row(
            button(getString(R.string.settings)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
            button(getString(R.string.diagnostics)) {
                startActivity(Intent(this, DiagnosticsActivity::class.java))
            }
        ), lp(b = 8))

        root.addView(button(getString(R.string.help)) {
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun buildDashboard(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            setBackgroundColor(Color.rgb(22, 38, 58))
        }
        speed = text("0", 62f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        unit = text("KM/H", 18f, Color.CYAN, true).apply { gravity = Gravity.CENTER }
        speedCard.addView(speed, lp(gravity = Gravity.CENTER_HORIZONTAL))
        speedCard.addView(unit, lp(gravity = Gravity.CENTER_HORIZONTAL, b = 3))
        root.addView(speedCard, lp(b = 8))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addMetric(metrics, getString(R.string.voltage), ::voltage, "V")
        addMetric(metrics, getString(R.string.battery), ::battery, "%")
        addMetric(metrics, getString(R.string.odometer), ::odometer, "km")
        addMetric(metrics, getString(R.string.trip), ::trip, "km")
        addMetric(metrics, getString(R.string.gear), ::gear, "")
        addMetric(metrics, getString(R.string.max_speed), ::maxSpeed, "")
        addMetric(metrics, getString(R.string.mode), ::mode, "")
        addMetric(metrics, getString(R.string.temperature), ::temperature, "")
        addMetric(metrics, getString(R.string.lights), ::lights, "")
        addMetric(metrics, getString(R.string.cruise), ::cruise, "")
        addMetric(metrics, getString(R.string.lock), ::lock, "")
        addMetric(metrics, getString(R.string.error), ::error, "")
        root.addView(metrics)

        rawFrame = text(getString(R.string.last_frame_none), 11f, Color.GRAY)
        root.addView(rawFrame, lp(t = 8))
        return root
    }

    private fun buildControls(): LinearLayout {
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(sectionTitle(getString(R.string.controls)), lp(t = 8, b = 6))

        controls.addView(row(
            button(getString(R.string.mode_1)) { ScootySession.sendCommand(ScootyProtocol.CMD_MODE, 1) },
            button(getString(R.string.mode_2)) { ScootySession.sendCommand(ScootyProtocol.CMD_MODE, 2) },
            button(getString(R.string.mode_3)) { ScootySession.sendCommand(ScootyProtocol.CMD_MODE, 3) }
        ), lp(b = 4))

        controls.addView(row(
            button(getString(R.string.gear_1)) { sendGear(0) },
            button(getString(R.string.gear_2)) { sendGear(1) },
            button(getString(R.string.gear_3)) { sendGear(2) }
        ), lp(b = 4))

        controls.addView(row(
            button(getString(R.string.light_on_off)) {
                ScootySession.sendCommand(
                    ScootyProtocol.CMD_LIGHT,
                    if (ScootySession.state.lightState == 1) 0 else 1
                )
            },
            button(getString(R.string.cruise_on_off)) {
                ScootySession.sendCommand(
                    ScootyProtocol.CMD_CRUISE,
                    if (ScootySession.state.cruiseState == 1) 0 else 1
                )
            }
        ), lp(b = 4))

        controls.addView(row(
            button(getString(R.string.lock_on)) { ScootySession.sendCommand(ScootyProtocol.CMD_HOME_LOCK, 1) },
            button(getString(R.string.lock_off)) { ScootySession.sendCommand(ScootyProtocol.CMD_HOME_LOCK, 0) }
        ), lp(b = 4))

        controls.addView(row(
            button(getString(R.string.shutdown)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.shutdown_title)
                    .setMessage(R.string.shutdown_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        ScootySession.sendCommand(
                            ScootyProtocol.CMD_SHUTDOWN,
                            if (ScootySession.state.shutdown == 1) 0 else 1
                        )
                    }
                    .show()
            },
            button(getString(R.string.factory_reset)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.factory_reset_title)
                    .setMessage(R.string.factory_reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        ScootySession.sendCommand(ScootyProtocol.CMD_FACTORY_RESET, 1)
                    }
                    .show()
            }
        ))
        return controls
    }

    private fun sendGear(value: Int) {
        val max = ScootySession.state.maxGears
        if (max > 0 && value + 1 > max) {
            Toast.makeText(this, getString(R.string.gear_unavailable, max), Toast.LENGTH_SHORT).show()
            return
        }
        ScootySession.sendCommand(ScootyProtocol.CMD_GEAR, value)
    }

    private fun addMetric(
        parent: LinearLayout,
        label: String,
        provider: () -> TextView,
        suffix: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(13.dp(), 9.dp(), 13.dp(), 9.dp())
            setBackgroundColor(Color.rgb(18, 29, 43))
        }
        row.addView(text(label, 14f, Color.LTGRAY), LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(provider())
        if (suffix.isNotEmpty()) row.addView(text(" $suffix", 12f, Color.GRAY))
        parent.addView(row, lp(b = 2))
    }

    private fun updateDashboard() {
        if (!::speed.isInitialized) return
        val s = ScootySession.state
        speed.text = String.format(Locale.US, "%.0f", s.speedDisplay)
        unit.text = s.speedUnitLabel
        voltage.text = String.format(Locale.US, "%.2f", s.voltageRaw / 100.0)
        battery.text = s.batteryPercent.toString()
        odometer.text = String.format(Locale.US, "%.2f", s.odometerDisplay)
        trip.text = String.format(Locale.US, "%.2f", s.tripDisplay)
        gear.text = s.gear.toString()
        maxSpeed.text = s.maxSpeed.toString()
        mode.text = s.mode.toString()
        temperature.text = if (s.systemTempRaw == 0) "—" else s.systemTempRaw.toString()
        lights.text = if (s.lightState == 1) getString(R.string.on) else getString(R.string.off)
        cruise.text = if (s.cruiseState == 1) getString(R.string.on) else getString(R.string.off)
        lock.text = if (s.lock == 1) getString(R.string.on) else getString(R.string.off)
        error.text = if (s.malfunction == 0) getString(R.string.no_error)
        else "0x%04X".format(s.malfunction)

        rawFrame.text = ScootySession.lastFrame?.let {
            getString(
                R.string.last_frame,
                ScootyProtocol.packetType(it)?.let { type -> "0x%02X".format(type) } ?: "—",
                ScootyProtocol.hex(it)
            )
        } ?: getString(R.string.last_frame_none)

        ScootySession.connectedDevice?.let {
            deviceText.text = getString(R.string.connected_device, safeName(it), it.address)
        }
    }

    private fun requestAndScan() {
        if (hasRequiredPermissions()) ScootySession.startScan()
        else {
            requestedScanAfterPermission = true
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    private fun hasRequiredPermissions(): Boolean {
        val bluetoothOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        val notificationOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return bluetoothOk && notificationOk
    }

    private fun row(vararg children: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            children.forEach {
                addView(it, LinearLayout.LayoutParams(0, 52.dp(), 1f).apply {
                    setMargins(2.dp(), 0, 2.dp(), 0)
                })
            }
        }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun sectionTitle(label: String) = text(label, 18f, Color.WHITE, true)

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun lp(
        t: Int = 0, l: Int = 0, b: Int = 0, r: Int = 0, gravity: Int? = null
    ) = LinearLayout.LayoutParams(WRAP, WRAP).apply {
        setMargins(l.dp(), t.dp(), r.dp(), b.dp())
        gravity?.let { this.gravity = it }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun safeName(device: BluetoothDevice): String =
        try { device.name ?: device.address } catch (_: SecurityException) { device.address }

    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        val address = device.address
        if (devices.findViewWithTag<View>(address) != null) return

        devices.addView(
            button(getString(R.string.device_row, safeName(device), address, rssi)) {
                BleForegroundService.saveAddress(this, address)
                BleForegroundService.start(this, address)
                status.text = getString(R.string.status_connecting)
            }.apply { tag = address },
            lp(b = 4)
        )
    }

    override fun onConnected(device: BluetoothDevice) {
        runOnUiThread {
            status.text = getString(R.string.status_connected)
            deviceText.text = getString(R.string.connected_device, safeName(device), device.address)
            updateDashboard()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, services: List<BluetoothGattService>) {
        runOnUiThread {
            status.text = if (services.any { it.uuid == ScootyProtocol.serviceUuid() })
                getString(R.string.status_protocol_ready)
            else getString(R.string.status_protocol_missing)
            updateDashboard()
        }
    }

    override fun onNotification(bytes: ByteArray) { runOnUiThread { updateDashboard() } }

    override fun onDisconnected() {
        runOnUiThread {
            status.text = getString(R.string.status_disconnected)
            updateDashboard()
        }
    }

    override fun onError(text: String) {
        runOnUiThread {
            status.text = text
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStatus(text: String) { runOnUiThread { status.text = text } }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    override fun onDestroy() {
        ScootySession.removeListener(this)
        super.onDestroy()
    }

    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    companion object {
        @JvmStatic external fun nativeAbiLevel(): Int
    }
}
