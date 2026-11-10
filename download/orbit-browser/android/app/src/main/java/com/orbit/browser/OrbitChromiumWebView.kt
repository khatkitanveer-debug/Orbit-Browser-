package com.orbit.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.orbit.browser.service.MediaSessionService

/**
 * OrbitChromiumWebView — Custom Android WebView utilizing Chromium (Blink/V8)
 * with aggressive performance optimizations, Orbit Shield ad-blocking,
 * anti-fingerprinting, and background media playback engine hooks.
 */
class OrbitChromiumWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var isDesktopMode: Boolean = false
        private set
    private var defaultUserAgent: String = ""

    init {
        configureChromiumEngine()
        setupPrivacyAndSecurity()
        setupBackgroundMediaBridge()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureChromiumEngine() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true

            // Aggressive Multi-Threaded Caching & Resource Prioritization
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            
            // Security: Prevent file system access from scripts
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false

            // Strict Mixed Content Blocking
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Media Autoplay & Background Playback configuration
            mediaPlaybackRequiresUserGesture = false

            defaultUserAgent = userAgentString
        }

        // Hardware Acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Dark Theme Support via Chromium WebSettingsCompat
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
        }

        // Custom WebViewClient with network-level request interceptor
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request == null) return null

                val url = request.url.toString()

                // 1. Force HTTPS Everywhere upgrade
                if (url.startsWith("http://") && !url.contains("localhost")) {
                    val httpsUrl = url.replaceFirst("http://", "https://")
                    post { loadUrl(httpsUrl) }
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }

                // 2. Orbit Shield: Network-Level Ad & Tracker Blocking (Brave-Like Standard)
                if (OrbitAdBlocker.isAdOrTracker(url)) {
                    // Return empty response to drop the request with 0 network latency
                    return OrbitAdBlocker.createEmptyResponse()
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Inject anti-fingerprinting & background media playback hooks
                injectBackgroundMediaHooks()
                injectAntiFingerprintingGuards()
            }
        }
    }

    private fun setupPrivacyAndSecurity() {
        // Strict Third-Party Cookie Isolation
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, false)
    }

    private fun setupBackgroundMediaBridge() {
        addJavascriptInterface(object {
            @JavascriptInterface
            fun onMediaPlaying(title: String?, artist: String?, duration: Long) {
                // Dispatch to Android MediaPlaybackService for background lock-screen controls
                MediaPlaybackService.startPlayback(
                    context,
                    title ?: "Orbit Web Playback",
                    artist ?: "Orbit Chromium Browser",
                    duration
                )
            }

            @JavascriptInterface
            fun onMediaPaused() {
                MediaPlaybackService.pausePlayback(context)
            }
        }, "OrbitNativeMediaBridge")
    }

    /**
     * Injects JavaScript into HTML5 media elements and YouTube player
     * to prevent pausing when tabs switch or screen is locked (JioSphere / Brave style).
     */
    fun injectBackgroundMediaHooks() {
        val script = """
            (function() {
                if (window.__orbitMediaHooked) return;
                window.__orbitMediaHooked = true;

                // Hook HTML5 Audio & Video
                function hookMediaElements() {
                    document.querySelectorAll('video, audio').forEach(function(media) {
                        media.addEventListener('play', function() {
                            var title = document.title || 'Web Media';
                            window.OrbitNativeMediaBridge.onMediaPlaying(title, location.hostname, media.duration || 0);
                        });
                        media.addEventListener('pause', function() {
                            if (!document.hidden) {
                                window.OrbitNativeMediaBridge.onMediaPaused();
                            }
                        });
                    });
                }

                // Prevent visibilitychange from pausing media (Brave background playback trick)
                Object.defineProperty(document, 'hidden', { value: false, writable: false });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false });
                window.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);

                hookMediaElements();
                setInterval(hookMediaElements, 2500);
            })();
        """.trimIndent()
        evaluateJavascript(script, null)
    }

    private fun injectAntiFingerprintingGuards() {
        val script = """
            (function() {
                // Brave-style Canvas Anti-Fingerprinting (Subtle noise injection)
                var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
                    var data = origGetImageData.apply(this, arguments);
                    for (var i = 0; i < data.data.length; i += 64) {
                        data.data[i] = data.data[i] ^ 1;
                    }
                    return data;
                };

                // Protect AudioContext Fingerprinting
                if (window.AudioBuffer) {
                    var origGetChannelData = AudioBuffer.prototype.getChannelData;
                    AudioBuffer.prototype.getChannelData = function() {
                        var buffer = origGetChannelData.apply(this, arguments);
                        for (var i = 0; i < buffer.length; i += 100) {
                            buffer[i] += 0.0000001 * (Math.random() - 0.5);
                        }
                        return buffer;
                    };
                }
            })();
        """.trimIndent()
        evaluateJavascript(script, null)
    }

    /**
     * Toggles between Mobile and Desktop User-Agent & viewport layout.
     */
    fun toggleDesktopMode(enable: Boolean) {
        isDesktopMode = enable
        if (enable) {
            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = defaultUserAgent
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        reload()
    }
}
