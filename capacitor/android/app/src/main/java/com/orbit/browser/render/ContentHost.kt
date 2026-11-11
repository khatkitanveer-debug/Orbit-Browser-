package com.orbit.browser.render

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat

/**
 * Owns the single native content slot of Module 1.
 *
 *  • Sits inside android.R.id.content, on top of the bridge (React chrome) view.
 *  • topMargin    = app bar bottom, measured in the DOM and sent by JS (physical px)
 *  • bottomMargin = system-bar inset, applied natively so page controls never
 *                   hide under gesture navigation
 *
 * Deliberately single-slot and tiny: the tab pool / thumbnails / sleeping tabs
 * arrive with the tab module and will reuse this contract.
 */
class ContentHost(
    private val activity: Activity,
    hostRoot: ViewGroup,
    private val listener: Listener,
) {
    interface Listener {
        fun onPageState(state: PageSnapshot)
    }

    data class PageSnapshot(
        val url: String,
        val title: String,
        val progress: Int,
        val loading: Boolean,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val crashed: Boolean = false,
    )

    private val webView = WebView(activity)
    private var lastProgress = 0

    init {
        WebViewDefaults.apply(activity, webView)

        // Viewport bootstrap runs BEFORE any page script/layout — no flicker.
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            ViewportBootstrap.SCRIPT,
            ViewportBootstrap.ALLOWED_ORIGIN_RULES,
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri: Uri = request.url
                val scheme = uri.scheme?.lowercase()
                // Web content stays inside Orbit; everything else goes to the system.
                if (scheme == "http" || scheme == "https" || scheme == "about" ||
                    scheme == "javascript" || scheme == "data" || scheme == "blob"
                ) {
                    return false
                }
                return try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (e: Exception) {
                    true // no handler on device — same as Chrome's unsupported-link path
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                emit(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                emit(view)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
                listener.onPageState(
                    PageSnapshot(
                        url = view.url ?: "",
                        title = "",
                        progress = 0,
                        loading = false,
                        canGoBack = false,
                        canGoForward = false,
                        crashed = true,
                    ),
                )
                destroy()
                return true // renderer died — never take the whole app down
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                emit(view, newProgress)
            }
        }

        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        // Gesture-nav bottom inset → bottom margin, applied natively and live.
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val lp = v.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && lp.bottomMargin != bottom) {
                lp.bottomMargin = bottom
                v.layoutParams = lp
            }
            insets
        }

        hostRoot.addView(webView)
    }

    /** Top inset in PHYSICAL px — equals the app bar bottom measured in the DOM. */
    fun setTopInset(physicalPx: Int) {
        val lp = webView.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.topMargin != physicalPx) {
            lp.topMargin = physicalPx
            webView.layoutParams = lp // layout pass only on real change
        }
    }

    fun load(url: String) {
        lastProgress = 0
        webView.loadUrl(url)
        emit(webView, 0)
    }

    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    fun goForward(): Boolean {
        if (!webView.canGoForward()) return false
        webView.goForward()
        return true
    }

    fun reload() {
        webView.reload()
    }

    fun snapshot(): PageSnapshot = PageSnapshot(
        url = webView.url ?: "",
        title = webView.title ?: "",
        progress = lastProgress,
        loading = lastProgress in 0..99,
        canGoBack = webView.canGoBack(),
        canGoForward = webView.canGoForward(),
    )

    private fun emit(view: WebView?, progress: Int? = null) {
        if (view == null || view !== webView) return
        if (progress != null) lastProgress = progress
        listener.onPageState(snapshot())
    }

    fun destroy() {
        ViewCompat.setOnApplyWindowInsetsListener(webView, null)
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
