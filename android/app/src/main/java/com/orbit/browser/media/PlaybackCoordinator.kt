package com.orbit.browser.media

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.orbit.browser.render.MediaWatchBootstrap
import java.lang.ref.WeakReference

/**
 * Module 3 port — multi-tab switchboard.
 *
 * Original app me ek se zyada tab ho sakte hain, isliye playing WebView ka
 * pata sink se aata hai (har WebView ka apna PlaybackSink — jo WebView media
 * start karta hai wahi coordinator me attach hota hai). Lock-screen buttons
 * usi WebView par jaate hain, chahe user doosre tab par baitha ho.
 *
 *   page JS (sink thread)  →  [here]  →  main thread  →  PlaybackService
 *   PlaybackService (media buttons)  →  [here]  →  main thread  →  page JS
 *
 * WebView WeakReference me rakhta hai (own nahi karta). Destroy hua WebView
 * MainActivity webViewDestroyed() se report hota hai — aur bhi destroyed
 * WebView par evaluateJavascript runCatching me safe hai.
 */
object PlaybackCoordinator {

    private val main = Handler(Looper.getMainLooper())
    private var webViewRef: WeakReference<WebView>? = null

    @Volatile
    private var service: PlaybackService? = null

    /* ------------------------- ContentHost lifecycle ------------------------- */

    fun attachWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun detachWebView() {
        webViewRef = null
    }

    /* -------------------------- service lifecycle --------------------------- */

    fun attachService(active: PlaybackService?) {
        service = active
    }

    /* ---------------------------- from page JS ------------------------------ */

    /** Kya ye WebView abhi media chala raha hai? MainActivity isko sleep/
     *  eviction me skip karne ke liye poochta hai — background video safe. */
    fun isMediaOwner(webView: WebView?): Boolean =
        webView != null && service != null && webViewRef?.get() === webView

    /** Media started playing (page thread) → start/refresh the FGS. */
    fun onPlaybackStarted(webView: WebView, title: String, url: String) {
        main.post {
            // jis WebView ne play kiya wahi ab media ka owner hai (multi-tab)
            webViewRef = WeakReference(webView)
            val context: Context = webView.context
            val current = service
            if (current != null) {
                current.updateMedia(title, url)
            } else {
                try {
                    val intent = Intent(context, PlaybackService::class.java)
                        .putExtra(PlaybackService.EXTRA_TITLE, title)
                        .putExtra(PlaybackService.EXTRA_URL, url)
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    // Foreground-start can be blocked if the app is already
                    // backgrounded mid-play — playback simply stays in-page.
                }
            }
        }
    }

    /** Media paused or ended → let the service wind down (grace for resume). */
    fun onPlaybackPaused() {
        main.post { service?.onMediaPaused() }
    }

    /** Main-frame navigation — a different origin means the media is gone. */
    fun onPageNavigatedAway(url: String) {
        main.post { service?.onPageNavigated(url) }
    }

    /** WebView destroyed (tab band / evicted / activity destroy) — agar wahi
     *  playing WebView tha to service hard stop. */
    fun webViewDestroyed(webView: WebView?) {
        if (webView == null) return
        main.post {
            if (webViewRef?.get() === webView) {
                webViewRef = null
                service?.let { it.stopSelf() }
                service = null
            }
        }
    }

    /** Sab kuch band — activity destroy par. */
    fun stopAll() {
        main.post {
            service?.let { it.stopSelf() }
            service = null
        }
    }

    /* --------------------------- from the service --------------------------- */

    /** Play/pause (notification buttons, headset, Android 13+ media card). */
    fun dispatchPlayPause(play: Boolean) {
        main.post {
            val webView = webViewRef?.get() ?: return@post
            runCatching { webView.evaluateJavascript(MediaWatchBootstrap.controlScript(play), null) }
        }
    }
}
