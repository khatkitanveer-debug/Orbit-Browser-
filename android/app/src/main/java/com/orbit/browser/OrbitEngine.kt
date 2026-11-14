package com.orbit.browser

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * v1.9.4 UC-BOOST MODULE — OrbitEngine (Chromium Speed Wrapper).
 *
 * UC Browser jitna "instant" feel dene ka engine-level wrapper. Har WebView
 * yahan se guzarta hai — cache strategy, GPU raster policy, network hints.
 *
 * REAL optimizations (sab measurable):
 *   1. CACHE        — LOAD_DEFAULT (HTTP cache + BFCache full use; UC ka
 *                     "offline jaisa" feel isi se aata hai — pehli visit ke
 *                     baad static assets disk se serve hote hai)
 *   2. PRECONNECT   — document-start par current domain + top domains ke
 *                     DNS+TLS handshakes pehle se shuru (0-RTT feel)
 *   3. TOUCH-PREFETCH — user link par ungli rakhte hi (touchstart) link ka
 *                     HTML no-cors fetch — tap karte hi cache se INSTANT
 *   4. RENDERER     — RENDERER_PRIORITY_IMPORTANT + whenVisible=false
 *                     (background tab bhi render continue — switching smooth)
 *   5. POOL         — prewarmed WebView pool: naya tab ZERO lag (renderer
 *                     process pehle se garam hota hai)
 *
 * Cloud Boost pref (off/wifi/on) iske sab network features ka master switch hai.
 */
object OrbitEngine {

    // ---------------------------------------------------------------- cache

    /** Cache mode — incognito tab ke alawa hamesha LOAD_DEFAULT (UC/Chrome parity). */
    fun cacheMode(incognito: Boolean): Int =
        if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

    // ---------------------------------------------------------------- cloud boost pref

    /** cloudBoost pref: "off" | "wifi" | "on". Default ON (UC parity). */
    fun boostMode(ctx: Context): String =
        LocalStore.getSetting(ctx, "cloudBoost", "on")

    /** Abhi network hints lagane allowed hai? (wifi-only mode respect karta hai) */
    fun boostActiveNow(ctx: Context): Boolean {
        return when (boostMode(ctx)) {
            "off" -> false
            "wifi" -> isUnmeteredNow(ctx)
            else -> true
        }
    }

    /** Unmetered (Wi-Fi) network abhi? — prerender/boost gating dono use karte hai. */
    fun isUnmeteredNow(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        if (Build.VERSION.SDK_INT >= 23) {
            val net = cm.activeNetwork ?: return true
            val cap = cm.getNetworkCapabilities(net) ?: return true
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } else {
            !cm.isActiveNetworkMetered
        }
    }.getOrDefault(true)

    // ---------------------------------------------------------------- renderer policy

    /** Background me bhi render — tab switch / split multitask me smoothness. */
    fun applyRendererPolicy(wv: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
    }

    // ---------------------------------------------------------------- document-start network hints

    /**
     * PRECONNECT + TOUCH-PREFETCH bootstrap (document-start).
     *
     * - Current hostname + hardcoded top-30 domains: <link rel=preconnect> +
     *   <link rel=dns-prefetch> — TLS/DNS handshake user ke aane se PEHLE.
     * - Link par touchstart/pointerdown aate hi high-priority no-cors fetch —
     *   page HTTP cache me aa jata hai, tap par INSTANT.
     * - Sirf http(s) links, same-origin cross-page bhi; top-5 links cap
     *   (network bikharna mana hai — UC bhi yahi karta hai).
     */
    val NETWORK_HINTS_SCRIPT: String = """
        (function () {
          try {
            if (window.__orbitHints) return;
            window.__orbitHints = 1;
            var hint = function (dom) {
              try {
                if (location.protocol !== 'https:' && location.protocol !== 'http:') return;
                var mk = function (rel, dom2) {
                  var l = document.createElement('link');
                  l.rel = rel; l.href = 'https://' + dom2 + '/';
                  (document.head || document.documentElement).appendChild(l);
                };
                mk('preconnect', dom);
                mk('dns-prefetch', dom);
              } catch (e) {}
            };
            var here = location.hostname;
            if (here) hint(here);
            var top = ['www.google.com','youtube.com','m.youtube.com','www.facebook.com',
              'www.instagram.com','web.whatsapp.com','x.com','www.amazon.in','www.flipkart.com',
              'i.ytimg.com','yt3.ggpht.com','fonts.googleapis.com','fonts.gstatic.com',
              'cdnjs.cloudflare.com','cdn.jsdelivr.net','www.wikipedia.org','duckduckgo.com',
              'saavn.dev','www.jiosaavn.com','c.saavncdn.com','t.co','pbs.twimg.com',
              'assets.redditmedia.com','external-preview.redd.it','graph.instagram.com',
              'static.xx.fbcdn.net','scontent.cdninstagram.com','media.licdn.com','m.imdb.com',
              'image-res.clic.fm'];
            for (var i = 0; i < top.length; i++) hint(top[i]);

            // TOUCH-PREFETCH — approach phase me HTML warm kar do
            var done = {};
            var n = 0;
            var warm = function (href) {
              try {
                if (!href || href.indexOf('http') !== 0) return;
                if (href.indexOf('#') === href.length - 1) return;
                if (done[href]) return;
                if (n >= 5) return;
                done[href] = 1; n++;
                fetch(href, { mode: 'no-cors', priority: 'high', credentials: 'include' })
                  .catch(function () {});
              } catch (e) {}
            };
            var pick = function (el) {
              try {
                var a = el.closest ? el.closest('a[href]') : null;
                if (a) warm(a.href);
              } catch (e) {}
            };
            document.addEventListener('touchstart', function (ev) { pick(ev.target); },
              { passive: true, capture: true });
            document.addEventListener('mousedown', function (ev) { pick(ev.target); },
              { passive: true, capture: true });
          } catch (e) {}
        })();
    """.trimIndent()

    // ---------------------------------------------------------------- speed mode

    /**
     * SPEED MODE (tools sheet) — text-first lite browsing: images off +
     * video autoplay off. Page text turant dikhna shuru ho jata hai (2G/3G
     * par UC Speed Mode jaisa feel). Image Quality pref ke saath layer hai.
     */
    fun speedModeOn(ctx: Context): Boolean =
        LocalStore.getSetting(ctx, "speedMode", "off") == "on"

    fun imageQuality(ctx: Context): String =
        LocalStore.getSetting(ctx, "imageQuality", "high") // none | low | medium | high

    /** Is WebView me images load honi chahiye? (speed mode + quality dono ka net result) */
    fun imagesAllowed(ctx: Context, incognito: Boolean): Boolean {
        if (incognito) return true
        if (speedModeOn(ctx)) return false
        return imageQuality(ctx) != "none"
    }
}
