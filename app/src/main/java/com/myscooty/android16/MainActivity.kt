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
import android.provider.Settings
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
    private lateinit var devicesTitle: TextView
    private lateinit var devices: LinearLayout
    private lateinit var dashboard: LinearLayout
    private lateinit var speed: TextView
    private lateinit var unit: TextView
    private lateinit var voltage: TextView
    private lateinit var battery: TextView
    private lateinit var odometer: TextView
    private lateinit var trip: TextView
    private lateinit var gear: TextView
    private lateinit var mode: TextView
    private lateinit var rawFrame: TextView

    private val found = linkedMapOf<String, BluetoothDevice>()
    private var requestedScanAfterPermission = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                result.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false) &&
                result.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)

            if (granted) {
                status.text = "Bluetooth gotowy"
                if (requestedScanAfterPermission) {
                    requestedScanAfterPermission = false
                    ScootySession.startScan()
                }
            } else {
                status.text = "Brak uprawnień Bluetooth"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScootySession.initialize(applicationContext)
        ScootySession.addListener(this)
        setContentView(buildUi())
        updateDashboard()

        if (!hasBlePermission()) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 28)
            setBackgroundColor(Color.rgb(10, 18, 30))
        }

        val title = TextView(this).apply {
            text = "MY SCOOTY PL"
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        root.addView(title, lp())

        val subtitle = TextView(this).apply {
            text = "Sterownik BLE • Android 16 • ARM64"
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }
        root.addView(subtitle, lp(0, 2, 0, 14))

        status = TextView(this).apply {
            text = "Niepołączono"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        root.addView(status, lp(0, 0, 0, 10))

        root.addView(row(
            button("Skanuj BLE") { requestAndScan() },
            button("Zatrzymaj") { ScootySession.stopScan() },
            button("Ustawienia") { startActivity(Intent(this, SettingsActivity::class.java)) }
        ))

        devicesTitle = TextView(this).apply {
            text = "Urządzenia BLE"
            textSize = 17f
            setTextColor(Color.WHITE)
        }
        root.addView(devicesTitle, lp(0, 16, 0, 6))

        devices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(devices)

        val connectHelp = TextView(this).apply {
            text = "Wybierz sterownik hulajnogi z listy. Po połączeniu aplikacja automatycznie włączy AB02 i odbiór danych."
            textSize = 12f
            setTextColor(Color.GRAY)
        }
        root.addView(connectHelp, lp(0, 8, 0, 12))

        dashboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(dashboard)

        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(20, 34, 52))
        }
        speed = TextView(this).apply {
            text = "0"
            textSize = 56f
            setTextColor(Color.WHITE)
        }
        unit = TextView(this).apply {
            text = "KM/H"
            textSize = 18f
            setTextColor(Color.CYAN)
        }
        speedCard.addView(speed)
        speedCard.addView(unit)
        dashboard.addView(speedCard, lp(0, 8, 0, 10))

        dashboard.addView(metricRow("Napięcie", { voltage }, "V"), lp(0, 0, 0, 4))
        dashboard.addView(metricRow("Bateria", { battery }, "%"), lp(0, 0, 0, 4))
        dashboard.addView(metricRow("Przebieg", { odometer }, "km"), lp(0, 0, 0, 4))
        dashboard.addView(metricRow("Trip", { trip }, "km"), lp(0, 0, 0, 4))
        dashboard.addView(metricRow("Bieg", { gear }, ""), lp(0, 0, 0, 4))
        dashboard.addView(metricRow("Tryb", { mode }, ""), lp(0, 0, 0, 10))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        controls.addView(row(
            button("Bieg") { nextGear() },
            button("Tryb") {
                val next = (ScootySession.state.mode % 3) + 1
                ScootySession.sendCommand(ScootyProtocol.CMD_MODE, next)
            }
        ), lp(0, 0, 0, 4))
        controls.addView(row(
            button("Światło") {
                ScootySession.sendCommand(
                    ScootyProtocol.CMD_LIGHT,
                    if (ScootySession.state.lightState == 1) 0 else 1
                )
            },
            button("Tempomat") {
                ScootySession.sendCommand(
                    ScootyProtocol.CMD_CRUISE,
                    if (ScootySession.state.cruiseState == 1) 0 else 1
                )
            }
        ), lp(0, 0, 0, 4))
        controls.addView(row(
            button("Blokada startu") {
                ScootySession.sendCommand(ScootyProtocol.CMD_NON_ZERO_START_LOCK, 1)
            },
            button("Odblokuj start") {
                ScootySession.sendCommand(ScootyProtocol.CMD_NON_ZERO_START_LOCK, 0)
            }
        ), lp(0, 0, 0, 4))
        controls.addView(row(
            button("Wyłącz hulajnogę") {
                ScootySession.sendCommand(
                    ScootyProtocol.CMD_SHUTDOWN_TIMER,
                    if (ScootySession.state.shutdown == 1) 0 else 1
                )
            },
            button("Test komunikacji") {
                ScootySession.sendCommand(ScootyProtocol.CMD_TEST, 0)
            }
        ))
        dashboard.addView(controls)

        rawFrame = TextView(this).apply {
            text = "Ostatnia ramka: —"
            textSize = 11f
            setTextColor(Color.GRAY)
        }
        dashboard.addView(rawFrame, lp(0, 12, 0, 0))

        return ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun metricRow(
        label: String,
        valueProvider: () -> TextView,
        suffix: String
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(Color.rgb(19, 29, 43))
        }

        val left = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val value = valueProvider()
        row.addView(value, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        if (suffix.isNotEmpty()) {
            val s = TextView(this).apply {
                text = " $suffix"
                textSize = 13f
                setTextColor(Color.GRAY)
            }
            row.addView(s)
        }
        return row
    }

    private fun updateDashboard() {
        if (!::speed.isInitialized) return
        val s = ScootySession.state
        val mph = s.unit == 1

        speed.text = String.format(Locale.US, "%.0f", if (mph) s.speedKmh * 0.621371192237 else s.speedKmh)
        unit.text = if (mph) "MI/H" else "KM/H"
        voltage.text = String.format(Locale.US, "%.2f", s.voltage)
        battery.text = s.batteryPercent.toString()
        odometer.text = String.format(Locale.US, "%.2f", if (mph) s.odometerKm * 0.621371192237 else s.odometerKm)
        trip.text = String.format(Locale.US, "%.2f", if (mph) s.tripKm * 0.621371192237 else s.tripKm)
        gear.text = s.gear.toString() + " / tryb prędkości " + s.gearSpeed
        mode.text = s.mode.toString()
    }

    private fun nextGear() {
        val s = ScootySession.state
        when (s.maxSpeed) {
            2 -> ScootySession.sendCommand(
                ScootyProtocol.CMD_GEAR,
                if (s.gearSpeed == 0) 1 else 1
            )
            3 -> {
                val next = when (s.gearSpeed) {
                    0 -> 1
                    1 -> 2
                    else -> 0
                }
                ScootySession.sendCommand(ScootyProtocol.CMD_GEAR, next)
            }
        }
    }

    private fun requestAndScan() {
        if (hasBlePermission()) {
            ScootySession.startScan()
        } else {
            requestedScanAfterPermission = true
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasBlePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    private fun row(vararg children: Button): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        children.forEach { r.addView(it, LinearLayout.LayoutParams(0, 54.dp(), 1f)) }
        return r
    }

    private fun button(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

    private fun lp(t: Int = 0, l: Int = 0, b: Int = 0, r: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(l.dp(), t.dp(), r.dp(), b.dp())
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        val key = device.address
        val first = !found.containsKey(key)
        found[key] = device
        runOnUiThread {
            if (first) {
                val name = try { device.name } catch (_: SecurityException) { null }
                val b = Button(this).apply {
                    text = (name ?: "Nieznane urządzenie") + "\n" + key + "   RSSI " + rssi
                    setOnClickListener {
                        ScootySession.connect(device)
                    }
                }
                devices.addView(b)
            }
        }
    }

    override fun onConnected(device: BluetoothDevice) {
        runOnUiThread {
            val name = try { device.name } catch (_: SecurityException) { null }
            status.text = "Połączono: " + (name ?: device.address)
        }
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        services: List<BluetoothGattService>
    ) {
        runOnUiThread {
            val foundScooty = services.any {
                it.uuid.toString().equals(ScootyProtocol.SERVICE_UUID, ignoreCase = true)
            }
            status.text = if (foundScooty) {
                "Znaleziono protokół My Scooty AB00/AB01/AB02"
            } else {
                "Połączono, ale nie znaleziono protokołu My Scooty"
            }
        }
    }

    override fun onNotification(bytes: ByteArray) {
        runOnUiThread {
            rawFrame.text = "Ostatnia ramka: " + ScootyProtocol.hex(bytes)
            updateDashboard()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            status.text = "Rozłączono"
        }
    }

    override fun onError(text: String) {
        runOnUiThread {
            status.text = text
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStatus(text: String) {
        runOnUiThread {
            status.text = text
        }
    }

    override fun onDestroy() {
        ScootySession.removeListener(this)
        super.onDestroy()
    }
}
