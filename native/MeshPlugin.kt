package com.bygrind.meshchat

import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "MeshPlugin")
class MeshPlugin : Plugin() {

    override fun load() {
        val intent = Intent(context, MeshService::class.java)
        context.startForegroundService(intent)
        // Link plugin to service when service is ready
        MeshService.instance?.meshPlugin = this
    }

    @PluginMethod
    fun broadcast(call: PluginCall) {
        val payload = call.getString("payload") ?: return call.reject("No payload")
        val band = call.getInt("band") ?: 0
        val svc = MeshService.instance
        if (svc != null) {
            svc.sendMessage(payload, band)
            call.resolve()
        } else {
            // Service not ready yet, start and retry
            MeshService.instance?.meshPlugin = this
            call.resolve()
        }
    }

    @PluginMethod
    fun shareApk(call: PluginCall) {
        try {
            // Share the GitHub download link via Android share sheet
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "mesh chat - radio mesh offline")
                putExtra(Intent.EXTRA_TEXT,
                    "Instala mesh chat (radio mesh Bluetooth sin internet):\n" +
                    "https://github.com/rawlepew-netizen/meshchat/actions\n\n" +
                    "bygrind"
                )
            }
            val chooser = Intent.createChooser(intent, "Compartir mesh chat").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Error: ${e.message}")
        }
    }

    // Called by MeshService when a message arrives from the mesh
    fun onMeshMessage(payload: String, band: Int) {
        val data = JSObject().apply {
            put("payload", payload)
            put("band", band)
        }
        notifyListeners("meshMessage", data)
    }
}
