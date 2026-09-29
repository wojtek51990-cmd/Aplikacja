package com.myscooty.android16

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

class BleForegroundService : Service(), ScootyBleManager.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectPending = false

    override fun onCreate() {
        super.onCreate()
        ScootySession.initialize(applicationContext)
        ScootySession.addListener(this)
        createNotificationChannel()

        val notification = buildNotification(getString(R.string.notification_connecting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)
            ?: prefs().getString(KEY_LAST_ADDRESS, null)

        if (!address.isNullOrBlank() && hasBluetoothPermission()) {
            prefs().edit().putString(KEY_LAST_ADDRESS, address).apply()
            reconnectTo(address)
        }
        return START_STICKY
    }

    private fun reconnectTo(address: String) {
        if (reconnectPending) return
        reconnectPending = true
        handler.postDelayed({
            try {
                val bluetoothManager = getSystemService(BluetoothManager::class.java)
                val adapter: BluetoothAdapter? = bluetoothManager?.adapter
                val device = adapter?.getRemoteDevice(address)
                if (device != null) ScootySession.connect(device)
            } catch (_: IllegalArgumentException) {
                updateNotification(getString(R.string.notification_bad_device))
            } finally {
                reconnectPending = false
            }
        }, 500L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ScootySession.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStatus(text: String) = updateNotification(text)

    override fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        // Skanowanie jest obsługiwane przez ekran aplikacji; usługa tylko utrzymuje połączenie.
    }

    override fun onConnected(device: BluetoothDevice) {
        updateNotification(getString(R.string.notification_connected, safeName(device)))
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        services: List<BluetoothGattService>
    ) {
        updateNotification(getString(R.string.notification_ready))
    }

    override fun onNotification(bytes: ByteArray) {
        val s = ScootySession.state
        updateNotification(
            getString(
                R.string.notification_data,
                s.speedDisplay,
                s.speedUnitLabel,
                s.batteryPercent
            )
        )
    }

    override fun onDisconnected() {
        updateNotification(getString(R.string.notification_disconnected))
        if (prefs().getBoolean(KEY_AUTO_RECONNECT, true)) {
            prefs().getString(KEY_LAST_ADDRESS, null)?.let(::reconnectTo)
        }
    }

    override fun onError(text: String) = updateNotification(text)

    private fun safeName(device: BluetoothDevice): String =
        try { device.name ?: device.address } catch (_: SecurityException) { device.address }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notification_channel),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    companion object {
        private const val CHANNEL_ID = "myscooty_ble"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val PREFS = "myscooty"
        private const val KEY_LAST_ADDRESS = "last_address"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"

        fun start(context: Context, address: String? = null) {
            val intent = Intent(context, BleForegroundService::class.java).apply {
                if (!address.isNullOrBlank()) putExtra(EXTRA_DEVICE_ADDRESS, address)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleForegroundService::class.java))
        }

        fun saveAddress(context: Context, address: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_ADDRESS, address).apply()
        }

        fun setAutoReconnect(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
        }

        fun autoReconnectEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_RECONNECT, true)
    }
}
