package com.orbit.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v1.6.0 — VPN notification ke ACTION buttons ka receiver (user request: country
 * change ke liye ab Settings me jaana NAHI padta — notification se hi ho jata hai).
 *
 * Notification par do buttons (sirf connected state me dikhte hain):
 *   • "Change country" → agla verified relay jo current se ALAG country ka ho
 *     (RelayEngine.cycleCountry)
 *   • "New IP" → turant identity rotate (RelayEngine.rotateNow)
 *
 * Receiver exported=false — sirf Orbit khud apne PendingIntents se trigger karta hai.
 */
class VpnNotifReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CYCLE -> RelayEngine.cycleCountry()
            ACTION_ROTATE -> RelayEngine.rotateNow()
        }
        // notification turant refresh (naya country/status text)
        SpiderMode.sync(context)
    }

    companion object {
        const val ACTION_CYCLE = "com.orbit.browser.VPN_CYCLE_COUNTRY"
        const val ACTION_ROTATE = "com.orbit.browser.VPN_ROTATE_IP"
    }
}
