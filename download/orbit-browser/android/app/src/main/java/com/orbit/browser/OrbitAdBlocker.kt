package com.orbit.browser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object OrbitAdBlocker {
    private val BLOCKED_DOMAINS = hashSetOf(
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "adservice.google.com",
        "facebook.net",
        "scorecardresearch.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com"
    )

    fun isAdOrTracker(url: String): Boolean {
        for (domain in BLOCKED_DOMAINS) {
            if (url.contains(domain, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
    }
}
