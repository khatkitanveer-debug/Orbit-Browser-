package com.orbit.browser

import android.content.Context
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HOT-PATCH CHANNEL (v1.3.1) — Blinkit/Flipkart-style "chote mote changes".
 *
 * Dev `ota/hotfix.json` push karta hai → app NEXT LAUNCH par bina APK install,
 * bina kisi user action, ye changes turant apply kar leta hai:
 *
 *   • blockDomains  — AdBlocker me live extra domains (naye ad/tracker hosts
 *     aaye to APK release ka intezaar nahi)
 *
 * Trust anchor: hotfix.json WAHI repo serve karta hai jahan se APK aata hai
 * (UpdateChecker jaisa hi trust model). Sanitization strict hai — sirf
 * [a-z0-9._-] domains, max 300, kuch aur field ignore.
 *
 * Flow:
 *   onCreate → loadCached() (sync, ~1ms — pichhla hotfix turant active)
 *            → refreshAsync() (background, 4h throttle — naya mila to cache
 *               + live apply)
 *
 * Fail-safe: network fail / corrupt JSON / bad domain → purana cache zinda
 * rehta hai, koi crash koi behavior change nahi.
 */
object RemoteConfig {

    private const val HOTFIX_URL =
        "https://raw.githubusercontent.com/khatkitanveer-debug/Orbit-Browser-/main/ota/hotfix.json"
    private const val CACHE_FILE = "hotfix.json"
    private const val PREFS = "orbit_remote"
    private const val KEY_LAST_CHECK = "hotfix_last_check"
    private const val CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4h
    private const val MAX_DOMAINS = 300
    private const val MAX_BODY_BYTES = 64 * 1024

    data class Hotfix(
        val version: Int = 0,
        val blockDomains: List<String> = emptyList(),
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /* ------------------------------ cache read ------------------------------ */

    /** Pichhla hotfix (sync, fast) — app start par turant apply karne ke liye. */
    fun loadCached(ctx: Context): Hotfix {
        val f = File(ctx.filesDir, CACHE_FILE)
        if (!f.exists()) return Hotfix()
        return runCatching { parse(f.readText()) }.getOrNull() ?: Hotfix()
    }

    /* ------------------------------- refresh -------------------------------- */

    /** Background refresh (4h throttle) — naya hotfix mila to cache + apply. */
    fun refreshAsync(ctx: Context) {
        val appCtx = ctx.applicationContext
        Thread {
            val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            val now = System.currentTimeMillis()
            if (now - last < CHECK_INTERVAL_MS) return@Thread
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            val body = runCatching {
                val req = okhttp3.Request.Builder().url(HOTFIX_URL).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@Thread
                    val bytes = resp.body?.bytes() ?: return@Thread
                    if (bytes.size > MAX_BODY_BYTES) return@Thread
                    String(bytes, Charsets.UTF_8)
                }
            }.getOrNull() ?: return@Thread

            val hotfix = runCatching { parse(body) }.getOrNull() ?: return@Thread

            // valid hotfix — cache + live apply
            runCatching {
                File(appCtx.filesDir, CACHE_FILE).writeText(body)
            }
            AdBlocker.setExtraDomains(hotfix.blockDomains)
        }.start()
    }

    /* -------------------------------- parse --------------------------------- */

    /** Strict parse — sirf jaane-maane fields, bad data → null (purana cache safe). */
    private fun parse(body: String): Hotfix? {
        val o = org.json.JSONObject(body)
        val domains = mutableListOf<String>()
        val arr = o.optJSONArray("blockDomains") ?: org.json.JSONArray()
        for (i in 0 until arr.length().coerceAtMost(MAX_DOMAINS)) {
            sanitizeDomain(arr.optString(i))?.let { domains.add(it) }
        }
        return Hotfix(
            version = o.optInt("version", 0),
            blockDomains = domains,
        )
    }

    /** Domain whitelist: lowercase [a-z0-9._-], 4..253 chars — kuch aur reject. */
    private fun sanitizeDomain(raw: String?): String? {
        val d = raw?.trim()?.lowercase() ?: return null
        if (d.length < 4 || d.length > 253) return null
        if (!d.matches(Regex("[a-z0-9._\\-]+"))) return null
        if (!d.contains('.')) return null // TLD-only strings reject
        return d
    }
}
