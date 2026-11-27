package com.bygrind.meshchat

import android.os.Bundle
import android.view.WindowManager
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(MeshPlugin::class.java)
        super.onCreate(savedInstanceState)
        // Block screenshots and screen recording permanently
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
