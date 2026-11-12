package com.orbit.browser.render

import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * MODULE 3 v1.3.3 — ASLI lock-screen fix (pipeline level).
 *
 * Pehle 2 release me jo laga wo SAB page-JS level par tha (document.hidden
 * spoof, visibilitychange swallow, audio-focus war) — par Chromium apni
 * window visibility android VIEW SYSTEM se leta hai, JS se NAHI. Screen
 * lock / app background hote hi WebView ko real WINDOW_GONE milti hai aur
 * Chromium apne MEDIA PIPELINE me video suspend + renderer throttle kar
 * deta hai — JS kuch bhi kare. Isliye YouTube ruk ruk ke chalta tha aur
 * lock karte hi pause ho jata tha.
 *
 * Ye subclass Chromium ko hamesha VISIBLE batata hai — renderer hidden
 * kabhi nahi hota, media pipeline suspend nahi hota. Yehi trick Chrome
 * tabs, JioSphere aur har "background playback" browser use karta hai.
 * (Foreground service + partial wake lock PlaybackService me pehle se
 * hain — process zinda + CPU awake; ab renderer bhi hidden nahi hoga.)
 */
class BackgroundWebView(context: Context) : WebView(context) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Real visibility GONE/INVISIBLE aaye to bhi VISIBLE report karo —
        // warna Chromium khud video pause kar deta hai (pipeline-level).
        // RESUME par real VISIBLE hi aata hai, koi asar nahi.
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}
