package com.orbit.browser.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * One place for Chrome-Android WebView parity.
 * Every content WebView MUST pass through here — no site-specific tuning
 * anywhere else in the codebase. Module 1 guarantee: every website fits the
 * screen exactly like Google Chrome.
 */
@SuppressLint("SetJavaScriptEnabled")
object WebViewDefaults {

    fun apply(context: Context, webView: WebView) {
        val s: WebSettings = webView.settings

        // — Viewport / layout (Chrome parity core) —
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        s.textZoom = textZoom(context)

        // — Zoom: pinch works, on-screen +/- buttons never show (Chrome behavior) —
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false

        // — Media: gesture-less playback ready now; Module 3 attaches the
        //    foreground service + MediaSession on top of this —
        s.mediaPlaybackRequiresUserGesture = false

        // — Compatibility —
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadsImagesAutomatically = true
        s.cacheMode = WebSettings.LOAD_DEFAULT

        // — Safety rails —
        s.allowFileAccess = false
        s.allowContentAccess = true
        // Tab module owns target=_blank later; single window keeps Module 1 predictable.
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.safeBrowsingEnabled = true

        // — Cookies: third-party ON so Google sign-in and OAuth flows behave
        //    exactly like Chrome —
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.setBackgroundColor(Color.WHITE)
        webView.isFocusableInTouchMode = true
    }

    /** Respect the user's system font scale exactly like Chrome does (a11y parity). */
    private fun textZoom(context: Context): Int {
        val scale = context.resources.configuration.fontScale
        return (scale * 100f).toInt().coerceIn(50, 250)
    }
}
