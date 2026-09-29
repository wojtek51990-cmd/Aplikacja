package com.myscooty.android16

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity(), ScootyBleManager.Listener {
    private lateinit var info: TextView
    private lateinit var values: LinearLayout

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
            setPadding(18.dp(), 22.dp(), 18.dp(), 24.dp())
            setBackgroundColor(Color.rgb(9, 16, 26))
        }

        root.addView(title(getString(R.string.settings_title)), lp(b = 8))
        info = label(getString(R.string.settings_ready))
        root.addView(info, lp(b = 14))

        root.addView(section(getString(R.string.units)), lp(b = 5))
        root.addView(row(
            button(getString(R.string.kmh)) { ScootySession.sendCommand(ScootyProtocol.CMD_UNIT, 0) },
            button(getString(R.string.mph)) { ScootySession.sendCommand(ScootyProtocol.CMD_UNIT, 1) }
        ), lp(b = 14))

        root.addView(section(getString(R.string.cruise)), lp(b = 5))
        root.addView(row(
            button(getString(R.string.enable)) { ScootySession.sendCommand(ScootyProtocol.CMD_CRUISE, 1) },
            button(getString(R.string.disable)) { ScootySession.sendCommand(ScootyProtocol.CMD_CRUISE, 0) }
        ), lp(b = 14))

        root.addView(section(getString(R.string.home_lock)), lp(b = 5))
        root.addView(row(
            button(getString(R.string.enable)) { ScootySession.sendCommand(ScootyProtocol.CMD_HOME_LOCK, 1) },
            button(getString(R.string.disable)) { ScootySession.sendCommand(ScootyProtocol.CMD_HOME_LOCK, 0) }
        ), lp(b = 14))

        root.addView(section(getString(R.string.power_timer)), lp(b = 5))
        root.addView(button(getString(R.string.choose_timer)) {
            val timeValues = listOf(0, 5, 10, 15, 20, 30, 40, 50, 60)
            AlertDialog.Builder(this)
                .setTitle(R.string.timer_title)
                .setItems(
                    timeValues.map {
                        if (it == 0) getString(R.string.timer_off) else it.toString() + " min"
                    }.toTypedArray()
                ) { _, which ->
                    ScootySession.sendCommand(ScootyProtocol.CMD_POWER_OFF_TIME, timeValues[which])
                }
                .show()
        }, lp(b = 14))

        root.addView(section(getString(R.string.background_ble)), lp(b = 5))
        val bgText = label(
            if (BleForegroundService.autoReconnectEnabled(this))
                getString(R.string.background_enabled)
            else getString(R.string.background_disabled)
        )
        root.addView(bgText, lp(b = 5))
        root.addView(row(
            button(getString(R.string.background_on)) {
                BleForegroundService.setAutoReconnect(this, true)
                bgText.text = getString(R.string.background_enabled)
                BleForegroundService.start(this)
            },
            button(getString(R.string.background_off)) {
                BleForegroundService.setAutoReconnect(this, false)
                bgText.text = getString(R.string.background_disabled)
                BleForegroundService.stop(this)
            }
        ), lp(b = 14))

        root.addView(section(getString(R.string.controller_info)), lp(b = 5))
        values = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(values, lp(b = 12))

        root.addView(button(getString(R.string.factory_reset)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.factory_reset_title)
                .setMessage(R.string.factory_reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    ScootySession.sendCommand(ScootyProtocol.CMD_FACTORY_RESET, 1)
                }
                .show()
        }, lp(b = 10))

        root.addView(button(getString(R.string.back)) { finish() })
        return ScrollView(this).apply { addView(root) }
    }

    private fun refresh() {
        if (!::values.isInitialized) return
        val s = ScootySession.state
        values.removeAllViews()
        addValue(getString(R.string.current_unit), if (s.isMph) getString(R.string.mph) else getString(R.string.kmh))
        addValue(getString(R.string.battery), s.batteryPercent.toString() + " %")
        addValue(getString(R.string.max_speed_controller), s.maxSpeed.toString())
        addValue(getString(R.string.max_gears), s.maxGears.toString())
        addValue(getString(R.string.gear_speed), s.gearSpeed.toString())
        addValue(getString(R.string.strength), s.strength.toString())
        addValue(getString(R.string.sensitivity), s.sensitivity.toString())
        addValue(getString(R.string.close_time), s.closeTime.toString() + " min")
        addValue(
            getString(R.string.malfunction),
            if (s.malfunction == 0) getString(R.string.no_error) else "0x%04X".format(s.malfunction)
        )
        addValue(getString(R.string.wheel_size), s.wheelSize.toString())
        addValue(
            getString(R.string.raw_rgb),
            s.colorLightR.toString() + " / " + s.colorLightG + " / " + s.colorLightB
        )
        addValue(getString(R.string.light_model), s.lightModel.toString())
        addValue(getString(R.string.light_brightness), s.lightLumince.toString())
        info.text = if (ScootySession.connectedDevice == null)
            getString(R.string.settings_not_connected)
        else getString(R.string.settings_connected)
    }

    private fun addValue(labelText: String, valueText: String) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(label(labelText), LinearLayout.LayoutParams(0, WRAP, 1f))
        r.addView(label(valueText))
        values.addView(r, lp(b = 3))
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

    private fun button(text: String, action: () -> Unit) =
        Button(this).apply { this.text = text; setOnClickListener { action() } }

    private fun section(text: String) = label(text, 17f, Color.WHITE, true)
    private fun title(text: String) = label(text, 26f, Color.WHITE, true)
    private fun label(text: String, size: Float = 14f, color: Int = Color.LTGRAY, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun lp(t: Int = 0, l: Int = 0, b: Int = 0, r: Int = 0) =
        LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(l.dp(), t.dp(), r.dp(), b.dp()) }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onNotification(bytes: ByteArray) { runOnUiThread { refresh() } }
    override fun onStatus(text: String) { runOnUiThread { info.text = text } }
    override fun onDeviceFound(device: android.bluetooth.BluetoothDevice, rssi: Int) {}
    override fun onConnected(device: android.bluetooth.BluetoothDevice) { runOnUiThread { refresh() } }
    override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt, services: List<android.bluetooth.BluetoothGattService>) { runOnUiThread { refresh() } }
    override fun onDisconnected() { runOnUiThread { refresh() } }
    override fun onError(text: String) { runOnUiThread { info.text = text } }

    override fun onDestroy() {
        ScootySession.removeListener(this)
        super.onDestroy()
    }
}
