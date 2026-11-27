package com.bygrind.meshchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import java.util.UUID

class MeshService : Service() {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("A5E3F001-1234-4B7A-9C2D-8F6E0A3B5C7D")
        val CHAR_RX_UUID: UUID = UUID.fromString("A5E3F002-1234-4B7A-9C2D-8F6E0A3B5C7D")
        val CHANNEL_ID = "mesh_channel"
        val NOTIF_ID = 1
    }

    private var bleAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private val seenMessages = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        startForeground()
        initBLE()
    }

    private fun startForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Mesh Chat", NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("mesh chat")
            .setContentText("Red mesh activa")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    private fun initBLE() {
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = btManager.adapter
        startScan()
        startAdvertise()
    }

    private fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        bleScanner = bleAdapter?.bluetoothLeScanner
        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            // Connect to discovered mesh peer
        }
    }

    private fun startAdvertise() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        bleAdvertiser = bleAdapter?.bluetoothLeAdvertiser
        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    fun broadcast(payload: String, band: Int) {
        // Send encrypted payload to all connected BLE peers
        // Mesh relay: forward to all connected devices
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bleScanner?.stopScan(scanCallback)
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        super.onDestroy()
    }
}
