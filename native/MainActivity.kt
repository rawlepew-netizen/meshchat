package com.bygrind.meshchat

import android.os.Bundle
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(MeshPlugin::class.java)
        super.onCreate(savedInstanceState)
        // Bloquear capturas de pantalla
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Dar permiso de micrófono y cámara al WebView
        bridge.webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread { request?.grant(request.resources) }
            }
        }
    }
}
