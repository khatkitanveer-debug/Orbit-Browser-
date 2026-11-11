package com.orbit.browser.media

import android.webkit.JavascriptInterface

/**
 * Injected into every content WebView as `window.OrbitMediaSink`
 * (Module 3). The document-start MediaWatch script calls these when page
 * media starts/pauses — the ONLY channel between page media and the
 * foreground service.
 *
 * Surface is intentionally tiny and string-typed: no objects, no callbacks,
 * nothing a hostile page could poke for fun.
 */
class PlaybackSink {

    @JavascriptInterface
    fun onPlaybackStarted(title: String?, url: String?) {
        PlaybackCoordinator.onPlaybackStarted(title.orEmpty(), url.orEmpty())
    }

    @JavascriptInterface
    fun onPlaybackPaused() {
        PlaybackCoordinator.onPlaybackPaused()
    }
}
