package com.orbit.browser

import android.os.Bundle
import androidx.core.view.WindowCompat
import com.getcapacitor.BridgeActivity
import com.orbit.browser.plugins.OrbitLensPlugin
import com.orbit.browser.plugins.OrbitPrivacyPlugin
import com.orbit.browser.plugins.OrbitRenderPlugin
import com.orbit.browser.plugins.OrbitSyncPlugin

class MainActivity : BridgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Local plugins must be registered BEFORE the bridge initializes.
        registerPlugin(OrbitRenderPlugin::class.java)
        registerPlugin(OrbitLensPlugin::class.java)
        registerPlugin(OrbitSyncPlugin::class.java)
        registerPlugin(OrbitPrivacyPlugin::class.java)
        super.onCreate(savedInstanceState)
        // Edge-to-edge: SDK 35 enforces this; explicit call keeps parity on older APIs.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
