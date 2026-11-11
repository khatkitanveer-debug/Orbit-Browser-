package com.orbit.browser.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Module 4 — VpnService scaffolding.
 *
 * STRUCTURE IS COMPLETE: consent flow (VpnService.prepare via
 * OrbitPrivacyPlugin), tun lifecycle, revoke handling, stop semantics.
 *
 * INTENTIONALLY INERT: `BACKEND_READY` is false, so no tun is ever
 * established. Establishing an empty tun would black-hole the device's
 * traffic — that is a worse failure than not starting. The routing
 * backend (Tor packet forwarding / kill-switch DNS policy) lands with the
 * next module; when it does, only `BACKEND_READY` and `forwardLoop()`
 * change — the consent/lifecycle contract stays identical.
 */
class OrbitVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!BACKEND_READY) {
            // Safe refusal — never take the device's traffic hostage.
            stopSelf()
            return START_NOT_STICKY
        }

        val fd = establishTunnel() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        tun = fd
        forwardLoop(fd)
        return START_STICKY
    }

    private fun establishTunnel(): ParcelFileDescriptor? = runCatching {
        Builder()
            .setSession("Orbit VPN")
            .addAddress("10.111.0.2", 32)
            .addDnsServer("10.111.0.1")
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setMtu(1500)
            .establish()
    }.getOrNull()

    /**
     * Reads packets from the tun and forwards them through the privacy
     * backend. Boilerplate — replaced by the real routing module.
     */
    private fun forwardLoop(fd: ParcelFileDescriptor) {
        // val input = FileInputStream(fd.fileDescriptor)
        // val output = FileOutputStream(fd.fileDescriptor)
        // ... backend packet pump ...
    }

    /** User revoked the VPN from system settings (or another VPN started). */
    override fun onRevoke() {
        teardown()
        stopSelf()
    }

    private fun teardown() {
        runCatching { tun?.close() }
        tun = null
    }

    companion object {
        const val ACTION_STOP = "com.orbit.browser.vpn.STOP"

        /** Flips true only when a real routing backend is wired in. */
        @Volatile
        var BACKEND_READY: Boolean = false
    }
}
