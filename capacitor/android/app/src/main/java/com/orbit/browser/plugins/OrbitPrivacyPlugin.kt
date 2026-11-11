package com.orbit.browser.plugins

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.orbit.browser.vpn.OrbitVpnService

/**
 * OrbitPrivacy — Module 4 native bridge (VPN toggle).
 *
 * vpnStart(): runs the mandatory system consent dialog the first time,
 * then starts OrbitVpnService. The service is deliberately inert until a
 * routing backend exists — this method reports that honestly
 * (supported: false) instead of pretending.
 */
@CapacitorPlugin(name = "OrbitPrivacy")
class OrbitPrivacyPlugin : Plugin() {

    @PluginMethod
    fun vpnStart(call: PluginCall) {
        val consentIntent: Intent? = VpnService.prepare(bridge.activity)
        if (consentIntent != null) {
            startActivityForResult(call, consentIntent, "onVpnConsent")
            return
        }
        // Consent already granted in the past — go straight to start.
        launchService(call)
    }

    @ActivityCallback
    private fun onVpnConsent(call: PluginCall?, result: androidx.activity.result.ActivityResult) {
        if (call == null) return
        if (result.resultCode != Activity.RESULT_OK) {
            call.resolve(JSObject().put("started", false).put("reason", "consent-denied"))
            return
        }
        launchService(call)
    }

    private fun launchService(call: PluginCall) {
        if (!OrbitVpnService.BACKEND_READY) {
            call.resolve(
                JSObject()
                    .put("started", false)
                    .put("supported", false)
                    .put("reason", "backend-not-configured"),
            )
            return
        }
        bridge.context.startService(Intent(bridge.context, OrbitVpnService::class.java))
        call.resolve(JSObject().put("started", true).put("supported", true))
    }

    @PluginMethod
    fun vpnStop(call: PluginCall) {
        bridge.context.startService(
            Intent(bridge.context, OrbitVpnService::class.java).setAction(OrbitVpnService.ACTION_STOP),
        )
        call.resolve()
    }
}
