package com.bygrind.meshchat

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

@CapacitorPlugin(name = "MeshPlugin")
class MeshPlugin : Plugin() {

    private var meshService: MeshService? = null

    override fun load() {
        // Start mesh foreground service
        val intent = Intent(context, MeshService::class.java)
        context.startForegroundService(intent)
        // Bind to service for communication
    }

    @PluginMethod
    fun broadcast(call: PluginCall) {
        val payload = call.getString("payload") ?: return call.reject("No payload")
        val band = call.getInt("band") ?: 0
        meshService?.broadcast(payload, band)
        call.resolve()
    }

    @PluginMethod
    fun shareApk(call: PluginCall) {
        try {
            val apkPath = context.packageManager
                .getApplicationInfo(context.packageName, 0).sourceDir
            val apkFile = File(apkPath)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartir mesh chat APK")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Error sharing APK: ${e.message}")
        }
    }

    // Called by MeshService when a message arrives
    fun onMeshMessage(payload: String, band: Int) {
        val data = JSObject().apply {
            put("payload", payload)
            put("band", band)
        }
        notifyListeners("meshMessage", data)
    }
}
