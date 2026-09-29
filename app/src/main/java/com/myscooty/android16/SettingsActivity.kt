package com.myscooty.android16

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity(), ScootyBleManager.Listener {
    private lateinit var info: TextView
    private lateinit var unitText: TextView
    private lateinit var cruiseText: TextView
    private lateinit var lockText: TextView
    private lateinit var timerText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScootySession.initialize(applicationContext)
        ScootySession.addListener(this)
        setContentView(buildUi())
        refresh()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 28)
            setBackgroundColor(Color.rgb(10, 18, 30))
        }

        root.addView(TextView(this).apply {
            text = "USTAWIENIA MY SCOOTY"
            textSize = 24f
            setTextColor(Color.WHITE)
        }, lp(0,0,0,18))

        info = TextView(this).apply {
            text = "Ustawienia są wysyłane przez natywny kanał BLE."
            textSize = 13f
            setTextColor(Color.LTGRAY)
        }
        root.addView(info, lp(0,0,0,14))

        unitText = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE) }
        root.addView(unitText, lp(0,0,0,6))
        root.addView(row(
            button("KM/H") { ScootySession.sendCommand(ScootyProtocol.CMD_UNIT, 1) },
            button("MPH") { ScootySession.sendCommand(ScootyProtocol.CMD_UNIT, 0) }
        ), lp(0,0,0,14))

        cruiseText = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE) }
        root.addView(cruiseText, lp(0,0,0,6))
        root.addView(row(
            button("Tempomat ON") { ScootySession.sendCommand(ScootyProtocol.CMD_CRUISE, 1) },
            button("Tempomat OFF") { ScootySession.sendCommand(ScootyProtocol.CMD_CRUISE, 0) }
        ), lp(0,0,0,14))

        lockText = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE) }
        root.addView(lockText, lp(0,0,0,6))
        root.addView(row(
            button("Blokada startu") { ScootySession.sendCommand(ScootyProtocol.CMD_NON_ZERO_START_LOCK, 1) },
            button("Start bez blokady") { ScootySession.sendCommand(ScootyProtocol.CMD_NON_ZERO_START_LOCK, 0) }
        ), lp(0,0,0,14))

        timerText = TextView(this).apply { textSize = 16f; setTextColor(Color.WHITE) }
        root.addView(timerText, lp(0,0,0,6))
        root.addView(button("Automatyczne wyłączenie") {
            val values = (1..12).map { it * 5 }.toIntArray()
            AlertDialog.Builder(this)
                .setTitle("Wyłączenie po czasie")
                .setItems(values.map { "$it min" }.toTypedArray()) { _, which ->
                    ScootySession.sendCommand(ScootyProtocol.CMD_POWER_OFF_TIME, values[which])
                }
                .show()
        }, lp(0,0,0,14))

        root.addView(button("Przywróć ustawienia fabryczne") {
            AlertDialog.Builder(this)
                .setTitle("Reset sterownika")
                .setMessage("Polecenie 24/1 zostanie wysłane 12 razy, zgodnie z oryginalną aplikacją.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Resetuj") { _, _ ->
                    ScootySession.sendCommand(ScootyProtocol.CMD_FACTORY_RESET, 1)
                }
                .show()
        }, lp(0,0,0,14))

        root.addView(button("Powrót") { finish() })

        return ScrollView(this).apply { addView(root) }
    }

    private fun refresh() {
        val s = ScootySession.state
        unitText.text = "Jednostka: " + if (s.unit == 1) "MPH" else "KM/H"
        cruiseText.text = "Tempomat: " + if (s.cruiseState == 1) "WŁĄCZONY" else "WYŁĄCZONY"
        lockText.text = "Blokada startu: " + if (s.lock == 1) "WŁĄCZONA" else "WYŁĄCZONA"
        timerText.text = "Czas automatycznego wyłączenia: " + s.closeTime + " min"
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
        ).apply { setMargins(l.dp(), t.dp(), r.dp(), b.dp()) }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onNotification(bytes: ByteArray) { runOnUiThread { refresh() } }
    override fun onStatus(text: String) { runOnUiThread { info.text = text } }
    override fun onDeviceFound(device: android.bluetooth.BluetoothDevice, rssi: Int) {}
    override fun onConnected(device: android.bluetooth.BluetoothDevice) { runOnUiThread { refresh() } }
    override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt, services: List<android.bluetooth.BluetoothGattService>) { runOnUiThread { refresh() } }
    override fun onDisconnected() { runOnUiThread { info.text = "Rozłączono BLE"; refresh() } }
    override fun onError(text: String) { runOnUiThread { info.text = text } }

    override fun onDestroy() {
        ScootySession.removeListener(this)
        super.onDestroy()
    }
}
