package com.bygrind.meshchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshService : Service() {

    companion object {
        val SERVICE_UUID = UUID.fromString("A5E3F001-1234-4B7A-9C2D-8F6E0A3B5C7D")
        val CHAR_MSG_UUID = UUID.fromString("A5E3F002-1234-4B7A-9C2D-8F6E0A3B5C7D")
        val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val CHANNEL_ID = "mesh_ch"
        const val NOTIF_ID = 1
        var instance: MeshService? = null
    }

    private var bleManager: BluetoothManager? = null
    private var bleAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()
    private val seenIds = ArrayDeque<String>()
    var meshPlugin: MeshPlugin? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground()
        initBLE()
    }

    private fun startForeground() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mesh", NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )
        startForeground(NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("mesh chat")
                .setContentText("Red activa")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true).setSilent(true).build()
        )
    }

    private fun initBLE() {
        bleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager?.adapter ?: return
        setupGattServer()
        startAdvertising()
        startScanning()
    }

    private fun setupGattServer() {
        gattServer = bleManager?.openGattServer(this, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) subscribedDevices.add(device)
                else subscribedDevices.remove(device)
            }
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int,
                char: BluetoothGattCharacteristic, prep: Boolean,
                responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                processMessage(String(value, Charsets.UTF_8))
            }
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice, requestId: Int,
                descriptor: BluetoothGattDescriptor, prep: Boolean,
                responseNeeded: Boolean, offset: Int, value: ByteArray
            ) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        })

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val char = BluetoothGattCharacteristic(
            CHAR_MSG_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        char.addDescriptor(BluetoothGattDescriptor(
            DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        ))
        service.addCharacteristic(char)
        gattServer?.addService(service)
    }

    private fun processMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val id = json.optString("id", "")
            if (id.isEmpty() || seenIds.contains(id)) return
            seenIds.addLast(id)
            if (seenIds.size > 500) seenIds.removeFirst()

            val band = json.optInt("band", 0)
            val payload = json.optString("payload", "")
            val ttl = json.optInt("ttl", 0)

            // Entregar al WebView vía plugin
            meshPlugin?.onMeshMessage(payload, band)

            // Relay con TTL-1
            if (ttl > 1) {
                json.put("ttl", ttl - 1)
                relay(json.toString())
            }
        } catch (_: Exception) {}
    }

    fun sendMessage(payload: String, band: Int) {
        val wrapper = JSONObject().apply {
            put("id", System.currentTimeMillis().toString(36) + (Math.random() * 1000).toInt())
            put("band", band)
            put("ttl", 5)
            put("payload", payload)
        }.toString()
        relay(wrapper)
    }

    private fun relay(raw: String) {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        // Notify server-side connected devices
        val char = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(CHAR_MSG_UUID)
        char?.value = bytes
        subscribedDevices.forEach { dev ->
            char?.let { gattServer?.notifyCharacteristicChanged(dev, it, false) }
        }
        // Write to client-connected devices
        clientGatts.values.forEach { gatt ->
            gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_MSG_UUID)?.let { c ->
                c.value = bytes
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(c)
            }
        }
    }

    private fun startScanning() {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        bleScanner = bleAdapter?.bluetoothLeScanner
        bleScanner?.startScan(listOf(filter), settings, object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val device = result.device
                if (!clientGatts.containsKey(device.address)) {
                    device.connectGatt(this@MeshService, false, object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                clientGatts[gatt.device.address] = gatt
                                gatt.discoverServices()
                            } else {
                                clientGatts.remove(gatt.device.address)
                                gatt.close()
                            }
                        }
                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            val c = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_MSG_UUID) ?: return
                            gatt.setCharacteristicNotification(c, true)
                            c.getDescriptor(DESCRIPTOR_UUID)?.let { d ->
                                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(d)
                            }
                        }
                        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
                            processMessage(String(c.value, Charsets.UTF_8))
                        }
                    })
                }
            }
        })
    }

    private fun startAdvertising() {
        bleAdvertiser = bleAdapter?.bluetoothLeAdvertiser ?: return
        bleAdvertiser?.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true).build(),
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .setIncludeDeviceName(false).build(),
            object : AdvertiseCallback() {}
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        instance = null
        bleScanner?.stopScan(null)
        bleAdvertiser?.stopAdvertising(null)
        clientGatts.values.forEach { it.disconnect(); it.close() }
        gattServer?.close()
        super.onDestroy()
    }
}
