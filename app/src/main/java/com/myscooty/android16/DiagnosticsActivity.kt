package com.myscooty.android16

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity(), ScootyBleManager.Listener {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScootySession.initialize(applicationContext)
        ScootySession.addListener(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 22.dp(), 18.dp(), 24.dp())
            setBackgroundColor(Color.rgb(9, 16, 26))
        }

        content.addView(
            text(getString(R.string.diagnostics_title), 26f, Color.WHITE, true),
            lp(b = 10)
        )

        setContentView(ScrollView(this).apply { addView(content) })
        refresh()
    }

    private fun refresh() {
        if (content.childCount > 1) content.removeViews(1, content.childCount - 1)

        val s = ScootySession.state
        addPair(
            getString(R.string.connected),
            ScootySession.connectedDevice?.let(::safeName) ?: getString(R.string.no_device)
        )
        addPair(
            getString(R.string.last_packet),
            ScootySession.lastPacketType?.let { "0x%02X".format(it) } ?: "—"
        )
        addPair(
            getString(R.string.packet_time),
            if (ScootySession.lastPacketAtMs == 0L) "—"
            else (System.currentTimeMillis() - ScootySession.lastPacketAtMs).toString() + " ms"
        )
        addPair(getString(R.string.voltage), String.format(Locale.US, "%.2f V", s.voltageRaw / 100.0))
        addPair(getString(R.string.speed), String.format(Locale.US, "%.2f %s", s.speedDisplay, s.speedUnitLabel))
        addPair(getString(R.string.total_distance_raw), s.mileageTotalRaw.toString())
        addPair(getString(R.string.trip_raw), s.mileageCurrentRaw.toString())
        addPair(getString(R.string.temperature_raw), s.systemTempRaw.toString())
        addPair(getString(R.string.work_time_raw), s.workingCurrentRaw.toString())
        addPair(getString(R.string.error), "0x%04X".format(s.malfunction))
        addPair(
            getString(R.string.state_flags),
            "MODE=" + s.mode + ", GEAR=" + s.gear + ", LIGHT=" + s.lightState +
                ", CRUISE=" + s.cruiseState + ", LOCK=" + s.lock
        )
        addPair(
            getString(R.string.raw_frame),
            ScootySession.lastFrame?.let(ScootyProtocol::hex) ?: "—"
        )
    }

    private fun addPair(labelText: String, valueText: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())
            setBackgroundColor(Color.rgb(18, 29, 43))
        }
        row.addView(text(labelText, 12f, Color.GRAY))
        row.addView(text(valueText, 14f, Color.WHITE))
        content.addView(row, lp(b = 2))
    }

    private fun safeName(device: BluetoothDevice): String =
        try { device.name ?: device.address } catch (_: SecurityException) { device.address }

    private fun text(v: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun lp(t: Int = 0, l: Int = 0, b: Int = 0, r: Int = 0) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(l.dp(), t.dp(), r.dp(), b.dp()) }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    override fun onNotification(bytes: ByteArray) { runOnUiThread { refresh() } }
    override fun onStatus(text: String) {}
    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {}
    override fun onConnected(device: BluetoothDevice) { runOnUiThread { refresh() } }
    override fun onServicesDiscovered(gatt: BluetoothGatt, services: List<BluetoothGattService>) { runOnUiThread { refresh() } }
    override fun onDisconnected() { runOnUiThread { refresh() } }
    override fun onError(text: String) {}

    override fun onDestroy() {
        ScootySession.removeListener(this)
        super.onDestroy()
    }
}
