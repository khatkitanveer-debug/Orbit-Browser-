package com.orbit.browser

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.getcapacitor.BridgeActivity
import com.orbit.browser.plugins.OrbitRenderPlugin

class MainActivity : BridgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Local plugins must be registered BEFORE the bridge initializes.
        registerPlugin(OrbitRenderPlugin::class.java)
        super.onCreate(savedInstanceState)
        // Edge-to-edge: SDK 35 enforces this; explicit call keeps parity on older APIs.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
