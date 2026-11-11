package com.orbit.browser.media

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Injected into every content WebView as `window.OrbitMediaSink`
 * (Module 3 port). The document-start MediaWatch script calls these when
 * page media starts/pauses — the ONLY channel between page media and the
 * foreground service.
 *
 * Original app me tabs multiple hain, isliye sink apna WebView carry karta
 * hai — coordinator ko turant pata chal jata hai kaunsa tab play kar raha
 * hai, aur lock-screen controls wahi tab control karte hain.
 *
 * Surface is intentionally tiny and string-typed: no objects, no callbacks,
 * nothing a hostile page could poke for fun.
 */
class PlaybackSink(private val webView: WebView) {

    @JavascriptInterface
    fun onPlaybackStarted(title: String?, url: String?) {
        PlaybackCoordinator.onPlaybackStarted(webView, title.orEmpty(), url.orEmpty())
    }

    @JavascriptInterface
    fun onPlaybackPaused() {
        PlaybackCoordinator.onPlaybackPaused()
    }
}
