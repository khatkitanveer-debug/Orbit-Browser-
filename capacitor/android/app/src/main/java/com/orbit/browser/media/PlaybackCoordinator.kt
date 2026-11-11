package com.orbit.browser.media

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.orbit.browser.render.MediaWatchBootstrap
import java.lang.ref.WeakReference

/**
 * Thread-safe switchboard of Module 3.
 *
 *   page JS (sink thread)  →  [here]  →  main thread  →  PlaybackService
 *   PlaybackService (media buttons)  →  [here]  →  main thread  →  page JS
 *
 * Holds the content WebView as a WeakReference (never owns it — ContentHost
 * does) and the live service instance. Every WebView mutation is posted to
 * the main looper; every entry point is safe to call from any thread.
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

    /** Media started playing (page thread) → start/refresh the FGS. */
    fun onPlaybackStarted(title: String, url: String) {
        main.post {
            val webView = webViewRef?.get() ?: return@post
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

    /** Content WebView destroyed (home tapped, renderer gone) — hard stop. */
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
            webView.evaluateJavascript(MediaWatchBootstrap.controlScript(play), null)
        }
    }
}
