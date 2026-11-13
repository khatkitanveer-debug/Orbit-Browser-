package com.orbit.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * v1.4.7 SPIDER MODE — Tor-style VPN identity surface.
 *
 * User ne manga tha: VPN dabane par notification me dikhe ki kaunse country ka
 * exit relay chal raha hai, aur IP Tor ki tarah badalta rahe. Rotation RelayEngine
 * me hai (5 min ticker); yahan sirf notification + REAL exit-country lookup.
 *
 * Country: relay ka apna "country" field geonode se aata hai (plain lists me
 * nahi hota), isliye SACHME exit country ip-api.com se lookup hota hai — request
 * relay ke THROUGH jati hai (java.net.Proxy), isliye ip-api ko relay ka exit IP
 * dikhta hai, user ka nahi. Fail hone par relay country label fallback.
 *
 * Notification ONGOING hai (swipe nahi hoti), IMPORTANCE_LOW (silent, no heads-up).
 * POST_NOTIFICATIONS runtime permission Android 13+ par MainActivity mangta hai
 * (VPN toggle par) — deny ho to VPN chalta rahega, sirf notification nahi dikhegi.
 */
object SpiderMode {

    const val ROTATE_MINUTES = 5
    private const val CHANNEL_ID = "orbit_vpn"
    private const val NOTIF_ID = 4200

    /** Bridge + UI read karte hai — last resolved exit-country label. */
    @Volatile var lastCountry: String = ""

    /** Sirf relay CHANGE hone par re-lookup (ip-api rate-limit 45 req/min). */
    @Volatile private var lastLookupRelay: String = ""

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    fun hasNotifPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

    /** RelayEngine.onUiChanged se — state ke hisaab se notification on/off/update.
     *  v1.4.8 FIX: "connecting" phase me bhi notification dikhti hai (pehle sirf
     *  "on" par — connect hone tak 15-45s kuch nahi dikhta tha, user ne "VPN
     *  notification nahi aa raha" report kiya). Ab VPN dabte hi turant notif. */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        val st = RelayEngine.state
        if (st != "on" && st != "connecting") {
            cancel(app)
            return
        }
        show(app)
        val r = RelayEngine.activeRelay ?: return
        if (st != "on") return   // connecting — country lookup baad me, relay active hone par
        val key = r.host + ":" + r.port
        if (lastLookupRelay != key) {
            lastLookupRelay = key
            Thread {
                val looked = lookupExitCountry(r)
                val label = looked
                    ?: r.country.takeIf { it.isNotBlank() && it != "Relay" }
                    ?: ""
                if (label.isNotBlank()) lastCountry = label
                if (RelayEngine.state == "on" && RelayEngine.activeRelay?.host == r.host) show(app)
            }.also { it.isDaemon = true }.start()
        }
    }

    fun cancel(ctx: Context) {
        runCatching { nm(ctx).cancel(NOTIF_ID) }
    }

    private fun show(ctx: Context) {
        if (!hasNotifPermission(ctx)) return
        runCatching { nm(ctx).notify(NOTIF_ID, build(ctx)) }
    }

    private fun nm(ctx: Context): NotificationManager =
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Orbit VPN", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Spider Mode connection status"
            ch.setShowBadge(false)
            nm(ctx).createNotificationChannel(ch)
        }
    }

    private fun build(ctx: Context): Notification {
        ensureChannel(ctx)
        val b: Notification.Builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(ctx, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(ctx)
        }
        val relay = RelayEngine.activeRelay
        val connecting = RelayEngine.state != "on"
        val country = lastCountry.ifBlank {
            relay?.country?.takeIf { it.isNotBlank() && it != "Relay" } ?: "securing"
        }
        val text = if (connecting) "Securing route — finding the fastest relay…"
            else "Exit: $country • IP rotates every $ROTATE_MINUTES min"
        // v1.6.0 — NOTIFICATION SE COUNTRY CHANGE (user request: settings me jaana
        // na pade). Connected state me do action buttons: "Change country" (agla
        // alag-country relay) + "New IP" (turant rotate). Connecting me buttons nahi
        // (kuch cycle karne ko nahi).
        if (!connecting) {
            val cycle = PendingIntent.getBroadcast(
                ctx, 4201,
                Intent(ctx, VpnNotifReceiver::class.java).setAction(VpnNotifReceiver.ACTION_CYCLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val rotate = PendingIntent.getBroadcast(
                ctx, 4202,
                Intent(ctx, VpnNotifReceiver::class.java).setAction(VpnNotifReceiver.ACTION_ROTATE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(R.drawable.ic_vpn, "Change country", cycle)
            b.addAction(R.drawable.ic_refresh, "New IP", rotate)
        }
        return b.setSmallIcon(R.drawable.ic_notif_orbit)
            .setContentTitle("Orbit VPN — Spider Mode")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    /** Relay ke THROUGH ip-api lookup — exit relay ka asli country ("Netherlands (NL)"). */
    private fun lookupExitCountry(relay: RelayEngine.Relay): String? = runCatching {
        val via = http.newBuilder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(relay.host, relay.port)))
            .build()
        via.newCall(
            Request.Builder()
                .url("http://ip-api.com/json/?fields=status,country,countryCode")
                .build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val o = JSONObject(body)
            if (o.optString("status") != "success") return null
            val name = o.optString("country", "")
            val cc = o.optString("countryCode", "").uppercase()
            if (name.isBlank()) null
            else if (cc.isNotBlank() && cc != name.uppercase()) "$name ($cc)" else name
        }
    }.getOrNull()
}
