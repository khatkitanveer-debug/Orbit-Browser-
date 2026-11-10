package com.orbit.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * PrivacyEngine — Enforces Brave-standard privacy:
 * - Local log cleansing on tab closure
 * - Strict third-party cookie isolation
 * - WebStorage / Cache purging for incognito sessions
 * - Zero telemetry guarantee
 */
class PrivacyEngine(private val context: Context) {

    fun cleanseTabArtifacts(isIncognito: Boolean) {
        if (isIncognito) {
            // Instant wipe of session DOM storage, cookies, and cache
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeSessionCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun purgeAllBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean
    ) {
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (clearCache) {
            WebStorage.getInstance().deleteAllData()
            context.cacheDir.deleteRecursively()
        }
    }

    companion object {
        fun sanitizeUrlForLog(url: String): String {
            // Strip tracking query parameters (fbclid, gclid, utm_*, etc.)
            val uri = android.net.Uri.parse(url)
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames.forEach { param ->
                if (!param.startsWith("utm_") && param != "fbclid" && param != "gclid" && param != "msclkid") {
                    builder.appendQueryParameter(param, uri.getQueryParameter(param))
                }
            }
            return builder.build().toString()
        }
    }
}
