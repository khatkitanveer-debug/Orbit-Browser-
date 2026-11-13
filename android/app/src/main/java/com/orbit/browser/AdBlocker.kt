package com.orbit.browser

/**
 * BRAVE MODULE (v1.2.0) — Orbit Shields.
 *
 * Network-level ad/tracker blocker: [shouldBlock] WebView ke request ko
 * server tak jaane se PEHLE drop karta hai (shouldInterceptRequest se).
 *
 * Curated top ad/tracker domains (EasyList-derived core hosts) — sirf pure
 * ad/tracker endpoints, taaki koi legit site na toote. Suffix-match hota hai:
 * "ads.example.doubleclick.net" bhi "doubleclick.net" se block ho jata hai.
 *
 * Privacy: list device me embedded hai — koi list download nahi, koi phone-home.
 */
object AdBlocker {

    private val DOMAINS = setOf(
        // Google ads
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "2mdn.net", "googlecommerce.com",
        // Big ad exchanges / SSPs
        "adnxs.com", "adsrvr.org", "pubmatic.com", "rubiconproject.com",
        "casalemedia.com", "openx.net", "sharethrough.com", "smartadserver.com",
        "bidswitch.net", "spotxchange.com", "teads.tv", "yieldmo.com",
        "yieldlab.net", "improvedigital.com", "indexww.com", "sonobi.com",
        "doubleverify.com", "adsafeprotected.com", "moatads.com", "moatpixel.com",
        // Retargeting / affiliate
        "criteo.com", "criteo.net", "tagular.com", "adroll.com", "outbrain.com",
        "taboola.com", "zergnet.com", "revcontent.com", "mgid.com", "media.net",
        "popads.net", "popcash.net", "propellerads.com", "zedo.com", "adskeeper.com",
        // Analytics / tracking
        "google-analytics.com", "scorecardresearch.com", "quantserve.com",
        "imrworldwide.com", "nr-data.net", "hotjar.com", "fullstory.com",
        "mixpanel.com", "amplitude.com", "appsflyer.com", "adjust.com",
        "kochava.com", "branch.io", "crashlytics.com",
        // Social ad pixels (login endpoints NOT blocked — FB/Google login safe)
        "ads-twitter.com", "static.ads-twitter.com", "analytics.twitter.com",
        "ads.linkedin.com", "px.ads.linkedin.com", "ads.pinterest.com",
        // Amazon / misc
        "amazon-adsystem.com", "adsystem.com", "bidr.io", "agkn.com",
        "crwdcntrl.net", "exelator.com", "mathtag.com", "tapad.com",
        "eyeota.net", "bluekai.com", "demdex.net", "omtrdc.net", "everesttech.net",
        "turn.com", "rlcdn.com", "id5-sync.com", "adsco.re", "unityads.unity3d.com",
        "applovin.com", "chartboost.com", "inmobi.com", "mopub.com", "vungle.com",
    )

    private const val EMPTY_BYTES = 0

    // HOT-PATCH channel (RemoteConfig) — ota/hotfix.json se aaye live domains.
    // active set rebuild sirf yahan hota hai — shouldBlock hot path par koi
    // allocation nahi (har WebView request isse guzarta hai).
    @Volatile
    private var activeDomains: Set<String> = DOMAINS

    /** Live shields update — dev ota/hotfix.json push kare, APK ke bina. */
    fun setExtraDomains(domains: List<String>) {
        val extra = domains.filter { it.isNotBlank() }.toSet()
        activeDomains = if (extra.isEmpty()) DOMAINS else DOMAINS + extra
    }

    // v1.7.0 — PATH-LEVEL ad endpoints (host match kaafi nahi hai — YouTube ke ad
    // beacons youtube.com/googlevideo.com ke PATHS par chalte hai). Sirf YE known
    // ad paths block hote hai, main video/content paths kabhi match nahi hote.
    private val PATH_PATTERNS = listOf(
        "/api/stats/ads",      // YouTube ad view beacons
        "/pagead/",            // Google display ads
        "/ptracking",          // YouTube ad/tracking redirect
        "adformat=",           // ad stream variant param (main video me kabhi nahi)
        "/get_ads",            // pre-roll ad fetch
        "youtube.com/api/stats/qoe?el=ad", // ad QoE
    )

    /** host suffix match — "ads.x.doubleclick.net" bhi "doubleclick.net" se match.
     *  Sirf dot-boundary match (naive endsWith "notdoubleclick.net" ko galat block karta) */
    fun shouldBlock(url: String): Boolean {
        for (p in PATH_PATTERNS) if (url.contains(p)) return true
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        if (host.isEmpty()) return false
        for (d in activeDomains) {
            if (host == d || host.endsWith(".$d")) return true
        }
        return false
    }

    /** session-wide total (dialog me dikhane ke liye) */
    @Volatile
    var totalBlocked: Int = 0
        private set

    fun countBlock() {
        totalBlocked++
    }

    fun resetStats() {
        totalBlocked = 0
    }
}
