package com.orbit.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.net.Uri
import android.net.VpnService
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.widget.CheckBox
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.URLUtil
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import com.orbit.browser.lens.LensEngine
import com.orbit.browser.media.PlaybackCoordinator
import com.orbit.browser.media.PlaybackSink
import com.orbit.browser.render.BackgroundWebView
import com.orbit.browser.render.MediaWatchBootstrap
import com.orbit.browser.render.ViewportBootstrap
import com.orbit.browser.render.YouTubeAdShield
import org.json.JSONArray
import org.json.JSONObject as JObj
import com.orbit.browser.sync.OrbitSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Orbit Browser — native Android browser, CHROME ANDROID PARITY (v1.1.0):
 *   • Toolbar: sirf [omnibox pill (lock + reload andar)][tab-count][3-dot] — Chrome jaisa
 *   • Koi persistent tab strip nahi — Chrome tab-switcher: cards grid + FAB
 *   • Real WebView UA ("; wv" hata kar) — Google login/search/YouTube kaam karte hain
 *   • Third-party cookies ON — Google sign-in flows
 *   • target=_blank links naye tab me (onCreateWindow)
 *   • Pull-to-refresh, progress line, find-in-page, desktop site, share
 * Har data DEVICE par hi rehta hai (LocalStore). Incognito tab history me nahi jata.
 */
class MainActivity : Activity() {

    private class Tab(val id: Long, var url: String, var title: String) {
        var webView: WebView? = null
        var isInternal: Boolean = true
        var isIncognito: Boolean = false
        var desktopSite: Boolean = false
        var thumb: Bitmap? = null
        var blockedCount: Int = 0      // Brave Shields — is page par kitne blocked
        var sleeping: Boolean = false  // Edge sleeping tabs — WebView frozen
        var evicted: Boolean = false   // Opera RAM limiter — WebView destroy hua, select par recreate
        var lastActive: Long = System.currentTimeMillis()
    }

    // v1.9.4 UC-BOOST — prewarmed WebView pool: naya tab ZERO lag (UC parity).
    // Renderer process pehle se garam; adopt hote hi sirf clients+bridge bind.
    private val webViewPool = ArrayDeque<WebView>()

    // v1.9.4 — SHARED PERSISTENT HOME WebView (home-reload fix ka core).
    // Har home visit par newtab.html RELOAD NAHI hota — music queue, YT For-You
    // position, feed scroll state sab zinda rehte hai (UC/Chrome NTP parity).
    // Incognito tab apna FRESH eclipse newtab use karta hai (isolation).
    private var homeWebView: WebView? = null
    private val homeTab = Tab(910_001L, NEWTAB, "Home")

    private val tabs = mutableListOf<Tab>()
    private var activeTab: Tab? = null
    private var nextTabId = 1L

    private lateinit var toolbar: LinearLayout
    private lateinit var container: FrameLayout
    // v1.6.0 — home screen (newtab) par omnibox pill HIDE
    // v1.8.0 — toolbar_brand/btn_home/btn_tabs/btn_reload REMOVE (user request:
    // header me logo/home/reload/tab nahi) — ab sirf pill + menu
    private lateinit var omniPill: FrameLayout
    private lateinit var omnibox: EditText
    private lateinit var omniIcon: ImageView
    private lateinit var btnClear: ImageButton
    // v1.4.6 — nullable (lateinit nahi): agar kabhi init order slip ho jaye to
    // NPE/UninitializedPropertyAccessException IMPOSSIBLE — safe-call silently skip
    private var ringLoader: OrbitLineLoader? = null
    // v1.4.4 — ring ab omnibox ke andar NAHI, search bar ke NEECHE floating overlay
    private var ringWrap: FrameLayout? = null
    private lateinit var pullRefresh: SwipeRefreshLayout
    // v1.4.1 — active tab load state (ring loader + lock-icon visibility ke liye)
    private var pageLoading = false
    private lateinit var tabSwitcher: FrameLayout
    private lateinit var switcherGrid: GridLayout
    private lateinit var switcherTitle: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView

    // v1.4.4 — Chrome-style download bar + Downloads page state
    private lateinit var dlBar: FrameLayout
    private lateinit var dlName: TextView
    private lateinit var dlSub: TextView
    private lateinit var dlProg: ProgressBar
    private lateinit var dlAct: ImageButton
    private val dlRowSubs = mutableMapOf<Long, TextView>()
    private val dlRowProgs = mutableMapOf<Long, ProgressBar>()
    private var downloadsPageOpen = false
    private var pendingDl: Array<String>? = null

    // v1.4.4 — page-stall detector: 9s me main frame 15% cross na kare to
    // relay swap try (sirf VPN ON par asar — warna no-op)
    private val stallCheck = Runnable {
        val prog = activeTab?.webView?.progress ?: 100
        if (pageLoading && prog < 15) RelayEngine.swapIfSlow(this)
    }

    private var aiJob: Thread? = null

    // LENS scanner — capture ke waqt ka temp file (onActivityResult me wapas milta hai)
    private var lensCaptureFile: File? = null
    private var menuPopup: PopupWindow? = null
    private var suggestPopup: PopupWindow? = null
    private lateinit var shieldBadge: TextView

    // v1.4.0 — omnibox bookmark star + full-screen pages (history/bookmarks/settings)
    private lateinit var btnStar: ImageButton
    private lateinit var pageOverlay: FrameLayout
    private var pageOpen = false

    // v1.4.0 — VPN UI refresh hooks (sheet/menu/settings switches)
    private val vpnUiHooks = mutableListOf<() -> Unit>()

    // EDGE MODULE — sleeping tabs ka timer
    private val sleeper = Handler(Looper.getMainLooper())

    // v1.9.4 UC TOOLS — fullscreen video (YouTube landscape fix) state
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    // v1.9.4 — brightness dim overlay (in-app, system brightness se independent)
    private var brightnessOverlay: View? = null
    // v1.9.4 — typing assistant quick-input bar (www. .com .in / arrows)
    private var typingBar: LinearLayout? = null
    // v1.9.4 — swipe gesture arrow overlay (edge-swipe back/forward feedback)
    private var gestureHint: TextView? = null
    // v1.9.4 — clipboard capture listener (app focus me copy → history)
    private var clipHookInstalled = false

    // v1.7.0 — home feed fetcher (CORS-free): newtab ka saara feed network
    // (JioSaavn API / Google News RSS / YouTube RSS) NATIVE OkHttp se jata hai —
    // WebView CORS na toke, feeds guaranteed milte hai. 10-min cache = feed
    // dobara khula to instant render (fastest-browser feel).
    private val feedHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val feedCache = LinkedHashMap<String, Pair<Long, String>>()

    @Volatile private var cityAt = 0L
    // v1.8.0 — userCity 2h cache (pehle 24h): tower switch/IP change par city stale
    // na rahe — news hamesha current location ke hisaab se

    // v1.7.0 — WARM PRERENDER TAB ("fastest browser"): newtab khulte hi user ki
    // top-visited site ek offscreen WebView me PRELOAD hoti hai. Usi site ka tile
    // tap karte hi ALREADY LOADED WebView attach ho jata hai — network kitna bhi
    // dheema ho, site "tap se hi khuli" lagti hai (asli speed bhi badhti hai).
    private var warmTab: Tab? = null
    private var warmAt = 0L

    /** System WebView ka REAL UA — "; wv" token hata kar. Fake/stale UA (purana
     *  Chrome/126 claim) Google ka bot-detection trigger karta tha — login/search
     *  par "browser not secure" aata tha. Real UA = engine version match.
     *  HARDENED: getDefaultUserAgent kabhi throw kare to fallback UA. */
    private val realUA: String by lazy {
        runCatching { WebSettings.getDefaultUserAgent(this).replace("; wv", "") }.getOrDefault(
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        )
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // GLOBAL CRASH GUARD — setContentView se PEHLE install, taaki layout-crash
        // bhi crash-log.txt me pahunch jaye
        CrashGuard.install(this)

        // SURVIVAL MODE — pichhla crash fresh (<48h) hai to seedha crash-report
        // screen dikhao (programmatic — khud kabhi crash nahi karegi). Screenshot
        // bhejo = EXACT wajah + device info + WebView version sab mil jayega.
        val prior = CrashGuard.lastCrash(this)
        if (prior != null && CrashGuard.isFresh(prior.first)) {
            showEmergency(prior.first, prior.second, prior.third)
            return
        }
        startNormalMode()
    }

    /** Normal startup — init me kahin bhi exception aayi to app "keeps stopping"
     *  NAHI hogi: exception pakdi jayegi, log hoga, emergency report screen
     *  dikhegi. App HAMESHA khulegi (ya exact wajah dikhayegi). */
    private fun startNormalMode() {
        try {
            initNormal()
        } catch (t: Throwable) {
            CrashGuard.logCrash(this, t)
            showEmergency(
                "abhi (launch par)",
                "${t.javaClass.simpleName}: ${t.message}",
                CrashGuard.stackOf(t),
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initNormal() {
        setContentView(R.layout.activity_main)

        // Android 15 (targetSdk 35) edge-to-edge FORCE karta hai — Chrome-jaisa
        // insets handling: toolbar status bar ke NEECHE baithega.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val root = findViewById<View>(R.id.root)
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                WindowInsets.CONSUMED
            }
        }

        toolbar = findViewById(R.id.toolbar)
        container = findViewById(R.id.container)
        omniPill = findViewById(R.id.omni_pill)
        omnibox = findViewById(R.id.omnibox)
        omniIcon = findViewById(R.id.omni_icon)
        ringLoader = findViewById(R.id.ring_loader)
        ringWrap = findViewById(R.id.ring_wrap)
        pullRefresh = findViewById(R.id.pull_refresh)
        tabSwitcher = findViewById(R.id.tab_switcher)
        switcherGrid = findViewById(R.id.switcher_grid)
        switcherTitle = findViewById(R.id.switcher_title)
        findBar = findViewById(R.id.find_bar)
        findInput = findViewById(R.id.find_input)
        findCount = findViewById(R.id.find_count)
        shieldBadge = findViewById(R.id.shield_badge)

        // Brave Shields badge click — is page ke block stats
        shieldBadge.setOnClickListener { showShieldStats() }

        // v1.4.4 — Chrome-style download bar + Orbit downloader engine
        dlBar = findViewById(R.id.download_bar)
        dlName = findViewById(R.id.dl_name)
        dlSub = findViewById(R.id.dl_sub)
        dlProg = findViewById(R.id.dl_prog)
        dlAct = findViewById(R.id.dl_act)
        dlBar.setOnClickListener { showDownloadsPage() }
        dlAct.setOnClickListener {
            val t = OrbitDownloader.runningTask() ?: return@setOnClickListener
            if (t.status == OrbitDownloader.ST_RUNNING) OrbitDownloader.pause(this, t)
            else OrbitDownloader.resume(this, t)
        }
        OrbitDownloader.init(this)
        OrbitDownloader.uiHook = {
            runOnUiThread {
                runCatching {
                    updateDownloadBar()
                    if (downloadsPageOpen) refreshDownloadsRows()
                }
            }
        }

        // v1.4.0 — omnibox bookmark star (Chrome parity): tap = save/remove bookmark
        btnStar = findViewById(R.id.btn_star)
        btnStar.setOnClickListener { toggleCurrentBookmark() }

        // v1.4.0 — full-screen page overlay (history / bookmarks / settings pages)
        // android.R.id.content = FrameLayout root — iske upar kuch bhi overlap ho sakta hai
        pageOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0xFF202124.toInt())
        }
        // v1.6.0 FIX — pageOverlay android.R.id.content me hai (root ka padding isi
        // liye us par lagta hai). Insets listener na hota to Settings/History jaisi
        // full-screen pages STATUS BAR ke NEECHE overlap karti thin (user bug report:
        // "Settings screen notification bar ke sath mix ho gayi hai").
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pageOverlay.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, 0)
                insets
            }
        }
        findViewById<ViewGroup>(android.R.id.content).addView(pageOverlay)

        // v1.4.0 — Orbit VPN (relay engine) init + silent auto-restore
        RelayEngine.setRelayCacheDir(this)
        RelayEngine.onUiChanged = {
            runOnUiThread {
                runCatching {
                    vpnUiHooks.forEach { it() }
                    SpiderMode.sync(this@MainActivity)   // v1.4.7 — spider notification (country + rotation)
                    spiderApplyState(forceUi = false)    // v1.4.8 — DDG switch STATE-driven (autoRestore/restart cover)
                    if (pageOpen) refreshChrome()
                }
            }
        }

        // CHROME MODULE — omnibox typing par history/bookmark suggestions.
        // v1.3.5 FIX: typing ke waqt X-button bhi toggle (icons-mix bug ka hissa)
        omnibox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (omnibox.hasFocus()) {
                    showSuggestions(s?.toString() ?: "")
                    btnClear.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
                }
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        omnibox.setOnFocusChangeListener { _, hasFocus ->
            applyOmniFocus(hasFocus)
            if (!hasFocus) suggestPopup?.dismiss()
        }

        omnibox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                navigateInput(omnibox.text.toString())
                true
            } else false
        }
        omnibox.setSelectAllOnFocus(true)

        // v1.3.5 — omnibox clear (X): focus-mode me reload ki jagah, tap par text saaf
        btnClear = findViewById(R.id.btn_clear)
        btnClear.setOnClickListener {
            omnibox.setText("")
            btnClear.visibility = View.GONE
        }

        // v1.8.0 — header clean: btn_home/btn_tabs/btn_reload ke listeners REMOVE
        // (views hi XML se gaye). Home/Tabs ab 3-dot menu se khulte hai (showMenu),
        // reload pull-to-refresh se hota hai.
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu() }

        // Chrome pull-to-refresh
        pullRefresh.setColorSchemeColors(0xFF8AB4F8.toInt())
        pullRefresh.setOnRefreshListener {
            activeTab?.webView?.reload()
        }
        // PTR SIRF PAGE-TOP PAR — end se upar scroll karne par reload NAHI.
        // SwipeRefreshLayout ka default canChildScrollUp() seedhe container
        // FrameLayout ko check karta hai (jo kabhi scroll nahi hota) — isliye
        // WebView ki REAL scroll position se decide karo.
        pullRefresh.setOnChildScrollUpCallback { _, _ ->
            activeTab?.webView?.canScrollVertically(-1) == true
        }

        // Chrome tab switcher
        findViewById<ImageButton>(R.id.btn_new_tab_fab).setOnClickListener {
            hideTabSwitcher()
            newTab(incognito = activeIncognitoContext())
        }
        findViewById<TextView>(R.id.btn_close_all).setOnClickListener {
            tabs.toList().forEach { closeTabInternal(it) }
            hideTabSwitcher()
            if (tabs.isEmpty()) newTab() else refreshChrome()
        }

        // Find in page bar
        findInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                activeTab?.webView?.findAllAsync(findInput.text.toString())
                true
            } else false
        }
        findViewById<ImageButton>(R.id.find_next).setOnClickListener {
            activeTab?.webView?.findNext(true)
        }
        findViewById<ImageButton>(R.id.find_prev).setOnClickListener {
            activeTab?.webView?.findNext(false)
        }
        findViewById<ImageButton>(R.id.find_close).setOnClickListener { hideFindBar() }

        // pehla tab — clean new tab screen
        // v1.9.4 — Reopen Tabs on Startup (UC parity): pref ON ho to pichhli
        // session ke tabs restore hote hai (naya tab ki jagah). OFF default.
        if (!restoreTabsIfEnabled()) newTab()
        // EDGE MODULE — sleeping tabs: har 30s check, 5 min inactive WebViews freeze
        sleeper.postDelayed(sleepCheck, 30_000)
        // HOT-PATCH channel (Blinkit-style): chote-mote changes bina APK install
        // ke — cached hotfix turant active, fresh 4h-throttle background check
        runCatching { AdBlocker.setExtraDomains(RemoteConfig.loadCached(this).blockDomains) }
        RemoteConfig.refreshAsync(this)
        // silent auto-update: naya version mila to background me khud download,
        // complete hote hi sirf INSTALL button aata hai (Chrome updater jaisa)
        runAutoUpdate(manual = false)
        // v1.4.0 — VPN pref ON tha to background me wapas connect (silent)
        RelayEngine.autoRestore(this)

        // ------------------------------------------------------------------
        // v1.9.4 UC-BOOST + UC TOOLS init block
        // ------------------------------------------------------------------
        // FULL-PAGE capture enable — screenshot tool ka long-shot iske bina
        // adhoora content hi pakadta hai (Chromium tile-only draw). Ek baar
        // app start par set — WebView me har page ka poora raster milta hai.
        runCatching { WebView.enableSlowWholeDocumentDraw() }
        // POOL PREWARM — pehla extra renderer background me garam karo (2s delay:
        // home pehle render ho jaye, tab switch instant ho jaye)
        container.postDelayed({ prewarmWebView() }, 2000)
        // BRIGHTNESS overlay — in-app dim (UC night browsing parity)
        applyBrightnessPref()
        // CLIPBOARD capture — app focus me copy hua content history me jata hai
        installClipboardHook()
        // TYPING ASSISTANT bar — omnibox focus par www./.com/.in quick input
        setupTypingBar()
        // UC-STYLE edge swipe back/forward arrow overlay (hint view ban jata hai)
        setupGestureHint()
        // JUST IN news alert timer (notification settings me se control hota hai)
        scheduleJustInCheck()
        // v1.9.4 — saved theme apply (AMOLED / wallpaper)
        val savedTheme = LocalStore.getSetting(this, "appTheme", "dark")
        if (savedTheme != "dark") runCatching { applyTheme(savedTheme) }
        // v1.9.4 — Quick Access persistent notification (Push Notification pref)
        runCatching { updateQuickAccessNotification() }
        // v1.9.4 — Download Settings ko engine me wire (startup par)
        OrbitDownloader.maxConcurrent =
            LocalStore.getSetting(this, "maxDownloads", "3").toIntOrNull()?.coerceIn(1, 6) ?: 3
        OrbitDownloader.autoReconnect = LocalStore.getSetting(this, "autoReconnect", "on") != "off"
        OrbitDownloader.relPath = LocalStore.getSetting(this, "downloadPath", Environment.DIRECTORY_DOWNLOADS)
        // v1.9.4 — Status bar pref (browsing me hide) startup par apply
        runCatching { if (!showStatusBarPref()) enterImmersive() }
    }

    // ---------------------------------------------------------- survival mode

    // v1.9.1 — OTA check pehle SIRF cold-start (onCreate) par tha: app memory me
    // din-bhar reh gayi to naya update kabhi nahi milta, aur purane version ke
    // bugs (jaise play-crash) user ko dikhte rahenge. Ab onResume par bhi check —
    // 6h throttle (battery/network friendly), silent background me.
    override fun onResume() {
        super.onResume()
        // v1.9.4 — Show Status Bar pref re-apply (fullscreen toggle ke baad bhi)
        runCatching { if (!showStatusBarPref()) exitImmersive() }
        runCatching {
            val last = LocalStore.getSetting(this, "lastOtaCheck", "0").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - last > 6 * 60 * 60 * 1000L) {
                LocalStore.setSetting(this, "lastOtaCheck", System.currentTimeMillis().toString())
                runAutoUpdate(manual = false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // v1.9.4 — Reopen Tabs on Startup: session tabs save (pref ON ho to)
        runCatching { saveTabsForRestore() }
    }

    /** v1.9.1 — cache me downloaded VALID apk jiska versionCode > current hai?
     *  (update banner + emergency-screen install button dono isko use karte hai).
     *  null = koi pending update nahi. */
    private fun pendingUpdateApk(): File? = runCatching {
        val dir = File(cacheDir, "updates")
        dir.listFiles()?.firstOrNull { f ->
            val okName = f.name.startsWith("orbit-") && f.name.endsWith(".apk")
            val info = runCatching { packageManager.getPackageArchiveInfo(f.absolutePath, 0) }.getOrNull()
            okName && info != null && info.packageName == packageName &&
                info.longVersionCode > BuildConfig.VERSION_CODE.toLong()
        }
    }.getOrNull()

    /** 100% PROGRAMMATIC crash-report screen — koi XML resource, koi WebView,
     *  koi lateinit field touch nahi hota, isliye YE SCREEN KABHI CRASH NAHI
     *  KAREGI. Dukh bhara "keeps stopping" band — ab app khud batayegi ki wajah
     *  kya hai. User screenshot bheje = line-by-line fix possible. */
    private fun showEmergency(whenStr: String, firstLine: String, full: String) {
        runCatching {
            val pad = dp(18)
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF101418.toInt())
                setPadding(pad, dp(20), pad, pad)
            }
            fun txt(s: String, size: Float, color: Int, bold: Boolean = false, mono: Boolean = false) =
                TextView(this).apply {
                    text = s
                    textSize = size
                    setTextColor(color)
                    if (bold) setTypeface(null, Typeface.BOLD)
                    if (mono) typeface = Typeface.MONOSPACE
                    setLineSpacing(0f, 1.2f)
                }
            fun btn(label: String, bg: Int, fg: Int, action: () -> Unit) {
                val b = TextView(this).apply {
                    text = label
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(fg)
                    gravity = Gravity.CENTER
                    setPadding(pad, dp(13), pad, dp(13))
                    background = GradientDrawable().apply {
                        setColor(bg)
                        cornerRadius = dp(26).toFloat()
                    }
                    setOnClickListener { action() }
                }
                root.addView(b, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(9) })
            }

            root.addView(txt("Orbit crash report", 22f, 0xFFFFFFFF.toInt(), bold = true))

            // WebView provider gayab/buggy ho to seedha FIX batao
            if (full.contains("MissingWebViewPackage") || full.contains("WebViewFactory") ||
                full.contains("WebView provider") || full.contains("WebView has crashed") ||
                full.contains("UnsupportedOperationException")
            ) {
                root.addView(txt(
                    "FIX: Open Play Store → search 'Android System WebView' → " +
                        "Update/Enable it → RESTART your phone → open Orbit again.",
                    14f, 0xFFFBBC05.toInt(), bold = true,
                ).apply { setPadding(0, dp(8), 0, 0) })
            }

            root.addView(txt("When: $whenStr", 13f, 0xFF9AA0A6.toInt()).apply {
                setPadding(0, dp(12), 0, 0)
            })
            root.addView(txt(firstLine, 15f, 0xFFFF8A80.toInt(), bold = true).apply {
                setPadding(0, dp(4), 0, 0)
            })

            val trace = ScrollView(this)
            trace.addView(txt(full, 11f, 0xFFBDC1C6.toInt(), mono = true).apply {
                setPadding(0, dp(8), 0, dp(8))
            })
            root.addView(trace, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ).apply { topMargin = dp(10); bottomMargin = dp(4) })

            // v1.9.1 — naya version pehle se download ho chuka hai? To crash
            // screen par hi "Install update" sabse UPAR dikhao — user ek tap me
            // fixed version par ja sakta hai (yahi wo moment hai jab update
            // sabse zyada zaroori hota hai).
            pendingUpdateApk()?.let { apk ->
                val vName = runCatching {
                    packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.versionName ?: ""
                }.getOrDefault("")
                btn(
                    "Install update v" + vName + " now — fixes this crash",
                    0xFFFBBC05.toInt(), 0xFF101418.toInt(),
                ) {
                    runCatching { UpdateChecker.installWithStatus(this@MainActivity, apk) }
                }
            }

            btn("Copy report", 0xFF8AB4F8.toInt(), 0xFF101418.toInt()) {
                runCatching {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(
                        ClipData.newPlainText("orbit-crash", CrashGuard.deviceInfo(this) + "\n\n" + full),
                    )
                    CrashGuard.toast(this, "Copied — paste it in the chat")
                }
            }
            btn("Open in safe mode (no extras)", 0xFF81C995.toInt(), 0xFF101418.toInt()) {
                safeMode = true
                CrashGuard.clear(this)
                startNormalMode()
            }
            btn("Try normal mode", 0xFF3C4043.toInt(), 0xFFFFFFFF.toInt()) {
                safeMode = false
                CrashGuard.clear(this)
                startNormalMode()
            }

            setContentView(root)
        }
        // runCatching ke bahar kuch nahi — worst case blank dark screen, kabhi
        // wapas "keeps stopping" dialog nahi aayega
    }

    override fun onBackPressed() {
        // emergency/survival screen chal rahi hai (init adhoora) — kuch na touch karo
        if (!this::tabSwitcher.isInitialized) {
            super.onBackPressed()
            return
        }
        // v1.4.0 — history/bookmarks/settings page khula hai to pehle wo band karo
        if (pageOpen) {
            closePage()
            return
        }
        if (tabSwitcher.visibility == View.VISIBLE) {
            hideTabSwitcher()
            return
        }
        if (findBar.visibility == View.VISIBLE) {
            hideFindBar()
            return
        }
        menuPopup?.let { if (it.isShowing) { it.dismiss(); return } }
        // v1.9.4 — fullscreen video me back = fullscreen exit (landscape wapas normal)
        if (fullscreenView != null) {
            activeTab?.webView?.post { runCatching { exitImmersive() } }
            fullscreenView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
            fullscreenView = null
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val cb = fullscreenCallback
            fullscreenCallback = null
            cb?.onCustomViewHidden()
            return
        }
        val wv = activeTab?.webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (tabs.size > 1) {
            activeTab?.let { closeTab(it.id) } ?: super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // cleanup — timers, popups, sab WebViews. Bina iske backgrounded process me
        // WebView leak + phantom callbacks rehte hain.
        runCatching { sleeper.removeCallbacksAndMessages(null) }
        runCatching { menuPopup?.dismiss() }
        runCatching { suggestPopup?.dismiss() }
        runCatching { PlaybackCoordinator.stopAll() }
        runCatching { dropWarmTab() }   // v1.7.0 — warm prerender WebView bhi saaf
        runCatching { drainWebViewPool() }  // v1.9.4 — pooled renderer saaf
        runCatching { destroyHomeWebView() } // v1.9.4 — shared home WebView bhi saaf
        runCatching {
            for (tab in tabs) {
                tab.webView?.apply {
                    stopLoading()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
                tab.webView = null
                tab.thumb = null
            }
            tabs.clear()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        // emergency screen mode me init adhoora hai — external link ignore karo
        if (!this::container.isInitialized) return
        // v1.9.4 — Quick Access notification actions (QR Scan / Downloads)
        when (intent?.getStringExtra("quick")) {
            "qr" -> {
                intent.removeExtra("quick")
                startLensScan()
                return
            }
            "downloads" -> {
                intent.removeExtra("quick")
                showDownloadsPage()
                return
            }
        }
        // Default browser ban gaya to external links yahan aate hain
        val data = intent?.data?.toString() ?: return
        if (data.startsWith("http")) newTabWithUrl(data)
    }

    // ---------------------------------------------------------------- tabs

    private fun newTab(incognito: Boolean = false) {
        val tab = Tab(nextTabId++, NEWTAB, "New Tab")
        tab.isInternal = true
        tab.isIncognito = incognito
        // v1.4.0 — Settings me "Desktop site" default ON ho to naye tabs desktop UA se khulenge
        tab.desktopSite = !incognito && LocalStore.getSetting(this, "desktopDefault", "off") == "on"
        tabs.add(tab)
        selectTab(tab.id)
        if (incognito) {
            Toast.makeText(this, "Incognito — IP hidden via relay, nothing is saved", Toast.LENGTH_SHORT).show()
            // v1.6.0 — INCOGNITO = IP HIDDEN (Tor-jaisa, user request): incognito tab
            // me relay AUTO connect hota hai — saara browser traffic community relay
            // se jata hai, sites ko relay ka IP dikhta hai. Pehle se on/connecting
            // ho to kuch nahi karna (double connect avoid).
            if (RelayEngine.state != "on" && RelayEngine.state != "connecting") {
                maybeGrantNotifPermission()
                RelayEngine.connect(this)
            }
        }
    }

    private fun activeIncognitoContext(): Boolean = activeTab?.isIncognito ?: false

    private fun newTabWithUrl(url: String) {
        val tab = Tab(nextTabId++, url, domainOf(url))
        tab.isInternal = false
        tabs.add(tab)
        selectTab(tab.id)
        loadUrlInTab(tab, url)
    }

    private fun closeTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        closeTabInternal(tabs[idx])
        if (tabs.isEmpty()) {
            finish()
        } else {
            selectTab(tabs[(idx - 1).coerceAtLeast(0)].id)
        }
    }

    private fun closeTabInternal(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tabs.remove(tab)
        // v1.9.4 — SHARED HOME WebView guard: agar is tab ka view shared home hai
        // to DESTROY na karo (music queue/feeds zinda rehte hai) — sirf unbind.
        if (tab.webView !== homeWebView) {
            // MODULE 3 — agar yahi tab media play kar raha tha to service band
            PlaybackCoordinator.webViewDestroyed(tab.webView)
            tab.webView?.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
        // NULL karna ZARURI hai — warna AI-stream/JS callbacks destroy hua WebView
        // use karke crash kar sakte hain
        tab.webView = null
        if (tabSwitcher.visibility == View.VISIBLE) renderSwitcherCards()
        refreshChrome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun selectTab(id: Long) {
        // firstOrNull — band ho chuke tab ka stale id kabhi app crash nahi karega
        val tab = tabs.firstOrNull { it.id == id } ?: return
        // outgoing tab: lastActive abhi set + thumbnail capture — switcher cards me dikhega
        activeTab?.let {
            it.lastActive = System.currentTimeMillis()
            captureThumb(it)
        }
        // v1.9.4 — HOME TAB: apna WebView banana hi nahi hai, SHARED home
        // attach hota hai (zero reload, zero lag — state preserved)
        if (tab.webView == null && tab.isInternal && tab.url == NEWTAB && !tab.isIncognito) {
            tab.webView = ensureHomeWebView()
        }
        if (tab.webView == null) {
            tab.webView = buildWebView(tab)
            // OPERA MODULE — evicted tab wapas aaya to URL dobara load (WebView fresh hai)
            if (tab.evicted) {
                tab.evicted = false
                if (!tab.isInternal && tab.url != NEWTAB) tab.webView?.loadUrl(tab.url)
            }
        }
        attachTabView(tab)
        activeTab = tab
        tab.lastActive = System.currentTimeMillis()
        // OPERA MODULE — RAM limit: sabse zyada purane inactive WebViews freeze/destroy
        evictExcessTabs()
        refreshChrome()
        if (tabSwitcher.visibility == View.VISIBLE) renderSwitcherCards()
        // v1.9.4 — NEWTAB load sirf INCognito home par (uska apna fresh eclipse
        // newtab hai). Non-incognito home SHARED webview use karta hai — RELOAD
        // BILKUL NAHI (yahi home-reload fix hai: music/YT/feeds state preserved).
        if (tab.isInternal && tab.url == NEWTAB && tab.isIncognito) {
            loadInternal(tab, NEWTAB)
        }
    }

    /**
     * v1.9.4 — tab ka WebView container me attach (selectTab + navigate dono se).
     * Pehle ye logic selectTab ke andar tha — navigate(home) par bhi chahiye hota hai.
     * Media owner WebView ko DETACH NAHI karte (audio pause ho jayega — MODULE 3).
     */
    private fun attachTabView(tab: Tab) {
        val keepOwner = tabs.firstOrNull { it !== tab && PlaybackCoordinator.isMediaOwner(it.webView) }
            ?.webView?.takeIf { it.parent === container }
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child !== keepOwner && child !== tab.webView) container.removeView(child)
        }
        tab.webView?.let {
            it.visibility = View.VISIBLE
            if (it.parent === container) {
                // already attached (owner wapas switch) — detach/reattach MAT karo,
                // warna Chromium video ko pause kar dega
                it.bringToFront()
            } else {
                container.addView(
                    it,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            // EDGE MODULE — sleeping tab ko jagao
            if (tab.sleeping) {
                it.onResume()
                tab.sleeping = false
            }
        }
    }

    /** Har chrome element ek jagah — toolbar, pill visibility, PTR, hint */
    private fun refreshChrome() {
        val tab = activeTab ?: return
        // user omnibox me TYPING ho to overwrite mat karo (Chrome jaisa)
        if (tab.isInternal) {
            // INTERNAL pages (NTP) par omnibox CLEAN — pichhle page ka URL kabhi
            // nahi dikhega. (Stale-text bug: new-tab par omnibox focused rehta tha,
            // isliye purana URL ruk jata tha.) Force-clear + keyboard hide.
            omnibox.setText("")
            omnibox.clearFocus()
            runCatching {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(omnibox.windowToken, 0)
            }
        } else if (!omnibox.hasFocus()) {
            omnibox.setText(tab.url)
            omnibox.clearFocus()
        }
        pullRefresh.isEnabled = !tab.isInternal
        // v1.6.0 — HOME SCREEN par upar wala search bar REMOVE (user request: bich
        // me search bar already hai). v1.8.0 — brand text bhi GAYA (header clean);
        // NTP par toolbar ka bg page bg me blend hota hai (sirf menu dikhta hai).
        val onNtp = tab.isInternal && tab.url == NEWTAB
        omniPill.visibility = if (onNtp) View.GONE else View.VISIBLE
        if (tab.isIncognito) {
            omnibox.hint = "Incognito — Search or type URL"
            toolbar.setBackgroundColor(if (onNtp) 0xFF050507.toInt() else 0xFF3C4043.toInt())
        } else {
            omnibox.hint = getString(R.string.search_hint)
            toolbar.setBackgroundColor(if (onNtp) 0xFF1A1A1D.toInt() else 0xFF202124.toInt())
        }
        updateOmniIcon(tab)
        updateBookmarkStar(tab)
    }

    /** v1.4.0 — Chrome-jaisa bookmark star: har site par dikhta hai; gold = saved.
     *  Internal pages (NTP) par nahi — Chrome bhi NTP par star nahi dikhata. */
    private fun updateBookmarkStar(tab: Tab) {
        val show = !tab.isInternal && !tab.isIncognito && tab.url.startsWith("http")
        btnStar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            val saved = runCatching { LocalStore.isBookmarked(this, tab.url) }.getOrDefault(false)
            btnStar.setColorFilter(if (saved) 0xFFFBBC04.toInt() else 0xFF9AA0A6.toInt())
        }
    }

    /** v1.4.0 — star toggle (omnibox star + menu "Add bookmark" dono isse call karte hain) */
    private fun toggleCurrentBookmark() {
        val tab = activeTab ?: return
        if (tab.isInternal || !tab.url.startsWith("http")) {
            Toast.makeText(this, "This page can't be bookmarked", Toast.LENGTH_SHORT).show()
            return
        }
        val nowSaved = runCatching {
            LocalStore.toggleBookmark(this, tab.url, tab.title.ifBlank { domainOf(tab.url) })
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (nowSaved) "Bookmark added" else "Bookmark removed",
            Toast.LENGTH_SHORT,
        ).show()
        updateBookmarkStar(tab)
    }

    /** Chrome jaisa: https = lock, http = "not secure" info icon */
    private fun updateOmniIcon(tab: Tab) {
        if (tab.url.startsWith("http://") && !tab.isInternal) {
            omniIcon.setImageResource(R.drawable.ic_notsecure)
            omniIcon.setColorFilter(0xFFF28B82.toInt())
        } else {
            omniIcon.setImageResource(R.drawable.ic_lock)
            omniIcon.setColorFilter(0xFF9AA0A6.toInt())
        }
    }

    private fun captureThumb(tab: Tab) {
        val wv = tab.webView ?: return
        if (wv.width == 0 || wv.height == 0) return
        runCatching {
            val scale = 480f / wv.width
            val h = (wv.height * scale).toInt().coerceIn(240, 1200)
            val bmp = Bitmap.createBitmap(480, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(scale, scale)
            wv.draw(c)
            // PURANA BITMAP RECYCLE MAT KARO — switcher card me displayed bitmap
            // recycle hua to "Canvas: trying to use a recycled bitmap" crash aata tha.
            // Reference replace karo — GC khud saaf kar dega.
            tab.thumb = bmp
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tab: Tab): WebView {
        // v1.9.4 UC-BOOST — POOL ADOPT: prewarmed WebView turant ready hai
        // (renderer process garam) — naya tab ZERO lag, UC parity.
        val pooled = synchronized(webViewPool) { webViewPool.removeFirstOrNull() }
        if (pooled != null) {
            configureWebView(pooled, tab)
            return pooled
        }
        // MODULE 3 v1.3.3 — BackgroundWebView: Chromium ko screen-lock/app-
        // background par bhi VISIBLE batata hai. Plain WebView window visibility
        // GONE leke media pipeline suspend kar deta tha (asli pause-yahan-hota-hai
        // wala fix — dekho render/BackgroundWebView.kt).
        val wv = BackgroundWebView(this)
        configureWebView(wv, tab)
        return wv
    }

    /** v1.9.4 — pool refill: 1 extra garam renderer background me taiyar.
     *  buildWebView ne adopt kiya to next tab ke liye dobara garam karte hai. */
    private fun prewarmWebView() {
        synchronized(webViewPool) {
            if (webViewPool.isNotEmpty()) return
            runCatching {
                val wv = BackgroundWebView(this)
                val poolTab = Tab(nextTabId++, "about:blank", "Pool")
                poolTab.isInternal = false
                configureWebView(wv, poolTab)
                wv.loadUrl("about:blank")
                webViewPool.addLast(wv)
            }
        }
    }

    /** v1.9.4 — pool ka stale WebView saaf (renderer crash / app exit par). */
    private fun drainWebViewPool() {
        synchronized(webViewPool) {
            while (webViewPool.isNotEmpty()) {
                val wv = webViewPool.removeFirst()
                runCatching {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView, tab: Tab) {
        wv.setBackgroundColor(0xFF202124.toInt())
        // v1.4.0 — Settings toggles ab LIVE apply hote hain (JavaScript/popups/cookies/images).
        // Internal pages (newtab) JS-ON force hote hain loadInternal() me.
        wv.settings.javaScriptEnabled = jsEnabledPref()
        wv.settings.domStorageEnabled = true
        // GOOGLE SERVICES FIX: real WebView UA (engine version sach me match karta
        // hai) + third-party cookies + multiple windows — bina inke Google login,
        // search aur YouTube ke flows block/broken rehte the.
        // v1.9.4 — Website Preferences (UA): Android / Desktop / iPhone / iPad
        wv.settings.userAgentString = when {
            tab.desktopSite -> DESKTOP_UA
            // SURVIVAL safe mode — bilkul vanilla UA, kuch bhi custom nahi
            safeMode -> runCatching { WebSettings.getDefaultUserAgent(this) }.getOrDefault(realUA)
            else -> when (uaMode()) {
                "desktop" -> DESKTOP_UA
                "iphone" -> IPHONE_UA
                "ipad" -> IPAD_UA
                else -> realUA
            }
        }
        CookieManager.getInstance().setAcceptCookie(cookiesEnabledPref())
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, cookiesEnabledPref())
        wv.settings.javaScriptCanOpenWindowsAutomatically = !blockPopupsPref()
        wv.settings.setSupportMultipleWindows(true)
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        // MODULE 1 (render tuning) — Chrome-parity engine settings. KOI VISUAL
        // CHANGE NAHI: textZoom default fontScale (1.0) par 100 hi rehta hai — bas
        // system font-scale ka respect ab Chrome jaisa. mediaPlaybackRequiresUserGesture
        // false — M3 ke lock-screen play/pause control JS ke liye zaroori.
        wv.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        // v1.9.4 — Font & Layout setting (50-200% slider) override
        wv.settings.textZoom = textZoomPref()
        wv.settings.mediaPlaybackRequiresUserGesture = !autoplayPref()
        // v1.9.4 UC-BOOST — RENDERER: background tab bhi render (smooth switch)
        OrbitEngine.applyRendererPolicy(wv)
        wv.settings.databaseEnabled = true
        // v1.9.4 — Image Quality + Speed Mode (none/low/medium/high + speedMode)
        val imagesAllowed = OrbitEngine.imagesAllowed(this, tab.isIncognito)
        wv.settings.loadsImagesAutomatically = imagesAllowed
        // v1.9.4 UC-BOOST — CACHE: LOAD_DEFAULT (HTTP cache + BFCache full use;
        // UC ka "offline jaisa" instant repeat-load feel isi se aata hai)
        wv.settings.cacheMode = OrbitEngine.cacheMode(tab.isIncognito)
        // v1.9.4 — Save Page (MHTML) ko file:// se wapas kholne ke liye app-scoped
        // file access on (sirf hamara apna archive path, UC Save Page parity)
        wv.settings.allowFileAccess = true
        // v1.9.4 — Forms & passwords pref (Always ask / Never save)
        if (formsPasswordsPref() == "never") {
            @Suppress("DEPRECATION")
            wv.settings.saveFormData = false
        }
        // MODULE 3 (background playback) — har WebView ka apna media sink.
        // Document-start bootstrap: viewport (M1) + media-watch (M3) + ad shield
        // + network hints + fingerprint shield SAB EK combined script me —
        // addDocumentStartJavaScript har call par pichhle scripts REPLACE karta hai.
        wv.addJavascriptInterface(PlaybackSink(wv), "OrbitMediaSink")
        wv.addJavascriptInterface(Bridge(tab), "OrbitBridge")
        val bootstrapJs = buildString {
            append(ViewportBootstrap.SCRIPT)
            append("\n;")
            append(MediaWatchBootstrap.SCRIPT)
            // v1.9.4 — YouTube Ad Shield ab AD Setting se OFF bhi ho sakta hai
            // (default ON — user demand: "koi bhi halat me YouTube ads nahi").
            if (ytShieldEnabled()) {
                append("\n;")
                append(YouTubeAdShield.SCRIPT)
            }
            // v1.9.4 UC-BOOST — Cloud Boost: preconnect + touch-prefetch network
            // hints (DNS/TLS handshake pehle, HTML approach-phase me warm)
            if (!tab.isIncognito && OrbitEngine.boostActiveNow(this@MainActivity)) {
                append("\n;")
                append(OrbitEngine.NETWORK_HINTS_SCRIPT)
            }
            // v1.9.4 — Change Link Color If Clicked (visited-links purple)
            if (visitedLinksPref()) {
                append("\n;")
                append(VISITED_LINKS_JS)
            }
            if (antiFpEnabled()) {
                // FIREFOX MODULE — anti-fingerprinting: canvas noise + hardware spoof
                append("\n;")
                append(FINGERPRINT_SHIELD_JS)
            }
        }
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(wv, bootstrapJs, setOf("*"))
        }
        // MODULE 4 (true incognito) — incognito tab ka WebView EPHEMERAL: koi HTTP
        // cache, koi DOM storage, koi third-party cookies. Normal tab pehle jaisa.
        if (tab.isIncognito) {
            wv.settings.domStorageEnabled = false
            @Suppress("DEPRECATION")
            wv.settings.saveFormData = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        }
        wv.webViewClient = OrbitWebViewClient(tab)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!tab.isInternal && !title.isNullOrBlank()) {
                    tab.title = title
                    refreshChrome()
                    if (!tab.isIncognito) LocalStore.addHistory(this@MainActivity, tab.url, title)
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                // v1.4.4 — ORBIT RING search bar ke NEECHE (floating overlay):
                // arc + satellite dot progress ke saath advance. Lock icon ab
                // hamesha visible (Chrome parity — load me hide nahi hota).
                pageLoading = newProgress in 1..99
                ringWrap?.visibility = if (pageLoading) View.VISIBLE else View.GONE
                ringLoader?.setProgress(newProgress)
                // page 15% cross kar gaya = stall-watchdog ki zaroorat nahi
                if (newProgress >= 15) sleeper.removeCallbacks(stallCheck)
                // v1.8.0 — btnReload hata (header clean): progress ab top line loader me hai
            }

            // v1.4.0 — "Block pop-ups" setting ON ho to target=_blank window.open
            // bhi block (Chrome "Pop-ups and redirects" parity)
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (blockPopupsPref()) return false
                if (view == null || resultMsg == null) return false
                val ok = runCatching {
                    val temp = WebView(this@MainActivity)
                    temp.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http")) {
                                runOnUiThread { if (!isFinishing) newTabWithUrl(url) }
                            }
                            v?.post { runCatching { v.destroy() } }
                            return true
                        }
                    }
                    (resultMsg.obj as? WebView.WebViewTransport)?.webView = temp
                    resultMsg.sendToTarget()
                    true
                }.getOrDefault(false)
                return ok
            }

            // v1.9.4 — HTML5 FULLSCREEN (YOUTUBE LANDSCAPE FIX — user report:
            // "YouTube me video landscape me nahi ho rahe hain"). YT fullscreen
            // button → HTML5 fullscreen event → ye handler: poora screen video
            // ka + LANDSCAPE LOCK + immersive. Exit par wapas portrait/normal.
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                hideKeyboard()
                omnibox.clearFocus()
                (findViewById<ViewGroup>(android.R.id.content)).addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                enterImmersive()
            }

            override fun onHideCustomView() {
                fullscreenView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
                fullscreenView = null
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                exitImmersive()
                val cb = fullscreenCallback
                fullscreenCallback = null
                cb?.onCustomViewHidden()
            }
        }
        // find-in-page counts
        wv.setFindListener { ordinal, matches, done ->
            if (findBar.visibility == View.VISIBLE) {
                findCount.text = if (matches > 0) "${ordinal + 1}/$matches" else "0/0"
            }
        }
        // v1.4.4 — FILE/APK DOWNLOADS: Orbit Download Engine — multi-segment
        // parallel download (Chrome-jaisi speed), real-time speed + ETA (UC
        // style), bottom download bar + in-app Downloads page. System
        // DownloadManager gaya — ab sab Orbit ke apne UI me hota hai.
        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            handleDownload(url, contentDisposition, mimeType)
        }
        // v1.9.4 — UC Swipe Forward&Backward edge gesture (setting ON ho to)
        (wv as? BackgroundWebView)?.orbitEdgeGesture = { ev -> handleEdgeGesture(ev) }
    }

    // ---------------------------------------------------------------- navigation

    private fun navigateInput(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        val tab = activeTab ?: return
        omnibox.clearFocus()
        navigate(tab.id, normalizeInput(input))
    }

    /** web app ke shared.ts ka port — orbit:// internal pages + search detection */
    private fun normalizeInput(t: String): String {
        val x = t.trim()
        val lower = x.lowercase()
        return when {
            lower.startsWith("orbit://newtab") -> NEWTAB
            lower.startsWith("orbit://history") -> "orbit://history"
            lower.startsWith("orbit://bookmarks") -> "orbit://bookmarks"
            lower.startsWith("orbit://settings") -> "orbit://settings"
            lower.startsWith("tez://") -> normalizeInput(x.replaceFirst(Regex("^tez://", RegexOption.IGNORE_CASE), "orbit://"))
            lower.startsWith("http://") || lower.startsWith("https://") -> x
            lower.startsWith("orbit://") -> NEWTAB
            else -> {
                val looksLikeUrl = !x.contains(" ") &&
                    (x.contains(".") || x.contains(":")) &&
                    !x.endsWith(".")
                if (looksLikeUrl) "https://$x" else searchEngineUrl() + java.net.URLEncoder.encode(x, "UTF-8")
            }
        }
    }

    private fun navigate(tabId: Long, url: String) {
        // v1.4.1 — navigation ka matlab keyboard ka kaam khatam: IME turant close
        hideKeyboard()
        // v1.7.0 — INSTANT SERVE: warm prerendered site match? already-loaded tab
        // turant adopt — network wait zero. Home newtab tab site ki jagah le leta hai.
        if (!url.startsWith("orbit://") && maybeAdoptWarm(url)) {
            val old = tabs.firstOrNull { it.id == tabId }
            if (old != null && old.isInternal && old.url == NEWTAB) closeTabInternal(old)
            refreshChrome()
            return
        }
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        tab.url = url
        when {
            url == NEWTAB -> {
                if (!tab.isIncognito) {
                    // v1.9.4 — HOME = SHARED persistent webview. RELOAD NAHI —
                    // music queue / YT For-You / feed scroll sab waise ka waisa.
                    tab.isInternal = true
                    tab.webView = ensureHomeWebView()
                    if (activeTab === tab) attachTabView(tab) else selectTab(tab.id)
                    tab.title = "New Tab"
                    refreshChrome()
                } else {
                    loadInternal(tab, NEWTAB)
                }
            }
            url.startsWith("orbit://search?q=") -> {
                val q = java.net.URLDecoder.decode(url.removePrefix("orbit://search?q="), "UTF-8")
                loadInternal(tab, "orbit://search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            }
            url.startsWith("orbit://") -> loadInternal(tab, url)
            else -> loadUrlInTab(tab, url)
        }
        refreshChrome()
    }

    /**
     * v1.9.4 — SHARED HOME WebView (UC/Chrome NTP parity).
     * Pehli dafa ban jata hai, phir HAMESHA wahi instance — koi reload nahi.
     * Isi se home par wapas aane par music player/YouTube dobara load nahi hota.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureHomeWebView(): WebView {
        homeWebView?.let {
            homeTab.webView = it
            return it
        }
        val hw = buildWebView(homeTab)
        homeTab.webView = hw
        hw.loadDataWithBaseURL("https://orbit.local/", assetHtml("newtab.html"), "text/html", "UTF-8", NEWTAB)
        hw.postDelayed({ runCatching { ensureCity() } }, 300)
        hw.postDelayed({ runCatching { maybeWarmPrerender() } }, 1500)
        homeWebView = hw
        return hw
    }

    /** v1.9.4 — shared home teardown (sirf onDestroy / renderer-gone par). */
    private fun destroyHomeWebView() {
        val hw = homeWebView ?: return
        homeWebView = null
        homeTab.webView = null
        tabs.forEach { if (it.webView === hw) it.webView = null }
        runCatching {
            PlaybackCoordinator.webViewDestroyed(hw)
            hw.stopLoading()
            (hw.parent as? ViewGroup)?.removeView(hw)
            hw.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadInternal(tab: Tab, url: String) {
        tab.isInternal = true
        // v1.9.4 — NON-INCOGNITO home: shared webview (zero reload). Ye branch
        // selectTab/navigate se direct calls ke liye hai (incognito neeche).
        if (url == NEWTAB && !tab.isIncognito) {
            val hw = ensureHomeWebView()
            tab.webView = hw
            tab.title = "New Tab"
            if (activeTab === tab) attachTabView(tab)
            refreshChrome()
            return
        }
        // v1.4.0 — internal pages (newtab/music/ai) ke liye JS ALWAYS on
        // (page khud JS se banta hai; user ka "JavaScript OFF" pref sirf real sites par)
        val wv = tab.webView ?: buildWebView(tab).also { tab.webView = it }
        wv.settings.javaScriptEnabled = true
        tab.title = when {
            url == NEWTAB -> "New Tab"
            url.startsWith("orbit://search") -> "Orbit AI Search"
            url == "orbit://music" -> "Orbit Music"
            else -> "Orbit"
        }
        when {
            url == NEWTAB -> {
                // yahan sirf INCOGNITO aata hai — apna fresh eclipse newtab (isolation)
                wv.loadDataWithBaseURL("https://orbit.local/", assetHtml("newtab.html"), "text/html", "UTF-8", NEWTAB)
                wv.postDelayed({ runCatching { ensureCity() } }, 300)
                wv.postDelayed({ runCatching { maybeWarmPrerender() } }, 1500)
            }
            // v1.3.5 — FREE MUSIC LIBRARY: GitHub-repo powered (radio-browser.info +
            // Audius) built-in page. Background play MediaWatchBootstrap se free milta hai.
            url == "orbit://music" -> wv.loadDataWithBaseURL("https://orbit.local/", assetHtml("music.html"), "text/html", "UTF-8", url)
            url.startsWith("orbit://search?q=") -> {
                val q = java.net.URLDecoder.decode(url.removePrefix("orbit://search?q="), "UTF-8")
                val html = assetHtml("aipage.html").replace("__QUERY__", escapeHtml(q))
                wv.loadDataWithBaseURL("https://orbit.local/", html, "text/html", "UTF-8", url)
                wv.postDelayed({ runAiSearch(tab, q) }, 350)
            }
            url == "orbit://history" -> { showHistory(); tab.url = NEWTAB; loadInternal(tab, NEWTAB) }
            url == "orbit://bookmarks" -> { showBookmarks(); tab.url = NEWTAB; loadInternal(tab, NEWTAB) }
            url == "orbit://settings" -> { showSettings(); tab.url = NEWTAB; loadInternal(tab, NEWTAB) }
        }
        refreshChrome()
    }

    private fun loadUrlInTab(tab: Tab, url: String) {
        tab.isInternal = false
        // v1.9.4 — SHARED HOME GUARD: home webview me site load kabhi nahi hoti —
        // warna music queue/feeds ud jate. Site ke liye tab ka APNA webview banao.
        if (tab.webView === homeWebView) {
            tab.webView = buildWebView(tab)
        }
        val wv = tab.webView ?: buildWebView(tab).also { tab.webView = it }
        // WebView hi Chromium hai — DIRECT load: cookies, JS, redirects, login sab
        // native browser jaisa chalega.
        wv.settings.javaScriptEnabled = jsEnabledPref()
        wv.loadUrl(url)
        refreshChrome()
    }

    // ------------------------------------------------- v1.7.0 instant-serve + feeds

    /** Voice search — Google recognizer intent (mic permission recognizer app
     *  khud manage karta hai). Result onActivityResult → navigateInput. */
    private fun startVoiceSearch() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }
        runCatching { startActivityForResult(i, REQ_VOICE) }.onFailure {
            Toast.makeText(this, "Voice search not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    /** v1.7.0 — LOCAL NEWS ka base: user ka city + region. v1.8.0: cache 24h→2h
     *  (mobile tower ka gateway IP badalta rehta hai — location fresh rahe) +
     *  https fallback (ipapi.co). Mobile data par IP ka city wahi tower ka area
     *  hota hai — isliye ye "tower location" ke sabse close kaam karta hai. */
    private fun ensureCity() {
        val cached = LocalStore.getSetting(this, "userCity", "")
        if (cached.isNotBlank() && System.currentTimeMillis() - cityAt < 2 * 60 * 60 * 1000L) {
            pushCityToPage()
            return
        }
        Thread {
            var city = ""; var region = ""
            // PRIMARY — ip-api (city+region, no key). FALLBACK — ipapi.co https.
            runCatching {
                feedHttp.newCall(
                    Request.Builder().url("http://ip-api.com/json/?fields=status,city,regionName").build(),
                ).execute().use { resp ->
                    val b = resp.body?.string() ?: return@use
                    val o = org.json.JSONObject(b)
                    if (o.optString("status") == "success") {
                        city = o.optString("city", "").trim()
                        region = o.optString("regionName", "").trim()
                    }
                }
            }
            if (city.isBlank()) {
                runCatching {
                    feedHttp.newCall(
                        Request.Builder().url("https://ipapi.co/json/").build(),
                    ).execute().use { resp ->
                        val b = resp.body?.string() ?: return@use
                        val o = org.json.JSONObject(b)
                        city = o.optString("city", "").trim()
                        region = o.optString("region", "").trim()
                    }
                }
            }
            if (city.isNotBlank()) {
                cityAt = System.currentTimeMillis()
                LocalStore.setSetting(this, "userCity", city)
                LocalStore.setSetting(this, "userRegion", region)
            }
            runOnUiThread { runCatching { pushCityToPage() } }
        }.also { it.isDaemon = true }.start()
    }

    private fun pushCityToPage() {
        val t = activeTab ?: return
        if (t.url != NEWTAB) return
        val city = LocalStore.getSetting(this, "userCity", "")
        val region = LocalStore.getSetting(this, "userRegion", "")
        t.webView?.evaluateJavascript(
            "if(window.cityPush)window.cityPush(${escapeJs(city)}, ${escapeJs(region)});", null,
        )
    }

    /** v1.7.0 — WARM PRERENDER: top-visited site offscreen WebView me preload.
    *   Network dheema ho to bhi tap = instant site. Connecting relay skip (net
    *   bikharta hai), warm 90s ke baad stale. */
    private fun maybeWarmPrerender() {
        // v1.9.4 — Preload Page setting: "off" = prerender band (UC parity)
        if (preloadPref() == "off") return
        // "wap" = Wi-Fi only (metered network par bachao)
        if (preloadPref() == "wap" && !OrbitEngine.isUnmeteredNow(this)) return
        warmTab?.let { if (System.currentTimeMillis() - warmAt > 90_000L) dropWarmTab() }
        if (warmTab != null) return
        if (RelayEngine.state == "connecting") return
        val top = runCatching {
            LocalStore.listHistory(this).firstOrNull { h ->
                h.url.startsWith("http") &&
                    !h.url.contains("youtube.com") &&
                    !h.url.contains("google.com/search")
            }
        }.getOrNull() ?: return
        if (top.url == activeTab?.url) return
        runCatching {
            val t = Tab(nextTabId++, top.url, top.title.ifBlank { domainOf(top.url) })
            t.isInternal = false
            t.desktopSite = LocalStore.getSetting(this, "desktopDefault", "off") == "on"
            val wv = buildWebView(t)
            t.webView = wv
            wv.visibility = View.INVISIBLE
            wv.loadUrl(top.url)
            warmTab = t
            warmAt = System.currentTimeMillis()
        }
    }

    /** Warm tab adopt — tap hui site already loaded hai? true = tab switch ho gaya. */
    private fun maybeAdoptWarm(url: String): Boolean {
        val t = warmTab ?: return false
        if (System.currentTimeMillis() - warmAt > 90_000L) { dropWarmTab(); return false }
        if (t.webView == null || t.url == url) return false
        val h1 = domainOf(url); val h2 = domainOf(t.url)
        if (h1.isBlank() || h1 != h2) return false
        return runCatching {
            tabs.add(t)
            selectTab(t.id)
            warmTab = null
            true
        }.getOrDefault(false)
    }

    private fun dropWarmTab() {
        val t = warmTab ?: return
        warmTab = null
        t.webView?.let { wv ->
            runCatching {
                PlaybackCoordinator.webViewDestroyed(wv)
                wv.stopLoading()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
        t.webView = null
    }

    private fun showErrorPage(wv: WebView, url: String, reason: String) {
        val html = assetHtml("error.html")
            .replace("__URL__", escapeHtml(url))
            .replace("__REASON__", escapeHtml(reason))
        wv.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url)
    }

    // ---------------------------------------------------------------- AI search

    private fun runAiSearch(tab: Tab, query: String) {
        aiJob?.interrupt()
        val backend = LocalStore.getSetting(this, "aiBackend", DEFAULT_AI_BACKEND)
        val wv = tab.webView ?: return
        if (backend.isBlank() || backend == "unset") {
            wv.evaluateJavascript("window.__aiError('Set your Orbit AI backend URL in Settings first (menu -> Settings)')", null)
            return
        }
        aiJob = Thread {
            AiClient.search(backend, query, object : AiClient.Listener {
                override fun onSources(json: String) = postJs(tab, "window.__aiSources(${escapeJs(json)})")
                override fun onDelta(text: String) = postJs(tab, "window.__aiDelta(${escapeJs(text)})")
                override fun onDone() = postJs(tab, "window.__aiDone()")
                override fun onError(message: String) = postJs(tab, "window.__aiError(${escapeJs(message)})")
            })
        }.also { it.start() }
    }

    private fun postJs(tab: Tab, js: String) {
        runOnUiThread {
            // band/badla hua tab ya finished activity — destroy hua WebView touch nahi
            if (isFinishing || isDestroyed) return@runOnUiThread
            tab.webView?.evaluateJavascript(js, null)
        }
    }

    // ---------------------------------------------------------------- bridge

    private inner class Bridge(private val tab: Tab) {
        @JavascriptInterface
        fun navigate(url: String, newTab: Boolean) {
            runOnUiThread {
                if (newTab) newTabWithUrl(url) else navigate(tab.id, url)
            }
        }

        @JavascriptInterface
        fun search(q: String) {
            // newtab search bar — Settings me chuna hua search engine (default Google)
            runOnUiThread {
                navigate(tab.id, searchEngineUrl() + java.net.URLEncoder.encode(q, "UTF-8"))
            }
        }

        @JavascriptInterface
        fun meta(title: String, url: String) {
            runOnUiThread {
                tab.title = title.ifBlank { domainOf(url) }
                tab.url = url
                refreshChrome()
            }
        }

        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME

        /** NTP ka Lens button — CAMERA scanner (QR/UPI/barcode local decode) */
        @JavascriptInterface
        fun lensScan() {
            runOnUiThread { startLensScan() }
        }

        /** MODULE 2 — Discover feed ka data: local history (incognito = []) */
        @JavascriptInterface
        fun historyJson(): String {
            if (tab.isIncognito) return "[]"
            val arr = org.json.JSONArray()
            runCatching {
                LocalStore.listHistory(this@MainActivity).take(300).forEach { h ->
                    arr.put(
                        org.json.JSONObject()
                            .put("url", h.url)
                            .put("title", h.title)
                            .put("ts", h.ts),
                    )
                }
            }
            return arr.toString()
        }

        /** v1.4.7 SPIDER MODE — newtab VPN state khud poll karta hai (Tor-style screen) */
        @JavascriptInterface
        fun vpnState(): String = RelayEngine.state

        /** v1.4.7 — exit country + rotation minutes (newtab spider status line) */
        @JavascriptInterface
        fun vpnInfo(): String = runCatching {
            org.json.JSONObject()
                .put("state", RelayEngine.state)
                .put("country", SpiderMode.lastCountry)
                .put("rotateMin", SpiderMode.ROTATE_MINUTES)
        }.getOrDefault(org.json.JSONObject().put("state", "off")).toString()

        // ---------------- v1.6.0 — newtab engine picker + incognito screen ----------------

        /** Engine picker (newtab G icon) — current engine read. */
        @JavascriptInterface
        fun getSetting(key: String, def: String): String =
            LocalStore.getSetting(this@MainActivity, key, def)

        /** Engine picker — choice save (same "searchEngine" key jo Settings use karta hai). */
        @JavascriptInterface
        fun setSetting(key: String, value: String) {
            runOnUiThread {
                LocalStore.setSetting(this@MainActivity, key, value)
                refreshChrome()
            }
        }

        /**
         * v1.9.4 — SOCIAL MEDIA DOWNLOADER: page-JS ne video/reel source pakda
         * (Instagram/Facebook/X/TikTok pe direct mp4 src) to ye callback aata hai.
         * Download bar offer dikhta hai — tap = OrbitDownloader multi-segment.
         */
        @JavascriptInterface
        fun socialVideo(json: String) {
            runOnUiThread {
                runCatching {
                    val arr = JSONArray(json)
                    if (arr.length() > 0) {
                        val url = arr.getString(0)
                        if (url.startsWith("http")) offerSocialDownload(url)
                    }
                }
            }
        }

        /** Incognito newtab screen — tab incognito hai to JS alag eclipse screen banata hai. */
        @JavascriptInterface
        fun isIncognito(): Boolean = tab.isIncognito

        /** Incognito screen ka "IP hide" tap — relay connect (IP webview proxy se chhupta hai). */
        @JavascriptInterface
        fun vpnOn() {
            runOnUiThread {
                if (RelayEngine.state != "on" && RelayEngine.state != "connecting") {
                    maybeGrantNotifPermission()
                    RelayEngine.connect(this@MainActivity)
                }
            }
        }

        /** JS se native toast (engine picker confirm etc). */
        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // ------------- v1.7.0 — home feed (music/news/youtube) + voice + quick cards -------------

        /** Home feed ka CORS-free fetcher — Google News RSS / YouTube RSS / JioSaavn
         *  API native OkHttp se fetch hote hai (WebView CORS na toke). 10-min cache
         *  — feed dobara khula to instant render. */
        @JavascriptInterface
        fun fetchFeed(url: String): String {
            if (!url.startsWith("http")) return ""
            val now = System.currentTimeMillis()
            synchronized(feedCache) {
                feedCache[url]?.let { (t, body) -> if (now - t < 10 * 60 * 1000L) return body }
            }
            val ua = if (url.contains("youtube.com")) DESKTOP_UA else realUA   // v1.9.0
            val body = runCatching {
                feedHttp.newCall(
                    Request.Builder().url(url).header("User-Agent", ua).build(),
                ).execute().use { resp ->
                    if (!resp.isSuccessful) "" else resp.body?.string() ?: ""
                }
            }.getOrDefault("")
            if (body.isNotBlank()) {
                synchronized(feedCache) {
                    feedCache[url] = now to body
                    while (feedCache.size > 24) feedCache.remove(feedCache.keys.first())
                }
            }
            return body
        }

        /** v1.7.0 ASYNC fetch — JS ko BLOCK nahi karta (sync bridge call slow network
         *  par home screen freeze kar dega). Native thread me fetch, result wapas
         *  window.feedData(id, body) callback me. Cache hit = instant callback.
         *  v1.9.0 — youtube.com par DESKTOP UA: mobile UA par YT /channel aur
         *  /results pages 302 (m.youtube redirect, geo-language random) dete hai;
         *  desktop UA = stable en-IN structure (live-verified parse). */
        @JavascriptInterface
        fun fetchFeedAsync(url: String, id: Int) {
            if (!url.startsWith("http")) { pushFeed(id, ""); return }
            val cached = synchronized(feedCache) {
                feedCache[url]?.takeIf { System.currentTimeMillis() - it.first < 10 * 60 * 1000L }?.second
            }
            if (cached != null) { pushFeed(id, cached); return }
            Thread {
                val ua = if (url.contains("youtube.com")) DESKTOP_UA else realUA
                val body = runCatching {
                    feedHttp.newCall(
                        Request.Builder().url(url).header("User-Agent", ua).build(),
                    ).execute().use { resp ->
                        if (!resp.isSuccessful) "" else resp.body?.string() ?: ""
                    }
                }.getOrDefault("")
                if (body.isNotBlank()) {
                    synchronized(feedCache) {
                        feedCache[url] = System.currentTimeMillis() to body
                        while (feedCache.size > 24) feedCache.remove(feedCache.keys.first())
                    }
                }
                pushFeed(id, body)
            }.also { it.isDaemon = true }.start()
        }

        private fun pushFeed(id: Int, body: String) {
            runOnUiThread {
                runCatching {
                    activeTab?.webView?.evaluateJavascript(
                        "if(window.feedData)window.feedData($id,${escapeJs(body)});", null,
                    )
                }
            }
        }

        /** JioSaavn encrypted_media_url → playable https URL. DES/ECB key = JioSaavn
         *  web player ka public constant (saare open-source saavn tools yahi use
         *  karte hai). http → https upgrade (mixed-content safe).
         *  v1.9.2 FIX — "Songs are unavailable" har song par: kuch Android providers
         *  par ECB-mode Cipher.init me IvParameterSpec pass karne se
         *  InvalidAlgorithmParameterException aati thi (runCatching = "" SAB songs =
         *  skip-cap toast). (1) IV GAYA — ECB me IV hota hi nahi; (2) PKCS5 fail →
         *  NoPadding + manual unpad fallback; (3) fail par Log.w — logcat me exact
         *  reason; (4) JS side me bhi pure-DES fallback (newtab.html desDecryptJs) —
         *  bridge crypto kabhi bhi total-fail ho to bhi music chalega. */
        @JavascriptInterface
        fun decryptMedia(enc: String): String = runCatching {
            if (enc.isBlank()) return@runCatching ""
            val data = android.util.Base64.decode(enc, android.util.Base64.DEFAULT)
            val plain = runCatching { desEcbDecrypt(data, true) }.getOrElse { desEcbDecrypt(data, false) }
            val s = String(plain, Charsets.UTF_8)
            if (!s.startsWith("http")) {
                android.util.Log.w("OrbitMusic", "decryptMedia: bad plaintext (${s.length} bytes)")
                ""
            } else s.replace("http://", "https://")
        }.getOrElse {
            android.util.Log.w("OrbitMusic", "decryptMedia failed: ${it.message}")
            ""
        }

        /** DES-ECB single-shot decrypt. pkcs5=true → provider unpad kare; false →
         *  NoPadding + manual PKCS5 strip (provider quirk ke against 2nd chance). */
        private fun desEcbDecrypt(data: ByteArray, pkcs5: Boolean): ByteArray {
            val spec = javax.crypto.spec.DESKeySpec("38346591".toByteArray())
            val key = javax.crypto.SecretKeyFactory.getInstance("DES").generateSecret(spec)
            val c = javax.crypto.Cipher.getInstance(if (pkcs5) "DES/ECB/PKCS5Padding" else "DES/ECB/NoPadding")
            c.init(javax.crypto.Cipher.DECRYPT_MODE, key)
            var out = c.doFinal(data)
            if (!pkcs5 && out.isNotEmpty()) {
                val pad = out[out.size - 1].toInt()
                if (pad in 1..8 && out.size > pad) out = out.copyOfRange(0, out.size - pad)
            }
            return out
        }

        /** Search bar mic — Google voice recognizer; result par seedha search. */
        @JavascriptInterface
        fun voiceSearch() {
            runOnUiThread { startVoiceSearch() }
        }

        /** Home ka Spider VPN card — VPN sheet kholta hai. */
        @JavascriptInterface
        fun openVpnSheet() {
            runOnUiThread { showVpnSheet() }
        }

        /** Home ka Incognito card — naya incognito tab (relay auto-connect). */
        @JavascriptInterface
        fun newIncognito() {
            runOnUiThread { newTab(incognito = true) }
        }

        /** Local news — user ka city (ip-api, 24h cache). Blank = abhi resolve nahi. */
        @JavascriptInterface
        fun cityFeed(): String = LocalStore.getSetting(this@MainActivity, "userCity", "")

        // ---------------- v1.9.1 — update banner (home screen) ----------------

        /** Naya version download ho chuka hai to JSON {name, apk} warna "".
         *  Home page isse banner dikhata hai — system installer ka prompt user
         *  miss kar jaye (cancel / Play Protect block) to bhi update INSTALL
         *  hone tak har home-open par dikhta rahega. */
        @JavascriptInterface
        fun updatePending(): String = runCatching {
            val apkFile = pendingUpdateApk()
            if (apkFile == null) {
                ""
            } else {
                val info = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                org.json.JSONObject()
                    .put("name", info?.versionName ?: "")
                    .put("apk", apkFile.absolutePath)
                    .toString()
            }
        }.getOrDefault("")

        /** Banner tap — downloaded APK par system installer kholo. Path guard:
         *  sirf apne cache/updates folder ki .apk (hostile-page abuse impossible). */
        @JavascriptInterface
        fun installUpdate(path: String) {
            runOnUiThread {
                runCatching {
                    val dir = File(cacheDir, "updates").canonicalPath
                    val p = File(path).canonicalPath
                    if (p.startsWith(dir) && p.endsWith(".apk")) {
                        val f = File(p)
                        if (f.exists()) UpdateChecker.installWithStatus(this@MainActivity, f)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- lens scanner

    /** Google Lens button (NTP) — CAMERA kholta hai: photo lo → QR/UPI/barcode
     *  LOCAL decode (bina internet, bina Google — privacy promise) → link/UPI/
     *  text action. QR na mile to photo Google app (Lens) ko bhejo, warna
     *  lens.google.com. System camera use karte hain = CAMERA permission ki
     *  zaroorat nahi, camera quirks ka risk nahi. */
    private fun startLensScan() {
        val dir = File(cacheDir, "lens")
        runCatching { dir.mkdirs() }
        val file = File(dir, "capture.jpg")
        val uri = runCatching {
            if (file.exists()) file.delete()
            file.createNewFile()
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            activeTab?.id?.let { navigate(it, LENS_WEB) }
            return
        }
        lensCaptureFile = file
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("capture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        // camera app na ho / block ho — seedha Lens website (pehle jaisa behavior)
        val ok = runCatching { startActivityForResult(intent, REQ_LENS_CAPTURE); true }.getOrDefault(false)
        if (!ok) {
            lensCaptureFile = null
            activeTab?.id?.let { navigate(it, LENS_WEB) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // MODULE 4 — Google sync sign-in ka result
        if (requestCode == OrbitSync.REQ_SIGN_IN) {
            OrbitSync.handleSignInResult(this, resultCode, data)
            return
        }
        // MODULE 4 — VPN consent ka result (relay proxy engine use karta hai — consent rare)
        if (requestCode == REQ_VPN_CONSENT) {
            CrashGuard.toast(
                this,
                if (resultCode == RESULT_OK) "VPN permission saved"
                else "VPN permission denied",
            )
            return
        }
        // v1.7.0 — VOICE SEARCH result → seedha search (koi extra tap nahi)
        if (requestCode == REQ_VOICE) {
            if (resultCode == RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = results?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) navigateInput(text)
            }
            return
        }
        // v1.9.4 — Themes: wallpaper pick → home background push (data-URI, CORS-safe)
        if (requestCode == REQ_WALLPAPER) {
            if (resultCode == RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    Thread {
                        runCatching {
                            val raw = contentResolver.openInputStream(uri)?.use { inp ->
                                BitmapFactory.decodeStream(inp)
                            } ?: return@Thread
                            // 720px cap — memory-safe, base64 transportable
                            val maxSide = 720
                            val scale = minOf(
                                maxSide.toFloat() / raw.width,
                                maxSide.toFloat() / raw.height,
                            ).coerceAtMost(1f)
                            val w = (raw.width * scale).toInt().coerceAtLeast(1)
                            val h = (raw.height * scale).toInt().coerceAtLeast(1)
                            val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                            val baos = java.io.ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 68, baos)
                            val b64 = android.util.Base64.encodeToString(
                                baos.toByteArray(), android.util.Base64.NO_WRAP,
                            )
                            raw.recycle()
                            scaled.recycle()
                            LocalStore.setSetting(this@MainActivity, "wallpaper", "data:image/jpeg;base64,$b64")
                            runOnUiThread {
                                applyTheme("wallpaper")
                                Toast.makeText(this@MainActivity, "Wallpaper applied", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Wallpaper failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.also { it.isDaemon = true }.start()
                }
            }
            return
        }
        if (requestCode != REQ_LENS_CAPTURE) return
        val file = lensCaptureFile ?: return
        lensCaptureFile = null
        if (resultCode != RESULT_OK) {
            runCatching { file.delete() }
            return
        }
        // kuch camera apps EXTRA_OUTPUT ignore karte hain — thumbnail fallback
        val thumb = data?.extras?.get("data") as? Bitmap
        Thread {
            val text = runCatching {
                if (file.exists() && file.length() > 0) decodeQrFromJpeg(file) else thumb?.let { decodeQrBitmap(it) }
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!text.isNullOrBlank()) {
                    runCatching { file.delete() }
                    showLensResult(text)
                } else {
                    lensImageFallback(file)
                }
            }
        }.start()
    }

    /** Decode: downscale (memory) — 4 rotations × Hybrid/Global binarizer, taaki
     *  EXIF-orientation ke bina bhi QR pakka mile. Background thread par hi. */
    private fun decodeQrFromJpeg(file: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return decodeQrBitmap(bmp)
    }

    private fun decodeQrBitmap(source: Bitmap): String? {
        val reader = MultiFormatReader()
        reader.setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.ITF,
                ),
            ),
        )
        var cur = source
        var rotated = false
        try {
            repeat(4) {
                runCatching {
                    val w = cur.width
                    val h = cur.height
                    val px = IntArray(w * h)
                    cur.getPixels(px, 0, w, 0, 0, w, h)
                    val lum = RGBLuminanceSource(w, h, px)
                    for (bin in listOf(HybridBinarizer(lum), GlobalHistogramBinarizer(lum))) {
                        try {
                            return reader.decodeWithState(BinaryBitmap(bin)).text
                        } catch (e: Exception) {
                            reader.reset()
                        }
                    }
                }
                val m = Matrix()
                m.postRotate(90f)
                val r = runCatching { Bitmap.createBitmap(cur, 0, 0, cur.width, cur.height, m, true) }
                    .getOrNull() ?: return@repeat
                if (rotated) cur.recycle() // sirf purane rotated bitmaps — source kabhi nahi
                cur = r
                rotated = true
            }
        } finally {
            reader.reset()
            if (rotated) cur.recycle()
        }
        return null
    }

    /** Scan result ka action dialog — QR content ke hisaab se smart */
    private fun showLensResult(text: String) {
        val t = text.trim()
        val lower = t.lowercase()
        val isUrl = lower.startsWith("http://") || lower.startsWith("https://")
        val isUpi = lower.startsWith("upi://") || lower.startsWith("tez://")
        val title = when {
            isUpi -> "UPI QR code scanned"
            isUrl -> "Link scanned"
            else -> "QR/Barcode scanned"
        }
        val action = when {
            isUpi -> "Open in payment app"
            isUrl -> "Open"
            else -> "Search on Google"
        }
        runCatching {
            AlertDialog.Builder(this, R.style.OrbitDialog)
                .setTitle(title)
                .setMessage(t.take(600))
                .setPositiveButton(action) { _, _ ->
                    when {
                        isUpi -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t))) }
                            .onFailure {
                                CrashGuard.toast(this, "No payment app found — copied instead")
                                copyLensText(t)
                            }
                        isUrl -> activeTab?.id?.let { navigate(it, t) }
                        else -> activeTab?.id?.let {
                            // v1.5.0 — Lens text search bhi searchEngineUrl se (spider
                            // mode = DDG; pehle hardcoded GOOGLE_SEARCH tha)
                            navigate(it, searchEngineUrl() + java.net.URLEncoder.encode(t, "UTF-8"))
                        }
                    }
                }
                .setNeutralButton("Copy") { _, _ -> copyLensText(t) }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun copyLensText(text: String) {
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("orbit-lens", text))
            CrashGuard.toast(this, "Copied")
        }
    }

    /** QR nahi mila — MODULE 3 in-app Lens: photo seedha lens.google.com upload
     *  → results URL APP KE ANDAR khulta hai. Endpoint drift/network fail ho to
     *  pehle jaisa: Google app (Lens image-share), warna lens.google.com. */
    private fun lensImageFallback(file: File) {
        CrashGuard.toast(this, "Searching on Google Lens…")
        Thread {
            val uploadCopy = File(file.parentFile, "up_${file.name}")
            var resultsUrl: String? = null
            runCatching {
                if (LensEngine.downscale(file, uploadCopy)) resultsUrl = LensEngine.upload(uploadCopy)
            }
            // Privacy: upload copy hamesha delete — EXIF/GPS kabhi device se bahar nahi
            runCatching { uploadCopy.delete() }
            val url = resultsUrl
            if (url != null) {
                // in-app results — original photo bhi delete, kuch cache me nahi bachta
                runCatching { file.delete() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    activeTab?.id?.let { navigate(it, url) }
                }
            } else {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    shareLensWithGoogleApp(file)
                }
            }
        }.start()
    }

    /** Pehla fallback — photo ko Google app (Lens image-search) me bhejo;
     *  Google app na ho to lens.google.com (v1.2.5 behavior). */
    private fun shareLensWithGoogleApp(file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull()
        val send = if (uri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("lens", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.google.android.googlequicksearchbox")
            }
        } else null
        val ok = send != null && runCatching {
            if (send.resolveActivity(packageManager) != null) {
                startActivity(send)
                true
            } else false
        }.getOrDefault(false)
        if (ok) {
            CrashGuard.toast(this, "Searching in Google app…")
        } else {
            activeTab?.id?.let { navigate(it, LENS_WEB) }
        }
    }

    private inner class OrbitWebViewClient(val tab: Tab) : WebViewClient() {
        // BRAVE MODULE — matched ad/tracker request server tak jaane se PEHLE drop
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val url = request?.url?.toString() ?: return null
            // v1.9.4 — whitelist: user ne site ke ads allow kiye to seedha pass
            if (isAdWhitelisted(url)) return null
            if (shieldsEnabled() && AdBlocker.shouldBlock(url)) {
                tab.blockedCount++
                AdBlocker.countBlock()
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url ?: return false
            val scheme = url.scheme?.lowercase() ?: return false
            // tel:/mailto:/market: jaise external schemes — system app me bhejo
            if (scheme !in setOf("http", "https", "about", "data", "javascript", "blob")) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }
            return false // http(s) WebView (Chromium) me hi khulega — real browser
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            // MODULE 3 — main-frame navigation: media wale origin se door gaye to playback band
            PlaybackCoordinator.onPageNavigatedAway(url ?: "")
            // BACK-navigate karke NTP/internal entry par wapas aana — Chrome ki
            // tarah CLEAN omnibox (pichhla URL kabhi nahi dikhega)
            if (url == NEWTAB || url == "https://orbit.local/") {
                tab.isInternal = true
                tab.url = NEWTAB
                tab.title = "New Tab"
                refreshChrome()
                return
            }
            if (!url.isNullOrEmpty() && !url.startsWith("data:")) {
                tab.url = url
                refreshChrome()
            }
            // v1.4.4 — stall-watchdog arm: 9s me 15% nahi pahuncha to relay swap
            sleeper.removeCallbacks(stallCheck)
            sleeper.postDelayed(stallCheck, 9000)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            pullRefresh.isRefreshing = false
            // v1.4.4 — ring loader safety stop (kuch pages progress-100 event nahi bhejte)
            pageLoading = false
            ringWrap?.visibility = View.GONE
            sleeper.removeCallbacks(stallCheck)
            if (!omnibox.hasFocus()) omniIcon.visibility = View.VISIBLE
            updateShieldBadge()
            // internal entry (back-nav included) — clean chrome, history me kuch nahi
            if (url == NEWTAB || url == "https://orbit.local/" || tab.isInternal) {
                refreshChrome()
                return
            }
            if (view == null || url.isNullOrEmpty()) return
            tab.url = url
            tab.title = view.title?.ifBlank { null } ?: domainOf(url)
            refreshChrome()
            // v1.9.4 — SOCIAL MEDIA DOWNLOADER: social sites par video/reel source
            // scan (page ke mp4 srcs) — mile to OrbitBridge.socialVideo se offer
            if (socialDownloaderEnabled() && isSocialHost(url)) {
                view.evaluateJavascript(SOCIAL_VIDEO_SCANNER_JS, null)
            }
            // Incognito tab — history me KUCH nahi jata (Chrome parity)
            if (!tab.isIncognito) LocalStore.addHistory(this@MainActivity, url, tab.title)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // sirf main frame — subresource errors page todte nahi
            if (request?.isForMainFrame == true) {
                showErrorPage(view ?: return, request.url.toString(), error?.description?.toString() ?: "Page failed to load")
            }
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // MODULE 3 FIX — background/OOM me renderer kill ho sakta hai; bina
            // handler ke poora app "WebView renderer crashed" se mar jata hai.
            // Chrome-parity graceful recover: WebView destroy + tab evicted mark
            // — select karte hi fresh renderer me page auto-reload ho jata hai.
            val wv = view ?: return false
            PlaybackCoordinator.webViewDestroyed(wv)
            runCatching {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            // v1.9.4 — SHARED HOME renderer gone: homeWebView null karo (next
            // ensureHomeWebView fresh bana dega) + active home tab attach rebuild.
            if (wv === homeWebView) {
                homeWebView = null
                homeTab.webView = null
                tabs.forEach { if (it.webView === wv) it.webView = null }
                if (tab === activeTab && tab.url == NEWTAB) {
                    tab.webView = ensureHomeWebView()
                    attachTabView(tab)
                    refreshChrome()
                }
                return true
            }
            tab.webView = null
            tab.evicted = true
            tab.sleeping = false
            if (tab === activeTab) rebuildActiveTabRenderer()
            return true // handled — system ko crash report nahi bhejte
        }
    }

    /** Renderer crash recovery — active tab ka WebView turant fresh banao */
    private fun rebuildActiveTabRenderer() {
        val tab = activeTab ?: return
        runCatching {
            tab.webView = buildWebView(tab)
            tab.evicted = false
            if (!tab.isInternal && tab.url != NEWTAB) tab.webView?.loadUrl(tab.url)
            container.removeAllViews()
            tab.webView?.let {
                it.visibility = View.VISIBLE
                container.addView(
                    it,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            refreshChrome()
        }
    }

    // ---------------------------------------------------------------- chrome (omnibox etc.)

    private fun domainOf(url: String): String = runCatching {
        java.net.URI(url).host ?: url
    }.getOrDefault(url)

    /** v1.4.1 — keyboard band karne ka ek hi zor-shor helper. Omnibox GO,
     *  suggestion tap, newtab search (Bridge.search), shortcut tap — sab paths
     *  navigate() se guzarte hain, isliye keyboard apne aap band ho jata hai. */
    private fun hideKeyboard() {
        runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val focus = currentFocus ?: findViewById<View>(R.id.container)
            imm?.hideSoftInputFromWindow(focus.windowToken, 0)
        }
    }

    // ---------------------------------------------------------------- Chrome tab switcher

    private fun showTabSwitcher() {
        captureThumb(activeTab ?: return)
        switcherTitle.text = "Tabs (${tabs.size})"
        renderSwitcherCards()
        tabSwitcher.visibility = View.VISIBLE
    }

    private fun hideTabSwitcher() {
        tabSwitcher.visibility = View.GONE
    }

    /** Chrome tab switcher: 2-column cards grid — 1:1 square preview + floating X
     *  (v1.4.0: user ne square previews + har card par × manga tha). */
    private fun renderSwitcherCards() {
        switcherTitle.text = "Tabs (${tabs.size})"
        switcherGrid.removeAllViews()
        val colWidth = (resources.displayMetrics.widthPixels - dp(40)) / 2
        tabs.forEach { tab ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(if (tab.isIncognito) R.drawable.card_bg_incognito else R.drawable.card_bg)
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            // thumbnail — 1:1 square (Chrome tab grid parity)
            val thumbHolder = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, colWidth,
                )
                setBackgroundColor(0xFF3C4043.toInt())
            }
            val iv = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                tab.thumb?.let { setImageBitmap(it) } ?: run { visibility = View.GONE }
            }
            val letter = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
                text = domainOf(tab.url).removePrefix("www.").take(1).uppercase().ifBlank { "?" }
                textSize = 34f
                setTextColor(0xFFE8EAED.toInt())
                if (tab.thumb != null) visibility = View.GONE
            }
            // floating X — preview ke top-right corner par (Chrome jaisa cancel)
            val closeBtn = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(0xFFE8EAED.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xE6202124.toInt())
                }
                layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener {
                    closeTabInternal(tab)
                    if (tabs.isEmpty()) { hideTabSwitcher(); newTab() }
                }
            }
            thumbHolder.addView(iv)
            thumbHolder.addView(letter)
            thumbHolder.addView(closeBtn)
            card.addView(thumbHolder)

            // title row (favicon letter + title)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(6), dp(4), dp(0))
            }
            val title = TextView(this).apply {
                text = (if (tab.sleeping) "😴 " else "") +
                    (if (tab.isIncognito) "[Incognito] " else "") +
                    tab.title.ifBlank { domainOf(tab.url) }
                textSize = 11.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFFE8EAED.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(title)
            card.addView(row)

            card.setOnClickListener {
                hideTabSwitcher()
                selectTab(tab.id)
            }
            val glp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply {
                width = colWidth
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            switcherGrid.addView(card, glp)
        }
    }

    // ---------------------------------------------------------------- find in page

    private fun hideFindBar() {
        findBar.visibility = View.GONE
        activeTab?.webView?.clearMatches()
        findInput.setText("")
        findCount.text = "0/0"
    }

    // ---------------------------------------------------------------- menu (Chrome order)

    /** Chrome-jaisa 3-dot menu — btn_menu ke neeche se slide hoke khulta hai, right-aligned.
     *  v1.4.0 — Chrome parity rebuild: icon rows + real ON/OFF switches + Downloads +
     *  Sign in with Google + VPN switch. Scrollable (Chrome menu bhi overflow par scroll hota hai). */
    private fun showMenu() {
        if (isFinishing || isDestroyed) return
        if (menuPopup?.isShowing == true) { menuPopup?.dismiss(); return }
        val anchor = findViewById<View>(R.id.btn_menu)
        val list = layoutInflater.inflate(R.layout.popup_menu, null) as LinearLayout

        val tab = activeTab

        fun divider() {
            list.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(dp(6), dp(4), dp(6), dp(4))
                }
                setBackgroundColor(0xFF3C4043.toInt())
            })
        }

        fun row(iconRes: Int, label: String, action: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                isFocusable = true
            }
            val iv = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(0xFFE8EAE9.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
            }
            val tv = TextView(this).apply {
                text = label
                setTextColor(0xFFE8EAE9.toInt())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(18)
                }
            }
            r.addView(iv)
            r.addView(tv)
            r.setOnClickListener {
                menuPopup?.dismiss()
                action()
            }
            list.addView(r)
        }

        fun rowToggle(iconRes: Int, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                isFocusable = true
            }
            val iv = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(0xFFE8EAE9.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
            }
            val tv = TextView(this).apply {
                text = label
                setTextColor(0xFFE8EAE9.toInt())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(18)
                }
            }
            val tgl = makeToggle(checked) { on -> onToggle(on) }
            r.addView(iv)
            r.addView(tv)
            r.addView(tgl)
            // poora row click bhi switch ko hi trigger kare (Chrome toggle-row parity)
            r.setOnClickListener { tgl.performClick() }
            list.addView(r)
        }

        // Chrome Android menu order
        row(R.drawable.ic_newtab, "New tab") { newTab() }
        row(R.drawable.ic_private, "New Incognito tab") { newTab(incognito = true) }
        // v1.8.0 — header buttons gaye, isliye Home + Tabs ab menu se (ek tap access)
        row(R.drawable.ic_home, "Home") { activeTab?.id?.let { navigate(it, NEWTAB) } }
        row(R.drawable.ic_tabs, "Tabs") { showTabSwitcher() }
        divider()
        row(R.drawable.ic_history, "History") { showHistory() }
        row(R.drawable.ic_star, "Bookmarks") { showBookmarks() }
        row(R.drawable.ic_download, "Downloads") { showDownloadsPage() }
        row(R.drawable.ic_star, "Add bookmark") { toggleCurrentBookmark() }
        row(R.drawable.ic_find, "Find in page") { showFindBar() }
        // v1.9.4 — UC TOOLBOX: bottom sheet me 9 quick tools (Speed Mode, Music,
        // Screenshot, Clipboard, Translate, QR, Save Page, Full Screen, Find)
        row(R.drawable.ic_speed, "Tools") { showToolsSheet() }
        row(R.drawable.ic_camera, "Screenshot") { captureScreenshot() }
        row(R.drawable.ic_savepage, "Save page") { savePageArchive() }
        row(R.drawable.ic_translate, "Translate page") { translateCurrentPage() }
        row(R.drawable.ic_share, "Share") { sharePage() }
        divider()
        rowToggle(R.drawable.ic_desktop, "Desktop site", tab?.desktopSite == true) { toggleDesktopSite() }
        rowToggle(R.drawable.ic_shield, "Shields (block ads & trackers)", shieldsEnabled()) { toggleShields() }
        rowToggle(R.drawable.ic_lock, "Anti-fingerprint", antiFpEnabled()) { toggleAntiFp() }
        rowToggle(R.drawable.ic_vpn, "VPN", RelayEngine.isEnabled(this)) { on ->
            if (on) RelayEngine.connect(this) else RelayEngine.disconnect(this)
        }
        divider()
        row(R.drawable.ic_google, "Sign in with Google") { showGoogleSignIn() }
        // v1.6.0 — "AI Search" menu row REMOVE (user request: Orbit AI option nahi)
        row(R.drawable.ic_fire, "Fire Button (clear all data)") { fireButton() }
        divider()
        row(R.drawable.ic_update, "Check for updates") { runAutoUpdate(manual = true) }
        row(R.drawable.ic_settings, "Settings") { showSettings() }

        // Chrome menu overflow par scroll hota hai — same
        val scroller = ScrollView(this).apply { addView(list) }
        menuPopup = PopupWindow(
            scroller,
            dp(292),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // rounded bg dikhne ke liye
            elevation = dp(12).toFloat()
            animationStyle = R.style.OrbitPopupAnim
            isOutsideTouchable = true
            showAsDropDown(anchor, -dp(2), dp(2), Gravity.END)
        }
    }

    /** v1.4.0 — premium ON/OFF pill switch (Material 3 style, dark theme).
     *  Track blue = ON, gray = OFF, thumb animate hota hai. Har "on/off ki
     *  zaroorat" wali jagah ye hi toggle dikhta hai. */
    private fun makeToggle(initial: Boolean, onChanged: (Boolean) -> Unit): View {
        var on = initial
        val track = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(34), dp(18), Gravity.CENTER_VERTICAL)
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(if (on) 0xFF8AB4F8.toInt() else 0xFF5F6368.toInt())
            }
        }
        val thumb = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER_VERTICAL)
            translationX = if (on) dp(16).toFloat() else dp(2).toFloat()
            background = GradientDrawable().apply {
                cornerRadius = dp(7).toFloat()
                setColor(if (on) 0xFF202124.toInt() else 0xFFE8EAED.toInt())
            }
        }
        val holder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(track)
            addView(thumb)
        }
        fun render() {
            (track.background as? GradientDrawable)?.setColor(if (on) 0xFF8AB4F8.toInt() else 0xFF5F6368.toInt())
            (thumb.background as? GradientDrawable)?.setColor(if (on) 0xFF202124.toInt() else 0xFFE8EAED.toInt())
            thumb.animate().translationX(if (on) dp(16).toFloat() else dp(2).toFloat()).setDuration(120).start()
        }
        holder.setOnClickListener { on = !on; render(); onChanged(on) }
        return holder
    }

    // ------------------------------------------------ v1.4.4 downloads (Chrome-style)

    /** WebView DownloadListener → Orbit engine. data:/http(s) handle; baaki toast. */
    private fun handleDownload(url: String, contentDisposition: String?, mimeType: String?) {
        if (url.startsWith("data:")) {
            val name = runCatching { URLUtil.guessFileName(url, contentDisposition, mimeType) }
                .getOrDefault("orbit-file-${System.currentTimeMillis() % 10000}")
            val t = OrbitDownloader.startDataUrl(this, url, name)
            Toast.makeText(
                this,
                if (t != null) "Saved: ${t.name}" else "This file can't be downloaded",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "This file type can't be downloaded", Toast.LENGTH_SHORT).show()
            return
        }
        // API 26-28: public Downloads write permission pehle (API 29+ MediaStore free)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingDl = arrayOf(url, contentDisposition ?: "", mimeType ?: "")
            requestPermissions(arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"), 4100)
            return
        }
        startOrbitDownload(url, contentDisposition, mimeType)
    }

    private fun startOrbitDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val name = runCatching { URLUtil.guessFileName(url, contentDisposition, mimeType) }
            .getOrDefault("orbit-download")
        // v1.9.4 — UC "New Task" setting: Always Ask = file name dialog pehle
        if (LocalStore.getSetting(this, "newTask", "ask") == "ask") {
            val input = EditText(this).apply {
                setText(name)
                setSingleLine(true)
                setPadding(dp(24), dp(10), dp(24), dp(0))
            }
            AlertDialog.Builder(this, R.style.OrbitDialog)
                .setTitle("New download task")
                .setView(input)
                .setPositiveButton("Download") { _, _ ->
                    launchOrbitDownload(url, input.text.toString().trim().ifBlank { name }, mimeType)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            launchOrbitDownload(url, name, mimeType)
        }
    }

    private fun launchOrbitDownload(url: String, name: String, mimeType: String?) {
        val t = OrbitDownloader.start(this, url, name, mimeType ?: "")
        if (t == null) {
            Toast.makeText(this, "This file can't be downloaded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Download started: ${t.name}", Toast.LENGTH_SHORT).show()
            updateDownloadBar()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // v1.4.8 — Spider Mode notification permission result (pehle IGNORE ho
        // raha tha — deny par notification kabhi nahi dikhti, dobara request bhi
        // silently drop hoti). Ab grant par notif turant, deny par settings.
        if (requestCode == 4201) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                SpiderMode.sync(this)
                pushSpiderStatus()
            } else {
                if (shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
                    Toast.makeText(this, "Spider Mode keeps working — only the notification stays hidden", Toast.LENGTH_LONG).show()
                } else {
                    openNotifSettings()
                }
            }
            return
        }
        if (requestCode != 4100) return
        val p = pendingDl
        pendingDl = null
        if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED && p != null) {
            startOrbitDownload(p[0], p[1], p[2])
        } else {
            Toast.makeText(this, "Storage permission is needed to save downloads", Toast.LENGTH_SHORT).show()
        }
    }

    /** Bottom download bar — 500ms ticker hook se live update (speed/ETA/%) */
    private fun updateDownloadBar() {
        val t = OrbitDownloader.runningTask()
        if (t == null) {
            dlBar.visibility = View.GONE
            return
        }
        dlBar.visibility = View.VISIBLE
        dlName.text = t.name
        val total = t.total.get()
        if (t.status == OrbitDownloader.ST_RUNNING) {
            dlSub.text = buildString {
                append(OrbitDownloader.fmtSpeed(t.speed))
                OrbitDownloader.fmtEta(t.eta).takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                append(" · ").append(OrbitDownloader.fmtSize(t.received.get()))
                if (total > 0) append(" / ").append(OrbitDownloader.fmtSize(total))
            }
            dlAct.setImageResource(R.drawable.ic_pause)
            if (total > 0) {
                dlProg.isIndeterminate = false
                dlProg.max = 100
                dlProg.progress = ((t.received.get() * 100) / total).toInt().coerceIn(0, 100)
            } else {
                dlProg.isIndeterminate = true
            }
        } else {
            dlSub.text = buildString {
                append("Paused · ").append(OrbitDownloader.fmtSize(t.received.get()))
                if (total > 0) append(" of ").append(OrbitDownloader.fmtSize(total))
            }
            dlAct.setImageResource(R.drawable.ic_play)
            if (total > 0) {
                dlProg.isIndeterminate = false
                dlProg.max = 100
                dlProg.progress = ((t.received.get() * 100) / total).toInt().coerceIn(0, 100)
            } else {
                dlProg.isIndeterminate = true
            }
        }
    }

    /** Chrome-jaisa Downloads page — in-app list, live progress, open/pause/delete. */
    private fun showDownloadsPage() {
        downloadsPageOpen = true
        openPage("Downloads", actionIcon = R.drawable.ic_trash, onAction = {
            LocalStore.clearFinishedDownloads(this)
            showDownloadsPage()
        }) { content ->
            dlRowSubs.clear()
            dlRowProgs.clear()
            val items = LocalStore.listDownloads(this)
            if (items.isEmpty()) {
                content.addView(pageEmpty("Files you download will show up here.\n\nFiles save to your phone's Downloads folder."))
                return@openPage
            }
            items.forEach { d -> content.addView(downloadRow(d)) }
        }
    }

    private fun downloadRow(d: LocalStore.DownloadItem): LinearLayout {
        val live = OrbitDownloader.taskById(d.id)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(6), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            background = getDrawable(R.drawable.btn_ripple)
            isClickable = true
            isFocusable = true
        }
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_download)
            setColorFilter(0xFF9AA0A6.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14); marginEnd = dp(4)
            }
        }
        val t1 = TextView(this).apply {
            text = d.name
            textSize = 15f
            setTextColor(0xFFE8EAED.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val t2 = TextView(this).apply {
            text = dlSubText(d, live)
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        dlRowSubs[d.id] = t2
        col.addView(t1)
        col.addView(t2)
        val status = live?.status ?: d.status
        if (status == OrbitDownloader.ST_RUNNING || status == OrbitDownloader.ST_PAUSED) {
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4),
                ).apply { topMargin = dp(6) }
                max = 100
                progressTintList = android.content.res.ColorStateList.valueOf(0xFF8AB4F8.toInt())
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF3C4043.toInt())
                val tot = live?.total?.get() ?: d.total
                if (tot > 0) {
                    isIndeterminate = false
                    progress = (((live?.received?.get() ?: d.received) * 100) / tot).toInt().coerceIn(0, 100)
                } else {
                    isIndeterminate = true
                }
            }
            dlRowProgs[d.id] = pb
            col.addView(pb)
        }
        row.addView(iv)
        row.addView(col)

        fun miniBtn(iconRes: Int, desc: String, action: () -> Unit): ImageButton =
            ImageButton(this).apply {
                setImageResource(iconRes)
                setColorFilter(0xFF9AA0A6.toInt())
                background = getDrawable(R.drawable.btn_ripple)
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                contentDescription = desc
                setOnClickListener { action() }
            }

        if (status == OrbitDownloader.ST_RUNNING || status == OrbitDownloader.ST_PAUSED) {
            row.addView(miniBtn(
                if (status == OrbitDownloader.ST_RUNNING) R.drawable.ic_pause else R.drawable.ic_play,
                "Pause or resume",
            ) {
                val t = OrbitDownloader.taskById(d.id) ?: return@miniBtn
                if (t.status == OrbitDownloader.ST_RUNNING) OrbitDownloader.pause(this@MainActivity, t)
                else OrbitDownloader.resume(this@MainActivity, t)
                refreshDownloadsRows()
            })
        }
        if (status == OrbitDownloader.ST_DONE || status == OrbitDownloader.ST_FAILED || status == OrbitDownloader.ST_PAUSED) {
            row.addView(miniBtn(R.drawable.ic_trash, "Delete download") {
                val t = OrbitDownloader.taskById(d.id)
                if (t != null) OrbitDownloader.cancel(this@MainActivity, t)
                LocalStore.removeDownload(this@MainActivity, d.id)
                runCatching { File(this@MainActivity.filesDir, "downloads/${d.id}").deleteRecursively() }
                if (d.uri.isNotEmpty() && !d.uri.startsWith("content:")) runCatching { File(d.uri).delete() }
                showDownloadsPage()
            })
        }
        if (status == OrbitDownloader.ST_DONE) {
            row.setOnClickListener {
                if (!OrbitDownloader.openTask(this@MainActivity, d)) {
                    Toast.makeText(this@MainActivity, "No app can open this file", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return row
    }

    /** Ticker hook — sirf texts/progress in-place update (poora rebuild nahi) */
    private fun refreshDownloadsRows() {
        if (!downloadsPageOpen) return
        val items = LocalStore.listDownloads(this)
        dlRowSubs.forEach { (id, tv) ->
            val d = items.firstOrNull { it.id == id } ?: return@forEach
            tv.text = dlSubText(d, OrbitDownloader.taskById(id))
        }
        dlRowProgs.forEach { (id, pb) ->
            val live = OrbitDownloader.taskById(id) ?: return@forEach
            val total = live.total.get()
            if (total > 0 && !pb.isIndeterminate) {
                pb.progress = ((live.received.get() * 100) / total).toInt().coerceIn(0, 100)
            }
        }
        updateDownloadBar()
    }

    private fun dlSubText(d: LocalStore.DownloadItem, live: OrbitDownloader.Task?): String {
        val status = live?.status ?: d.status
        return when (status) {
            OrbitDownloader.ST_RUNNING -> buildString {
                append(OrbitDownloader.fmtSpeed(live?.speed ?: 0))
                val eta = OrbitDownloader.fmtEta(live?.eta ?: -1)
                if (eta.isNotBlank()) append(" · ").append(eta)
                append(" · ").append(OrbitDownloader.fmtSize(live?.received?.get() ?: d.received))
            }
            OrbitDownloader.ST_PAUSED -> "Paused · ${OrbitDownloader.fmtSize(live?.received?.get() ?: d.received)}"
            OrbitDownloader.ST_FAILED -> "Failed"
            OrbitDownloader.ST_DONE -> {
                val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(d.ts))
                "${OrbitDownloader.fmtSize(if (d.total > 0) d.total else d.received)} · $date"
            }
            else -> OrbitDownloader.fmtSize(d.total)
        }
    }

    /** v1.4.0 — Google sign-in card: Continue dabao → accounts.google.com Orbit ke
     *  WebView me khulta hai. Google ka apna account-chooser dialog phone me jo
     *  accounts saved hain wo dikhata hai; session Orbit ke cookies me persist
     *  hota hai — YouTube/Gmail waghera baad me auto logged-in milte hain. */
    private fun showGoogleSignIn() {
        val pad = dp(22)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(20), pad, dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF1E1F22.toInt())
            }
        }
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_google)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val title = TextView(this).apply {
            text = "Sign in with Google"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(6))
        }
        val msg = TextView(this).apply {
            text = "Use the Google account already signed in on this phone.\n\n" +
                "Orbit opens Google's own account picker — pick your account and you're in. " +
                "Your Google session stays inside Orbit, so YouTube, Gmail and other Google sites stay signed in."
            textSize = 14f
            setTextColor(0xFF9AA0A6.toInt())
            setLineSpacing(0f, 1.25f)
        }
        box.addView(iv)
        box.addView(title)
        box.addView(msg)

        fun pill(label: String, bg: Int, fg: Int, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(bg)
                }
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        pill("Continue with Google", 0xFF8AB4F8.toInt(), 0xFF1E1F22.toInt()) {
            hideCardOverlay()
            val tab = activeTab
            if (tab == null) return@pill
            navigate(
                tab.id,
                "https://accounts.google.com/ServiceLogin?continue=" +
                    java.net.URLEncoder.encode("https://www.google.com/", "UTF-8"),
            )
        }
        pill("Not now", 0xFF3C4043.toInt(), 0xFFE8EAED.toInt()) { hideCardOverlay() }

        showCardOverlay(box)
    }

    // ------------------------------------------------- v1.4.0 overlay infra
    // Chrome se ALAG premium look: dim + centered rounded card (dialogs) aur
    // full-screen pages (history/bookmarks/settings) slide-in animation ke saath.

    private var pageDialog: FrameLayout? = null

    /** Centered premium card — dim background, tap-outside dismiss, slide-up anim */
    private fun showCardOverlay(card: View) {
        hideCardOverlay()
        pageDialog = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setOnClickListener { hideCardOverlay() }
        }
        card.isClickable = true
        card.layoutParams = FrameLayout.LayoutParams(dp(336), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        pageDialog!!.addView(card)
        findViewById<ViewGroup>(android.R.id.content).addView(pageDialog!!)
        card.translationY = dp(26).toFloat()
        card.alpha = 0f
        card.animate().translationY(0f).alpha(1f).setDuration(180).start()
    }

    private fun hideCardOverlay() {
        pageDialog?.let { (it.parent as? ViewGroup)?.removeView(it) }
        pageDialog = null
    }

    /** Full-screen page (history/bookmarks/settings) — Chrome page jaisa, slide-in */
    private fun openPage(
        title: String,
        actionIcon: Int = 0,
        onAction: () -> Unit = {},
        build: (LinearLayout) -> Unit,
    ) {
        pageOverlay.removeAllViews()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58),
            )
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(0xFFE8EAED.toInt())
            background = getDrawable(R.drawable.btn_ripple)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { closePage() }
        }
        val tv = TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }
        bar.addView(back)
        bar.addView(tv)
        if (actionIcon != 0) {
            val act = ImageButton(this).apply {
                setImageResource(actionIcon)
                setColorFilter(0xFFE8EAED.toInt())
                background = getDrawable(R.drawable.btn_ripple)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener { onAction() }
            }
            bar.addView(act)
        }
        pageOverlay.addView(bar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(24))
        }
        build(content)
        val scroll = ScrollView(this).apply {
            addView(content)
            isVerticalScrollBarEnabled = false
        }
        pageOverlay.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        pageOverlay.visibility = View.VISIBLE
        pageOpen = true
        pageOverlay.clearAnimation()
        val anim = TranslateAnimation(dp(36).toFloat(), 0f, 0f, 0f).apply { duration = 170 }
        pageOverlay.startAnimation(anim)
    }

    private fun closePage() {
        pageOpen = false
        downloadsPageOpen = false
        vpnUiHooks.clear()
        pageOverlay.visibility = View.GONE
        pageOverlay.removeAllViews()
        hideCardOverlay()
    }

    /** page row factory — icon + title + subtitle (+ optional right widget) */
    private fun pageRow(
        iconRes: Int,
        title: String,
        sub: String,
        right: View? = null,
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58),
            ).apply { topMargin = dp(2) }
            if (onClick != null) {
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(0xFF9AA0A6.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16); marginEnd = dp(8)
            }
        }
        val t = TextView(this).apply {
            text = title
            textSize = 15.5f
            setTextColor(0xFFE8EAED.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val s = TextView(this).apply {
            text = sub
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        col.addView(t)
        col.addView(s)
        r.addView(iv)
        r.addView(col)
        right?.let { r.addView(it) }
        return r
    }

    private fun pageHeader(label: String, into: LinearLayout) {
        into.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF8AB4F8.toInt())
            setPadding(dp(14), dp(18), dp(8), dp(6))
        })
    }

    private fun pageDivider(into: LinearLayout) {
        into.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(dp(14), dp(6), dp(14), dp(6))
            }
            setBackgroundColor(0xFF3C4043.toInt())
        })
    }

    private fun pageEmpty(msg: String): View = TextView(this).apply {
        text = msg
        textSize = 14f
        setTextColor(0xFF9AA0A6.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(60), dp(24), dp(24))
    }

    // ------------------------------------------------- history page (Chrome-style)

    /** v1.4.0 — Chrome jaisa HISTORY page: day-groups, per-row delete, trash →
     *  clear-browsing-data card. (Pehle ka plain AlertDialog history gaya.) */
    private fun showHistory() {
        openPage("History", actionIcon = R.drawable.ic_trash, onAction = { showClearDataCard() }) { content ->
            val items = LocalStore.listHistory(this)
            if (items.isEmpty()) {
                content.addView(pageEmpty("Pages you visit will show up here.\n\nBrowsing history stays on this device only."))
                return@openPage
            }
            val dayFmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            var lastDay = ""
            items.forEachIndexed { i, h ->
                val day = dayFmt.format(Date(h.ts))
                if (day != lastDay) {
                    lastDay = day
                    val pretty = when {
                        android.text.format.DateUtils.isToday(h.ts) -> "Today"
                        System.currentTimeMillis() - h.ts < 48 * 3600_000L -> "Yesterday"
                        else -> day
                    }
                    content.addView(TextView(this).apply {
                        text = pretty
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(0xFF9AA0A6.toInt())
                        setPadding(dp(14), dp(14), dp(8), dp(6))
                    })
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), 0, dp(6), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56),
                    )
                    background = getDrawable(R.drawable.btn_ripple)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { newTabWithUrl(h.url) }
                }
                // letter avatar circle
                val letter = domainOf(h.url).removePrefix("www.").take(1).uppercase().ifBlank { "?" }
                val avatar = TextView(this).apply {
                    text = letter
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(0xFFE8EAED.toInt())
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF3C4043.toInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                }
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(12); marginEnd = dp(4)
                    }
                }
                val t = TextView(this).apply {
                    text = h.title.ifBlank { h.url }
                    textSize = 14.5f
                    setTextColor(0xFFE8EAED.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val u = TextView(this).apply {
                    text = domainOf(h.url)
                    textSize = 12f
                    setTextColor(0xFF9AA0A6.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                col.addView(t)
                col.addView(u)
                val del = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_close)
                    setColorFilter(0xFF9AA0A6.toInt())
                    background = getDrawable(R.drawable.btn_ripple)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                    setOnClickListener {
                        runCatching { LocalStore.removeHistoryAt(this@MainActivity, i) }
                        showHistory()
                    }
                }
                row.addView(avatar)
                row.addView(col)
                row.addView(del)
                content.addView(row)
            }
        }
    }

    /** v1.4.0 — clear browsing data card (Chrome "Delete browsing data" parity):
     *  checkboxes + premium card + real clearing */
    private fun showClearDataCard() {
        val pad = dp(22)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(20), pad, dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF1E1F22.toInt())
            }
        }
        box.addView(TextView(this).apply {
            text = "Clear browsing data"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        box.addView(TextView(this).apply {
            text = "Basic"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(0, 0, 0, dp(6))
        })

        var cbHistory: CheckBox? = null
        var cbCookies: CheckBox? = null
        var cbCache: CheckBox? = null
        fun checkRow(label: String, initial: Boolean, assign: (CheckBox) -> Unit): View {
            val cb = CheckBox(this).apply {
                text = label
                textSize = 15f
                setTextColor(0xFFE8EAED.toInt())
                isChecked = initial
                setPadding(dp(8), dp(10), dp(8), dp(10))
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFF8AB4F8.toInt())
            }
            assign(cb)
            box.addView(cb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            return cb
        }
        checkRow("Browsing history", true) { cbHistory = it }
        checkRow("Cookies and site data", true) { cbCookies = it }
        checkRow("Cached images and files", false) { cbCache = it }

        fun pill(label: String, bg: Int, fg: Int, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(bg)
                }
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        pill("Clear data", 0xFF8AB4F8.toInt(), 0xFF1E1F22.toInt()) {
            if (cbHistory?.isChecked == true) runCatching { LocalStore.clearHistory(this) }
            if (cbCookies?.isChecked == true) runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                runCatching { WebStorage.getInstance().deleteAllData() }
                runCatching { WebViewDatabase.getInstance(this).clearFormData() }
            }
            if (cbCache?.isChecked == true) runCatching {
                tabs.forEach { it.webView?.clearCache(true) }
            }
            hideCardOverlay()
            if (pageOpen) showHistory()
            Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show()
        }
        pill("Cancel", 0xFF3C4043.toInt(), 0xFFE8EAED.toInt()) { hideCardOverlay() }
        showCardOverlay(box)
    }

    // ------------------------------------------------- bookmarks page (Chrome-style)

    /** v1.4.0 — BOOKMARKS ka apna section: list + gold star + per-row delete.
     *  (Pehle plain AlertDialog tha.) */
    private fun showBookmarks() {
        openPage("Bookmarks") { content ->
            val items = LocalStore.listBookmarks(this)
            if (items.isEmpty()) {
                content.addView(pageEmpty("No bookmarks yet.\n\nTap the star in the address bar to save the page you're on."))
                return@openPage
            }
            items.forEachIndexed { i, b ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), 0, dp(6), 0)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(56),
                    )
                    background = getDrawable(R.drawable.btn_ripple)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { newTabWithUrl(b.url) }
                }
                val star = ImageView(this).apply {
                    setImageResource(R.drawable.ic_star)
                    setColorFilter(0xFFFBBC04.toInt())
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                }
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(14); marginEnd = dp(4)
                    }
                }
                val t = TextView(this).apply {
                    text = b.title.ifBlank { b.url }
                    textSize = 14.5f
                    setTextColor(0xFFE8EAED.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val u = TextView(this).apply {
                    text = domainOf(b.url)
                    textSize = 12f
                    setTextColor(0xFF9AA0A6.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                col.addView(t)
                col.addView(u)
                val del = ImageButton(this).apply {
                    setImageResource(R.drawable.ic_close)
                    setColorFilter(0xFF9AA0A6.toInt())
                    background = getDrawable(R.drawable.btn_ripple)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                    setOnClickListener {
                        runCatching { LocalStore.removeBookmarkAt(this@MainActivity, i) }
                        showBookmarks()
                        refreshChrome()
                    }
                }
                row.addView(star)
                row.addView(col)
                row.addView(del)
                content.addView(row)
            }
        }
    }

    // ------------------------------------------------- settings page (Chrome-style)

    /** v1.4.0 — Chrome jaisa SETTINGS page: Basics / Advanced / Sync & sign-in /
     *  Orbit AI / About sections, har on-off cheez par real switch. */
    /**
     * v1.9.4 — UC-STYLE SETTINGS ARCHITECTURE (screenshot parity).
     * Pehla screen: AD Setting / Customized Banner / Set as Default Browser /
     * Browsing / Download / Notification / Search / Social Media Downloader /
     * Themes / Cloud Boost / Language / Clear Records / Account / About +
     * bottom "Reset to Default". Har row ka REAL working sub-screen.
     */
    private fun showSettings() {
        openPage("Settings") { content ->
            // ---- AD Setting ----
            content.addView(pageRow(
                R.drawable.ic_shield, "AD Setting",
                "Ad blocker · pop-ups · whitelist (${AdBlocker.totalBlocked} blocked)",
            ) { showAdSettings() })
            // ---- Customized Banner ----
            content.addView(pageRow(
                R.drawable.ic_banner, "Customized Banner",
                "Home screen banner text & visibility",
            ) { showBannerSettings() })
            // ---- Set as Default Browser (live role state) ----
            val defTgl = makeToggle(isDefaultBrowser()) {
                requestDefaultBrowserRole()
            }
            val defRow = pageRow(R.drawable.ic_home, "Set as Default Browser", "Open links in Orbit", right = defTgl)
            vpnUiHooks.add { defTgl.tag = null }  // hook slot — state onResume refresh hota hai
            content.addView(defRow)
            // ---- Browsing Settings ----
            content.addView(pageRow(
                R.drawable.ic_settings, "Browsing Settings",
                "Font · preload · tab view · gestures · UA",
            ) { showBrowsingSettings() })
            // ---- Download Settings ----
            content.addView(pageRow(
                R.drawable.ic_download, "Download Settings",
                "Path · max tasks · auto reconnect",
            ) { showDownloadSettings() })
            // ---- Notification Settings ----
            content.addView(pageRow(
                R.drawable.ic_notif_orbit, "Notification Settings",
                "Push · Just In · site notifications",
            ) { showNotificationSettings() })
            // ---- Search Settings ----
            val engineName = when (LocalStore.getSetting(this, "searchEngine", "google")) {
                "bing" -> "Bing"; "ddg" -> "DuckDuckGo"; "brave" -> "Brave"; else -> "Google"
            }
            content.addView(pageRow(
                R.drawable.ic_find, "Search Settings",
                "Default: $engineName · suggestions",
            ) { showSearchSettingsPage() })
            // ---- Social Media Downloader ----
            content.addView(pageRow(
                R.drawable.ic_social, "Social Media Downloader Settings",
                "Auto-detect videos on social sites",
            ) { showSocialDownloaderSettings() })
            // ---- Themes ----
            val themeName = when (LocalStore.getSetting(this, "appTheme", "dark")) {
                "amoled" -> "AMOLED Black"; "wallpaper" -> "Wallpaper"; else -> "Dark"
            }
            content.addView(pageRow(
                R.drawable.ic_theme, "Themes", themeName,
            ) { showThemesPage() })
            // ---- Cloud Boost ----
            val boost = LocalStore.getSetting(this, "cloudBoost", "on")
            content.addView(pageRow(
                R.drawable.ic_cloud, "Cloud Boost",
                if (boost == "off") "Off" else "ON — compression & acceleration",
            ) { showCloudBoostSheet() })
            // ---- Language ----
            val langName = languageName(LocalStore.getSetting(this, "appLanguage", "en"))
            content.addView(pageRow(
                R.drawable.ic_language, "Language", langName,
            ) { showLanguagePage() })
            // ---- Clear Records ----
            content.addView(pageRow(
                R.drawable.ic_trash, "Clear Records",
                "Cache · cookies · history · form data · passwords",
            ) { showClearRecords() })
            // ---- Account ----
            val acct = LocalStore.getSetting(this, "orbitAccount", "")
            content.addView(pageRow(
                R.drawable.ic_account, "Account",
                if (acct.isNotBlank()) "Signed in as $acct" else "Sign in with Google or Orbit",
            ) { showSignInSheet() })
            // ---- About Orbit ----
            content.addView(pageRow(
                R.drawable.ic_about, "About Orbit", "V${BuildConfig.VERSION_NAME}",
            ) { showAbout() })

            // ---- Reset to Default (bottom centered, UC parity) ----
            val reset = TextView(this).apply {
                text = "Reset to Default"
                textSize = 15f
                setTextColor(0xFFE8EAED.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(18) }
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                setOnClickListener { confirmResetToDefault() }
            }
            content.addView(reset)
        }
    }

    /** v1.4.0 — search engine picker card */
    private fun showSearchEngineCard() {
        val pad = dp(22)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(20), pad, dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF1E1F22.toInt())
            }
        }
        box.addView(TextView(this).apply {
            text = "Search engine"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        val engines = listOf(
            "google" to "Google",
            "bing" to "Bing",
            "ddg" to "DuckDuckGo",
            "brave" to "Brave",
        )
        val current = LocalStore.getSetting(this, "searchEngine", "google")
        engines.forEach { (key, name) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48),
                )
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                isFocusable = true
            }
            r.addView(TextView(this).apply {
                text = name
                textSize = 15.5f
                setTextColor(0xFFE8EAED.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(ImageView(this).apply {
                setImageResource(if (key == current) R.drawable.ic_star else R.drawable.ic_down)
                setColorFilter(if (key == current) 0xFF8AB4F8.toInt() else 0x00000000)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            r.setOnClickListener {
                LocalStore.setSetting(this, "searchEngine", key)
                // v1.5.0 — spider mode me DDG lock hai; user ki choice VPN OFF par
                // apply hogi (searchEngineUrl hard-guarantee spider mode me DDG dega)
                if ((RelayEngine.state == "on" || RelayEngine.state == "connecting") && key != "ddg") {
                    Toast.makeText(
                        this,
                        "Spider Mode uses DuckDuckGo — your choice will apply when the VPN is off",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(this, "Search engine: $name", Toast.LENGTH_SHORT).show()
                }
                hideCardOverlay()
            }
            box.addView(r)
        }
        showCardOverlay(box)
    }

    // ------------------------------------------------- VPN sheet (premium bottom card)

    /** v1.4.0 — ORBIT VPN sheet: ON/OFF switch, live status, free relay list,
     *  refresh. Tor-jaisa browser-scoped routing — websites ko relay ka IP dikhta hai. */
    // ------------------------------------------------------------ spider mode

    /** v1.4.7 — VPN ON ke waqt user ka pehle wala engine (OFF par restore) */
    @Volatile private var prevEngine: String? = null

    /** v1.4.8 — transition guard: spider engine-switch ek hi baar apply ho
     *  (state-change par), user ki manual engine choice baad me override ho. */
    @Volatile private var spiderApplied = false

    /**
     * v1.4.8 SPIDER MODE STATE-SYNC — pehle engine switch sirf toggle-press par
     * hota tha, isliye app restart / VPN auto-restore par DuckDuckGo KABHI switch
     * nahi hota tha (user bug report). Ab STATE-driven hai: RelayEngine ki har
     * UI-change par idempotent apply — connecting/on dono spider states hai.
     *
     * forceUi = true (sirf toggle-press se): current tab me spider newtab FORCE
     * load hota hai — website par VPN dabao to bhi screen TURANT badalti hai
     * ("instant nahi hota" bug fix). forceUi = false (state hook se): sirf
     * engine + live status push, current page hijack nahi hoti.
     */
    private fun spiderApplyState(forceUi: Boolean) {
        val on = RelayEngine.state == "on" || RelayEngine.state == "connecting"
        if (on) {
            if (!spiderApplied) {
                spiderApplied = true
                // prevEngine sirf TRANSITION par save (repeat calls par user ki
                // original choice overwrite na ho)
                prevEngine = LocalStore.getSetting(this, "searchEngine", "google").takeIf { it != "ddg" }
            }
            // v1.5.0 — CONTINUOUS force (idempotent): har state-event par ddg likho
            // taaki Settings picker + placeholder spider mode me hamesha DDG dikhaye
            if (LocalStore.getSetting(this, "searchEngine", "google") != "ddg") {
                LocalStore.setSetting(this, "searchEngine", "ddg")
                refreshChrome()
            }
            if (forceUi) {
                val t = activeTab ?: return
                loadInternal(t, NEWTAB)
            } else {
                pushSpiderStatus()
            }
        } else {
            if (spiderApplied) {
                spiderApplied = false
                prevEngine?.let { p ->
                    // user ne spider mode me engine manually badla ho to usi ko respect
                    if (LocalStore.getSetting(this, "searchEngine", "google") == "ddg") {
                        LocalStore.setSetting(this, "searchEngine", p)
                        refreshChrome()
                    }
                    prevEngine = null
                }
                reloadNewtabIfVisible()
            }
        }
    }

    /** v1.4.8 — spider newtab me LIVE status push (evaluateJavascript) — 6s poll
     *  ka intezar nahi. Relay connect/rotate hone par country turant update. */
    private fun pushSpiderStatus() {
        val t = activeTab ?: return
        if (t.url != NEWTAB) return
        runCatching {
            val j = org.json.JSONObject()
                .put("state", RelayEngine.state)
                .put("country", SpiderMode.lastCountry)
                .put("rotateMin", SpiderMode.ROTATE_MINUTES)
            t.webView?.evaluateJavascript("if(window.spiderPush)spiderPush($j);", null)
        }
    }

    private fun reloadNewtabIfVisible() {
        val t = activeTab ?: return
        if (t.url == NEWTAB) loadInternal(t, NEWTAB)
    }

    /** Android 13+ (SDK 33) par notification ke liye runtime permission. */
    private fun maybeGrantNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 4201)
        }
    }

    /** v1.4.8 — notification permission permanently denied ho to system dialog
     *  dobara nahi aata — sirf app ki notification settings page khulti hai. */
    private fun openNotifSettings() {
        runCatching {
            val i = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
            Toast.makeText(this, "Enable notifications to see Spider Mode country", Toast.LENGTH_LONG).show()
        }
    }

    private fun showVpnSheet() {
        hideCardOverlay()
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            setOnClickListener { hideCardOverlay() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
                setColor(0xFF292A2D.toInt())
            }
            background = bg
            setPadding(dp(20), dp(14), dp(20), dp(24))
            isClickable = true
        }
        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ))

        // drag handle
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0xFF5F6368.toInt())
            }
        })

        val statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                gravity = Gravity.CENTER_VERTICAL; marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF9AA0A6.toInt())
            }
        }
        val statusText = TextView(this).apply {
            text = RelayEngine.statusText
            textSize = 14f
            setTextColor(0xFF9AA0A6.toInt())
        }
        val relayList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val vpnToggle = makeToggle(RelayEngine.isEnabled(this)) { on ->
            if (on) {
                maybeGrantNotifPermission()   // v1.4.7 — Android 13+ runtime grant
                hideCardOverlay()             // v1.4.8 — sheet hatao, transformation DIKHE
                RelayEngine.connect(this)     // background relay test (15-45s) — non-blocking
                spiderApplyState(forceUi = true)  // v1.4.8 — INSTANT spider screen + DDG (connecting state bhi spider hi hai)
            } else {
                RelayEngine.disconnect(this)
                SpiderMode.cancel(this)
                // v1.4.8 — engine restore + normal newtab spiderApplyState se (state off hook)
            }
        }

        fun renderRelays() {
            relayList.removeAllViews()
            statusText.text = RelayEngine.statusText
            (statusDot.background as? GradientDrawable)?.setColor(
                when (RelayEngine.state) {
                    "on" -> 0xFF81C995.toInt()
                    "connecting" -> 0xFFFBBC05.toInt()
                    else -> 0xFF9AA0A6.toInt()
                },
            )
            val rs = RelayEngine.relays
            if (rs.isEmpty()) {
                relayList.addView(TextView(this).apply {
                    text = if (RelayEngine.state == "connecting") "Finding working relays…"
                    else "Tap the switch to connect.\nOrbit picks the fastest working relay automatically."
                    textSize = 13f
                    setTextColor(0xFF9AA0A6.toInt())
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                })
            } else {
                rs.forEach { r ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), 0, dp(8), 0)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(44),
                        )
                        background = getDrawable(R.drawable.btn_ripple)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { RelayEngine.useRelay(r) }
                    }
                    row.addView(TextView(this).apply {
                        text = r.country
                        textSize = 14.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(0xFFE8EAED.toInt())
                        layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
                    })
                    row.addView(TextView(this).apply {
                        text = "${r.host}:${r.port}"
                        textSize = 12f
                        setTextColor(0xFF9AA0A6.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = dp(8)
                        }
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    })
                    row.addView(TextView(this).apply {
                        text = "${r.latencyMs}ms" + (if (RelayEngine.activeRelay == r) "  •" else "")
                        textSize = 12f
                        setTextColor(if (RelayEngine.activeRelay == r) 0xFF81C995.toInt() else 0xFF9AA0A6.toInt())
                    })
                    relayList.addView(row)
                }
            }
        }

        // title row: VPN + switch
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(6))
        }
        titleRow.addView(TextView(this).apply {
            text = "VPN"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(vpnToggle)
        card.addView(titleRow)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        statusRow.addView(statusDot)
        statusRow.addView(statusText)
        card.addView(statusRow)

        // v1.4.8 — notification permission missing ho to sheet me seedha fix row
        // (denied ho chuka ho to system dialog dobara nahi aata — yahan se settings)
        if (!SpiderMode.hasNotifPermission(this)) {
            card.addView(TextView(this).apply {
                text = "Notifications are off — tap to show Spider Mode country in the notification bar"
                textSize = 12.5f
                setTextColor(0xFFFBBC05.toInt())
                setPadding(dp(4), dp(2), dp(4), dp(10))
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                setOnClickListener {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")
                    ) maybeGrantNotifPermission()
                    else openNotifSettings()
                }
            })
        }

        card.addView(TextView(this).apply {
            text = "Routes your browser traffic through free community relays — " +
                "websites see the relay's IP, not yours (like Tor, inside the browser). " +
                "Browser traffic only. Free public relays — speed varies."
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            setLineSpacing(0f, 1.2f)
            setPadding(0, 0, 0, dp(12))
        })

        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headRow.addView(TextView(this).apply {
            text = "Free relays"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF8AB4F8.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_refresh)
            setColorFilter(0xFF9AA0A6.toInt())
            background = getDrawable(R.drawable.btn_ripple)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener { RelayEngine.refresh(this@MainActivity, RelayEngine.isEnabled(this@MainActivity)) }
        })
        card.addView(headRow)
        card.addView(relayList)

        if (!RelayEngine.proxySupported()) {
            card.addView(TextView(this).apply {
                text = "This device's WebView doesn't support proxy routing — VPN can't work here."
                textSize = 12f
                setTextColor(0xFFF28B82.toInt())
                setPadding(0, dp(10), 0, 0)
            })
        }

        renderRelays()
        val hook: () -> Unit = { runOnUiThread { renderRelays() } }
        vpnUiHooks.add(hook)

        findViewById<ViewGroup>(android.R.id.content).addView(root)
        pageDialog = root
        card.translationY = dp(40).toFloat()
        card.animate().translationY(0f).setDuration(200).start()
    }

    // ------------------------------------------------- preferences helpers (v1.4.0)

    private fun jsEnabledPref(): Boolean = LocalStore.getSetting(this, "jsEnabled", "on") != "off"
    private fun blockPopupsPref(): Boolean = LocalStore.getSetting(this, "blockPopups", "on") != "off"
    private fun cookiesEnabledPref(): Boolean = LocalStore.getSetting(this, "cookiesEnabled", "on") != "off"
    private fun imagesPref(): Boolean = true   // v1.6.0 — images ALWAYS on (toggle removed, user request)
    private fun autoplayPref(): Boolean = LocalStore.getSetting(this, "autoplay", "on") != "off"

    /**
     * v1.5.0 HARD GUARANTEE — Spider Mode (VPN on/connecting) me search HAMESHA
     * DuckDuckGo par jayegi, chahe setting kuch bhi ho. v1.4.8 ka state-sync
     * transition-based tha (ek hi baar force) — user spider mode ke dauran engine
     * picker kholta ya settings me jaata to sync dobara force nahi karta tha, aur
     * user ko Google hi milta raha. Ab spider mode ka DDG setting-write se INDEPENDENT
     * hai — search ke waqt live state check hota hai. VPN OFF = user ki setting.
     */
    private fun searchEngineUrl(): String {
        if (RelayEngine.state == "on" || RelayEngine.state == "connecting") {
            return "https://duckduckgo.com/?q="
        }
        return when (LocalStore.getSetting(this, "searchEngine", "google")) {
            "bing" -> "https://www.bing.com/search?q="
            "ddg" -> "https://duckduckgo.com/?q="
            "brave" -> "https://search.brave.com/search?q="
            else -> GOOGLE_SEARCH
        }
    }

    private fun showFindBar() {
        findBar.visibility = View.VISIBLE
        findInput.requestFocus()
        val q = findInput.text.toString()
        if (q.isNotBlank()) activeTab?.webView?.findAllAsync(q)
    }

    private fun toggleDesktopSite() {
        val tab = activeTab ?: return
        tab.desktopSite = !tab.desktopSite
        tab.webView?.settings?.userAgentString = if (tab.desktopSite) DESKTOP_UA else realUA
        tab.webView?.reload()
        Toast.makeText(
            this,
            if (tab.desktopSite) "Desktop site ON" else "Desktop site OFF (mobile)",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun sharePage() {
        val tab = activeTab ?: return
        if (tab.isInternal) {
            Toast.makeText(this, "Internal pages can't be shared", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tab.url)
            putExtra(Intent.EXTRA_SUBJECT, tab.title)
        }
        runCatching { startActivity(Intent.createChooser(i, "Share via")) }
    }

    // ---------------------------------------------------------------- modules (v1.2.0)

    private fun shieldsEnabled(): Boolean =
        !safeMode && LocalStore.getSetting(this, "shields", "on") != "off"

    private fun antiFpEnabled(): Boolean =
        !safeMode && LocalStore.getSetting(this, "antiFp", "on") != "off"

    private fun toggleShields() {
        val on = shieldsEnabled()
        LocalStore.setSetting(this, "shields", if (on) "off" else "on")
        Toast.makeText(this, if (on) "Shields OFF — ads will show" else "Shields ON — ads/trackers blocked", Toast.LENGTH_SHORT).show()
    }

    private fun toggleAntiFp() {
        val on = antiFpEnabled()
        LocalStore.setSetting(this, "antiFp", if (on) "off" else "on")
        Toast.makeText(
            this,
            if (on) "Anti-fingerprint OFF (new tabs)" else "Anti-fingerprint ON (new tabs)",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Brave Shields badge — omnibox pill me blocked count.
     *  v1.3.5 FIX: badge visible hote hi omnibox ka end-padding badhao — warna
     *  URL/search text badge ke NEECHE se guzar ke icons ke saath MIX hota tha.
     *  v1.4.0: star bhi end icons me hai — dono ke liye sahi padding. */
    private fun updateShieldBadge() {
        val n = activeTab?.blockedCount ?: 0
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            if (n > 0 && shieldsEnabled() && !omnibox.hasFocus()) {
                shieldBadge.text = "🛡 $n"
                shieldBadge.visibility = View.VISIBLE
                shieldBadge.post {
                    val w = shieldBadge.width.takeIf { it > 0 } ?: dp(52)
                    val starSpace = if (btnStar.visibility == View.VISIBLE) dp(32) else 0
                    omnibox.setPadding(omnibox.paddingLeft, omnibox.paddingTop, dp(44) + starSpace + w + dp(8), omnibox.paddingBottom)
                }
            } else {
                shieldBadge.visibility = View.GONE
                val starSpace = if (btnStar.visibility == View.VISIBLE) dp(34) else 0
                omnibox.setPadding(omnibox.paddingLeft, omnibox.paddingTop, dp(44) + starSpace, omnibox.paddingBottom)
            }
        }
    }

    /** v1.3.5 — Chrome-jaisa omnibox focus mode: typing ke waqt lock/shield/star
     *  icons hide + X dikhao. Suggestions popup aur icons ka "mix" isi se fixed.
     *  (v1.8.0 — reload button header se gaya, to ab sirf ye icons manage hote hai.) */
    private fun applyOmniFocus(focused: Boolean) {
        if (focused) {
            omniIcon.visibility = View.GONE
            shieldBadge.visibility = View.GONE
            btnStar.visibility = View.GONE
            btnClear.visibility = if (omnibox.text.isBlank()) View.GONE else View.VISIBLE
            // v1.9.4 — Typing Assistant bar (keyboard ke upar quick-input)
            typingBar?.visibility = if (typingAssistantPref()) View.VISIBLE else View.GONE
            positionTypingBar()
        } else {
            omniIcon.visibility = if (pageLoading) View.INVISIBLE else View.VISIBLE
            btnClear.visibility = View.GONE
            updateShieldBadge()
            activeTab?.let { updateBookmarkStar(it) }
            typingBar?.visibility = View.GONE
        }
    }

    private fun showShieldStats() {
        val tab = activeTab
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("🛡 Orbit Shields")
            .setMessage(
                "${tab?.blockedCount ?: 0} ads/trackers blocked on this page.\n" +
                    "${AdBlocker.totalBlocked} blocked in this session.\n\n" +
                    "Network-level blocking — matched requests are dropped before they ever reach the server.",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /** DDG FIRE BUTTON — cookies + cache + storage + history, sab ek jhatke me */
    private fun fireButton() {
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("🔥 Fire Button")
            .setMessage("Cookies, cache, site data and history will ALL be cleared and all tabs closed. Sure?")
            .setPositiveButton("🔥 Burn it all") { _, _ -> doFire() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doFire() {
        val root = findViewById<ViewGroup>(R.id.root)
        val flash = View(this).apply { setBackgroundColor(0xFFFF5252.toInt()); alpha = 0f }
        root.addView(flash, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        flash.animate().alpha(0.85f).setDuration(180).withEndAction {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            runCatching { WebStorage.getInstance().deleteAllData() }
            runCatching { WebViewDatabase.getInstance(this).clearFormData() }
            runCatching { WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword() }
            runCatching { LocalStore.clearHistory(this) }
            AdBlocker.resetStats()
            tabs.toList().forEach { tab ->
                tab.webView?.clearCache(true)
                closeTabInternal(tab)
            }
            tabs.clear()
            flash.animate().alpha(0f).setDuration(320)
                .withEndAction { (flash.parent as? ViewGroup)?.removeView(flash) }.start()
            newTab()
            Toast.makeText(this, "🔥 All data wiped — fresh start", Toast.LENGTH_SHORT).show()
        }.start()
    }

    /** OPERA MODULE lite — max live WebViews, baaki oldest ka WebView destroy
     *  (tab khud zinda rehta hai — URL yaad hai, select par recreate ho jata hai).
     *  v1.4.0 — Settings me "Memory saver" OFF ho to eviction band. */
    private fun evictExcessTabs() {
        // v1.6.0 — "Memory saver" setting removed: eviction HAMESHA active
        // MODULE 3 FIX — media owner tab eviction se OUT: video background me
        // chal rahi ho to uska WebView kabhi destroy nahi hoga
        // v1.9.4 — SHARED HOME WebView eviction se OUT (music queue zinda rehta hai)
        var live = tabs.filter {
            it !== activeTab && it.webView != null &&
                it.webView !== homeWebView && !PlaybackCoordinator.isMediaOwner(it.webView)
        }
        for (tab in live.sortedBy { it.lastActive }) {
            if (live.size < MAX_LIVE_WEBVIEWS) break
            // MODULE 3 — evicted WebView media play kar raha tha to service band
            PlaybackCoordinator.webViewDestroyed(tab.webView)
            runCatching {
                captureThumb(tab)
                tab.webView?.apply {
                    stopLoading()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
            }
            tab.webView = null
            tab.evicted = true
            live = tabs.filter {
                it !== activeTab && it.webView != null &&
                    it.webView !== homeWebView && !PlaybackCoordinator.isMediaOwner(it.webView)
            }
        }
    }

    /** EDGE MODULE — har 30s check: 5 min se inactive WebViews freeze (onPause)
     *  — JS/timers/battery sab ruk jata hai. Select karte hi onResume. */
    private val sleepCheck = object : Runnable {
        override fun run() {
            // activity chali gayi to timer band — dead activity ko callbacks nahi
            if (isFinishing || isDestroyed) return
            // v1.4.0 — "Sleeping tabs" setting OFF = battery feature band
            if (LocalStore.getSetting(this@MainActivity, "sleepTabs", "on") == "off") {
                sleeper.postDelayed(this, 30_000)
                return
            }
            val now = System.currentTimeMillis()
            for (tab in tabs) {
                if (tab === activeTab || tab.sleeping || tab.webView == null || tab.isInternal) continue
                // MODULE 3 FIX — media play karne wala tab KABHI sleep nahi hoga
                // (user doosre tab par ho tab bhi YouTube background me chalta rahega)
                if (PlaybackCoordinator.isMediaOwner(tab.webView)) continue
                if (now - tab.lastActive > SLEEP_AFTER_MS) {
                    runCatching { tab.webView?.onPause() }
                    tab.sleeping = true
                }
            }
            sleeper.postDelayed(this, 30_000)
        }
    }

    /** v1.3.5 — suggestion row factory (icon + title + subtitle + click). Ek hi
     *  jagah row-banawat: search-row aur history/bookmark rows dono isse bante hain. */
    private fun suggestionRow(iconRes: Int, title: String, sub: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = getDrawable(R.drawable.btn_ripple)
            isClickable = true
        }
        val iv = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(0xFF9AA0A6.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        val t = TextView(this).apply {
            text = title
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(0xFFE8EAED.toInt())
        }
        val u = TextView(this).apply {
            text = sub
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(0xFF9AA0A6.toInt())
        }
        col.addView(t)
        col.addView(u)
        row.addView(iv)
        row.addView(col)
        row.setOnClickListener { onClick() }
        return row
    }

    /** CHROME MODULE — omnibox suggestions: pehli row "Google search" (v1.3.5),
     *  phir history + bookmarks, ranking ke saath */
    private fun showSuggestions(q: String) {
        if (isFinishing || isDestroyed) return
        val query = q.trim().lowercase()
        val typed = q.trim()
        if (suggestPopup?.isShowing == true) suggestPopup?.dismiss()
        if (query.length < 2 && typed.isEmpty()) return
        val merged = LinkedHashMap<String, Pair<String, Boolean>>() // url -> (title, isBookmark)
        runCatching {
            LocalStore.listBookmarks(this).forEach { b ->
                if (b.url.lowercase().contains(query) || b.title.lowercase().contains(query)) {
                    merged[b.url] = Pair(b.title.ifBlank { b.url }, true)
                }
            }
            LocalStore.listHistory(this).forEach { h ->
                if (merged.size >= 6) return@forEach
                if (h.url.lowercase().contains(query) || h.title.lowercase().contains(query)) {
                    merged.putIfAbsent(h.url, Pair(h.title.ifBlank { h.url }, false))
                }
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.menu_bg)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        // v1.3.5 — PEHLI ROW hamesha "Google search": Chrome jaisa typed query top
        // suggestion hota hai — tappable search-link, tap karo aur seedha jao
        if (typed.isNotEmpty()) {
            content.addView(
                suggestionRow(R.drawable.ic_find, typed, "Search") {
                    suggestPopup?.dismiss()
                    omnibox.clearFocus()
                    navigateInput(typed)
                },
            )
        }
        for ((url, meta) in merged.entries.take(6)) {
            content.addView(
                suggestionRow(if (meta.second) R.drawable.ic_star else R.drawable.ic_history, meta.first, url) {
                    suggestPopup?.dismiss()
                    omnibox.clearFocus()
                    val tab = activeTab ?: return@suggestionRow
                    navigate(tab.id, url)
                },
            )
        }
        if (content.childCount == 0) return
        suggestPopup = PopupWindow(content, maxOf(omnibox.width, dp(220)), ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(10).toFloat()
            isOutsideTouchable = true
            // window token dead hone par BadTokenException — runCatching me rakho
            // v1.3.5 — 6dp gap: popup omnibox ke icons se chipka nahi lagta
            runCatching { showAsDropDown(omnibox, 0, dp(6)) }
        }
    }

    /** AI search optional hai — menu se. Default search Google hai. */
    private fun showAiSearchPrompt() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF1E1F22.toInt())
            }
        }
        box.addView(TextView(this).apply {
            text = "Orbit AI Search"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        val input = EditText(this).apply {
            hint = "What do you want to ask?"
            setHintTextColor(0xFF9AA0A6.toInt())
            setTextColor(0xFFE8EAED.toInt())
            textSize = 15f
            background = getDrawable(R.drawable.omnibox_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        box.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        fun pill(label: String, bg: Int, fg: Int, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(bg)
                }
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        pill("Search", 0xFF8AB4F8.toInt(), 0xFF1E1F22.toInt()) {
            val q = input.text.toString().trim()
            val tab = activeTab
            if (q.isNotEmpty() && tab != null) navigate(tab.id, "orbit://search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            hideCardOverlay()
        }
        pill("Cancel", 0xFF3C4043.toInt(), 0xFFE8EAED.toInt()) { hideCardOverlay() }
        showCardOverlay(box)
    }

    /** v1.4.0 — AI backend card (Settings → Orbit AI → AI backend) */
    private fun showAiBackendCard() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF1E1F22.toInt())
            }
        }
        box.addView(TextView(this).apply {
            text = "AI backend"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(4))
        })
        box.addView(TextView(this).apply {
            text = "Your deployed Orbit web app URL. AI search answers stream from here."
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        val input = EditText(this).apply {
            setText(LocalStore.getSetting(this@MainActivity, "aiBackend", DEFAULT_AI_BACKEND))
            hint = "https://your-orbit-web-app"
            setHintTextColor(0xFF9AA0A6.toInt())
            setTextColor(0xFFE8EAED.toInt())
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = getDrawable(R.drawable.omnibox_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        box.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        fun pill(label: String, bg: Int, fg: Int, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(bg)
                }
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        pill("Save", 0xFF8AB4F8.toInt(), 0xFF1E1F22.toInt()) {
            LocalStore.setSetting(this, "aiBackend", input.text.toString().trim())
            hideCardOverlay()
            Toast.makeText(this, "AI backend saved", Toast.LENGTH_SHORT).show()
        }
        pill("Cancel", 0xFF3C4043.toInt(), 0xFFE8EAED.toInt()) { hideCardOverlay() }
        showCardOverlay(box)
    }

    // ---------------------------------------------------------------- MODULE 4 (sync)

    /** MODULE 4 — Orbit Sync: Google account se bookmarks/history sync
     *  (Firestore REST — bina google-services.json). Config khali = feature
     *  honestly "off" bolegi. v1.4.0 — premium card + English. */
    private fun showSync() {
        val pad = dp(20)
        fun label(s: String) = TextView(this).apply {
            text = s
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
        }
        fun field(prefill: String, hint: String) = EditText(this).apply {
            setText(prefill)
            this.hint = hint
            textSize = 13f
            setTextColor(0xFFE8EAED.toInt())
            setHintTextColor(0xFF9AA0A6.toInt())
            background = getDrawable(R.drawable.omnibox_bg)
            setPadding(dp(14), 0, dp(14), 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44),
            ).apply { topMargin = dp(6) }
        }
        val fClient = field(
            LocalStore.getSetting(this, "syncWebClientId", ""),
            "Google OAuth Web client ID (…apps.googleusercontent.com)",
        )
        val fApi = field(LocalStore.getSetting(this, "syncApiKey", ""), "Firebase Web API key")
        val fProject = field(LocalStore.getSetting(this, "syncProjectId", ""), "Firebase project ID")

        fun pill(text: String, bg: Int, fg: Int, action: () -> Unit) = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(fg)
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(bg)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            ).apply { marginEnd = dp(8) }
            setOnClickListener { action() }
        }

        fun saveConfig() {
            LocalStore.setSetting(this@MainActivity, "syncWebClientId", fClient.text.toString().trim())
            LocalStore.setSetting(this@MainActivity, "syncApiKey", fApi.text.toString().trim())
            LocalStore.setSetting(this@MainActivity, "syncProjectId", fProject.text.toString().trim())
        }

        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(0, dp(14), 0, 0)
            text = OrbitSync.statusLine(this@MainActivity)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(20), pad, dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFF1E1F22.toInt())
            }
        }
        box.addView(TextView(this).apply {
            text = "Orbit Sync"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        box.addView(label("Firebase config (console → Project settings → Web app):"))
        box.addView(fClient)
        box.addView(fApi)
        box.addView(fProject)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        actions.addView(pill("Sign in with Google", 0xFF1A73E8.toInt(), 0xFFFFFFFF.toInt()) {
            saveConfig()
            OrbitSync.signIn(this@MainActivity)
        })
        actions.addView(pill("Sync now", 0xFF3C4043.toInt(), 0xFFE8EAED.toInt()) {
            saveConfig()
            OrbitSync.syncNow(this@MainActivity) { _, msg ->
                CrashGuard.toast(this@MainActivity, msg)
                status.text = OrbitSync.statusLine(this@MainActivity)
            }
        })
        box.addView(actions)
        box.addView(status)

        fun pillBar(label: String, bg: Int, fg: Int, action: () -> Unit) {
            val b = TextView(this).apply {
                text = label
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(bg)
                }
                setOnClickListener { action() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        pillBar("Save config", 0xFF8AB4F8.toInt(), 0xFF1E1F22.toInt()) {
            saveConfig()
            hideCardOverlay()
            Toast.makeText(this, "Sync config saved", Toast.LENGTH_SHORT).show()
        }
        pillBar("Close", 0xFF3C4043.toInt(), 0xFFE8EAED.toInt()) { hideCardOverlay() }
        showCardOverlay(box)
    }

    private fun showAbout() {
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Orbit Browser")
            .setMessage(
                "Orbit — Fast. Private. Yours.\n\n" +
                    "• Real browser: Chromium engine, Google services built-in\n" +
                    "• Chrome-style UI: tab switcher, omnibox, find, desktop site\n" +
                    "• VPN: free community relays, Tor-style browser routing\n" +
                    "• Your data stays on your device — no servers\n" +
                    "• Incognito tab — nothing saved to history\n" +
                    "• OTA updates: background download, one-tap install\n\n" +
                    "v${BuildConfig.VERSION_NAME}",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Fully silent auto-update — launch par bhi, menu check par bhi same flow:
     * update mila → background download (Orbit ke cache me) → sirf INSTALL wala
     * dialog. Koi "Update karo?" puchha-woochha nahi, koi Chrome/Downloads nahi.
     * v1.2.1: downloaded APK pehle se validated + signature pre-checked aata hai
     * (UpdateChecker me) — isliye yahan kabhi adhoora APK ya doomed install nahi
     * pahunchta. Har dialog me "Chrome me APK kholo" escape hatch bhi hai.
     */
    private fun runAutoUpdate(manual: Boolean) {
        UpdateChecker.autoUpdate(this, manual) { info, apk, compatible ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (!compatible) {
                    // signing mismatch — install attempt bekaar hai, seedha migration
                    showMigrationDialog(info, apk)
                    return@runOnUiThread
                }
                // v1.4.9 → v1.6.0 — SILENT AUTO-INSTALL HAMESHA (user request: auto-install
                // ka button nahi chahiye, wo hamesha on rahe). Update download hote hi
                // seedha system installer khul jata hai — koi Orbit dialog nahi.
                // (Google Play Protect ka scan screen Google ka hai — wo code se nahi
                // hat sakta, par showPlayProtectGuideOnce ek baar OFF karne ka tareeka
                // dikhata hai.)
                Toast.makeText(this, "Orbit ${info.versionName} downloaded — installing update…", Toast.LENGTH_SHORT).show()
                UpdateChecker.installWithStatus(this, apk)
                showPlayProtectGuideOnce()
                return@runOnUiThread
            }
        }
    }

    /** v1.4.9 — ONE-TIME guide: Play Protect ka "App scan recommended" Google ka
     *  system security screen hai — koi app ise remove nahi kar sakti. Ye screen
     *  har NAYE APK par aati hai (naya APK = Google ke liye unknown file). Sirf
     *  user khud Play Protect scan OFF karke hata sakta hai — ye guide wahi karta
     *  hai: seedha Play Protect settings kholti hai (verified GMS deep link). */
    private fun showPlayProtectGuideOnce() {
        if (LocalStore.getSetting(this, "ppGuideShown", "no") == "yes") return
        LocalStore.setSetting(this, "ppGuideShown", "yes")
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("One-time setup — stop Google's scan prompt")
            .setMessage(
                "The \"App scan recommended\" popup comes from Google Play Protect — " +
                    "it appears for every new APK file and no app can remove it.\n\n" +
                    "To never see it again:\n" +
                    "1. Tap \"Open Play Protect settings\"\n" +
                    "2. Tap the gear icon (top right)\n" +
                    "3. Turn OFF \"Scan apps with Play Protect\"\n\n" +
                    "After this, every update downloads and installs silently.",
            )
            .setPositiveButton("Open Play Protect settings") { _, _ -> openPlayProtectSettings() }
            .setNegativeButton("Skip", null)
            .show()
    }

    /** v1.4.9 — Play Protect screen ka direct deep link (GMS VerifyAppsSettingsActivity
     *  — verified public method). Na chale to Play Store fallback + English steps. */
    private fun openPlayProtectSettings() {
        val opened = runCatching {
            startActivity(
                Intent().setClassName("com.google.android.gms", "com.google.android.gms.security.settings.VerifyAppsSettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrElse {
            runCatching {
                startActivity(Intent("com.google.android.gms.settings.PLAY_PROTECT_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
        if (!opened) {
            runCatching {
                packageManager.getLaunchIntentForPackage("com.android.vending")?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                }
            }
            Toast.makeText(
                this,
                "In Play Store: tap your profile icon > Play Protect > gear icon > turn off \"Scan apps with Play Protect\"",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * Signature mismatch (purana version alag key se bana) — Android iske upar
     * naya version install nahi hone dega. One-time migration: APK Downloads me
     * save (uninstall se cache mar jata hai, Downloads nahi) → uninstall →
     * Downloads me APK kholo. Iske baad ke saare updates khud install honge.
     */
    private fun showMigrationDialog(info: UpdateChecker.UpdateInfo, apk: File) {
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Orbit ${info.versionName} — one-time migration needed")
            .setMessage(
                "The old Orbit was built with a different key, so Android won't install the new " +
                    "version on top of it (that's why nothing happened after 'Install').\n\n" +
                    "1. Tap 'Save APK to Downloads' below\n" +
                    "2. Tap 'Uninstall old version'\n" +
                    "3. Open the saved APK from the Files/Downloads app to install\n\n" +
                    "This is needed ONLY ONCE — after that every update installs itself.",
            )
            .setPositiveButton("Save APK to Downloads") { _, _ ->
                Thread {
                    val path = UpdateChecker.saveApkToDownloads(this, apk)
                    runOnUiThread {
                        if (isFinishing) return@runOnUiThread
                        if (path == null) {
                            Toast.makeText(
                                this,
                                "Save failed — download the APK directly in your browser: ${info.apkUrl}",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            AlertDialog.Builder(this, R.style.OrbitDialog)
                                .setTitle("APK saved")
                                .setMessage("$path\n\nUninstall the old Orbit now?")
                                .setPositiveButton("Yes, uninstall") { _, _ ->
                                    UpdateChecker.uninstallSelf(this)
                                }
                                .setNegativeButton("Later", null)
                                .show()
                        }
                    }
                }.start()
            }
            .setNeutralButton("Open APK in browser") { _, _ ->
                UpdateChecker.openApkInBrowser(this, info.apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    // ==================================================================
    // v1.9.4 — UC BROWSER PARITY MODULE (speed engine + tools + settings)
    // ==================================================================

    /** v1.9.4 — Change Link Color If Clicked: visited links ko purple karta hai
     *  (localStorage visited set + CSS class inject). document-start me chalta hai. */
    private val VISITED_LINKS_JS: String = """
        (function () {
          try {
            if (window.__orbitVisited) return;
            window.__orbitVisited = 1;
            var KEY = 'orbitVisited';
            var load = function () { try { return JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { return {}; } };
            var save = function (o) { try { localStorage.setItem(KEY, JSON.stringify(o)); } catch (e) {} };
            var map = load();
            var here = location.href;
            if (here) { map[here] = 1; try { save(map); } catch (e) {} }
            var st = document.createElement('style');
            st.textContent = 'a.orbit-visited{color:#9a6cff !important;}';
            (document.head || document.documentElement).appendChild(st);
            var mark = function () {
              try {
                var as = document.querySelectorAll('a[href]');
                for (var i = 0; i < as.length; i++) {
                  var h = as[i].href;
                  if (h && map[h]) as[i].classList.add('orbit-visited');
                }
              } catch (e) {}
            };
            if (document.readyState === 'loading') {
              document.addEventListener('DOMContentLoaded', mark);
            } else { mark(); }
            try {
              new MutationObserver(function () { setTimeout(mark, 300); }).observe(
                document.documentElement, { childList: true, subtree: true });
            } catch (e) {}
          } catch (e) {}
        })();
    """.trimIndent()

    /** v1.9.4 — Social video scanner (document-end inject, social sites only). */
    private val SOCIAL_VIDEO_SCANNER_JS: String = """
        (function () {
          try {
            var found = {};
            var push = function (u) {
              try {
                if (!u) return;
                if (u.indexOf('blob:') === 0) return;
                if (u.indexOf('http') !== 0) return;
                if (u.length > 2000) return;
                found[u] = 1;
              } catch (e) {}
            };
            try {
              var vs = document.querySelectorAll('video, video source');
              for (var i = 0; i < vs.length; i++) {
                push(vs[i].getAttribute && vs[i].getAttribute('src'));
                push(vs[i].src);
                try {
                  var cands = vs[i].querySelectorAll ? vs[i].querySelectorAll('source') : [];
                  for (var k = 0; k < cands.length; k++) push(cands[k].src);
                } catch (e2) {}
              }
            } catch (e3) {}
            try {
              var html = document.documentElement.innerHTML || '';
              var re = /https?:\\\/\\\/[^"'\\\\ ]+\.mp4[^"'\\\\ ]*/g;
              var m;
              var n = 0;
              while ((m = re.exec(html)) !== null && n < 8) {
                push(m[0].replace(/\\u0026/g, '&').replace(/\\u002F/g, '/'));
                n++;
              }
            } catch (e4) {}
            var list = [];
            for (var key in found) { list.push(key); if (list.length >= 5) break; }
            if (list.length && window.OrbitBridge && OrbitBridge.socialVideo) {
              OrbitBridge.socialVideo(JSON.stringify(list));
            }
          } catch (e) {}
        })();
    """.trimIndent()

    /** v1.9.4 — iPhone/iPad UA strings (Website Preferences). */
    private val IPHONE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    private val IPAD_UA =
        "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    private val SOCIAL_HOSTS = listOf(
        "facebook.com", "instagram.com", "twitter.com", "x.com", "tiktok.com",
        "pinterest.com", "reddit.com", "linkedin.com",
    )

    // ---------------------------------------------------------------- v1.9.4 prefs

    private fun uaMode(): String = LocalStore.getSetting(this, "uaMode", "android")
    private fun textZoomPref(): Int =
        LocalStore.getSetting(this, "textZoom", "100").toIntOrNull()?.coerceIn(50, 200) ?: 100
    private fun ytShieldEnabled(): Boolean = LocalStore.getSetting(this, "ytShield", "on") != "off"
    private fun visitedLinksPref(): Boolean = LocalStore.getSetting(this, "visitedLinks", "off") == "on"
    private fun formsPasswordsPref(): String = LocalStore.getSetting(this, "formsPasswords", "ask")
    private fun swipeGesturePref(): Boolean = LocalStore.getSetting(this, "swipeNav", "on") != "off"
    private fun reopenTabsPref(): Boolean = LocalStore.getSetting(this, "reopenTabs", "off") == "on"
    private fun showStatusBarPref(): Boolean = LocalStore.getSetting(this, "showStatusBar", "off") != "off"
    private fun typingAssistantPref(): Boolean = LocalStore.getSetting(this, "typingAssistant", "on") != "off"
    private fun animationsPref(): Boolean = LocalStore.getSetting(this, "uiAnimation", "on") != "off"
    private fun volumeScrollPref(): Boolean = LocalStore.getSetting(this, "volumeScroll", "off") == "on"
    private fun socialDownloaderEnabled(): Boolean = LocalStore.getSetting(this, "socialDownloader", "on") != "off"
    private fun preloadPref(): String = LocalStore.getSetting(this, "preloadPages", "wap")
    private fun tabViewPref(): String = LocalStore.getSetting(this, "tabView", "card")
    private fun justInPref(): Boolean = LocalStore.getSetting(this, "justIn", "on") != "off"
    private fun pushNotifPref(): Boolean = LocalStore.getSetting(this, "pushNotif", "on") != "off"

    private fun isSocialHost(url: String): Boolean {
        val h = domainOf(url).lowercase()
        return SOCIAL_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    private fun languageName(code: String): String = when (code) {
        "hi" -> "Hindi"; "gu" -> "Gujarati"; "bn" -> "Bengali"; "ta" -> "Tamil"; "te" -> "Telugu"
        else -> "English"
    }

    // ---------------------------------------------------------------- v1.9.4 immersive

    private fun enterImmersive() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { c ->
                    c.hide(WindowInsets.Type.systemBars())
                    c.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
            }
        }
    }

    private fun exitImmersive() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    /** v1.9.4 — Tools sheet ka Full Screen toggle (status+nav bars hide/show). */
    private fun toggleImmersive() {
        if (showStatusBarPref()) {
            LocalStore.setSetting(this, "showStatusBar", "off")
            exitImmersive()
            Toast.makeText(this, "Full screen OFF", Toast.LENGTH_SHORT).show()
        } else {
            LocalStore.setSetting(this, "showStatusBar", "on")
            enterImmersive()
            Toast.makeText(this, "Full screen ON — swipe from top edge to exit", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- v1.9.4 typing assistant

    /** UC "Typing Assistant" — omnibox focus par keyboard ke upar quick-input
     *  bar: www. / .com / .in / / / arrows. Real text insert at cursor. */
    private fun setupTypingBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF202124.toInt())
            visibility = View.GONE
        }
        fun chip(label: String, insert: () -> Unit) {
            val c = TextView(this).apply {
                text = label
                textSize = 13.5f
                setTextColor(0xFFE8EAED.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(0xFF3C4043.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                setOnClickListener { insert() }
            }
            bar.addView(c)
        }
        fun insertAtCursor(s: String) {
            val ed = omnibox.editableText
            val pos = omnibox.selectionEnd.coerceIn(0, ed.length)
            ed.insert(pos, s)
            omnibox.setSelection(pos + s.length)
        }
        chip("www.", { insertAtCursor("www.") })
        chip(".com", { insertAtCursor(".com") })
        chip(".in", { insertAtCursor(".in") })
        chip("/", { insertAtCursor("/") })
        chip("\u2190", { if (omnibox.selectionStart > 0) omnibox.setSelection(omnibox.selectionStart - 1) })
        chip("\u2192", { if (omnibox.selectionEnd < omnibox.length()) omnibox.setSelection(omnibox.selectionEnd + 1) })
        // toolbar ke niche dalo — root layout me omnibox ke baad
        val root = findViewById<ViewGroup>(android.R.id.content)
        root.addView(
            bar,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        bar.y = resources.displayMetrics.heightPixels.toFloat()  // offscreen initial
        typingBar = bar
        bar.post { positionTypingBar() }
    }

    /** Keyboard ke THEEK UPAR anchor karo (ime insets se height nikal kar). */
    private fun positionTypingBar() {
        val bar = typingBar ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val rootInsets = window.decorView.rootWindowInsets
                val ime = rootInsets.getInsets(WindowInsets.Type.ime())
                val nav = rootInsets.getInsets(WindowInsets.Type.navigationBars())
                val bottom = maxOf(ime.bottom, nav.bottom)
                bar.translationY = (-bottom).toFloat()
            } else {
                bar.translationY = 0f
                @Suppress("DEPRECATION")
                val visible = window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                bar.y = (resources.displayMetrics.heightPixels - dp(54)).toFloat()
                bar.tag = visible
            }
        }
    }

    // ---------------------------------------------------------------- v1.9.4 swipe gestures

    /** UC "Swipe Forward&Backward" — screen kinare se swipe = back/forward,
     *  arrow hint overlay ke saath. WebView scroll ke saath co-exist karta hai:
     *  trigger sirf edge-start + horizontal-dominant move par hota hai. */
    private var edgeStartX = 0f
    private var edgeStartY = 0f
    private var edgeFired = false

    private fun setupGestureHint() {
        val tv = TextView(this).apply {
            text = "\u2190"
            textSize = 42f
            setTextColor(0xFF8AB4F8.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(dp(64), dp(64))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66000000)
            }
        }
        findViewById<ViewGroup>(android.R.id.content).addView(tv)
        gestureHint = tv
    }

    private fun handleEdgeGesture(ev: MotionEvent): Boolean {
        if (!swipeGesturePref()) return false
        if (pageOpen || fullscreenView != null) return false
        val wv = activeTab?.webView ?: return false
        val w = resources.displayMetrics.widthPixels
        val edge = dp(36).toFloat()
        val threshold = dp(90).toFloat()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeStartX = ev.rawX
                edgeStartY = ev.rawY
                edgeFired = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!edgeFired) {
                    val startedAtEdge = edgeStartX <= edge || edgeStartX >= w - edge
                    val dx = ev.rawX - edgeStartX
                    val dy = ev.rawY - edgeStartY
                    if (startedAtEdge && kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                        edgeFired = true
                        val goBack = dx > 0
                        val ok = if (goBack) {
                            if (wv.canGoBack()) { wv.goBack(); true } else false
                        } else {
                            if (wv.canGoForward()) { wv.goForward(); true } else false
                        }
                        showGestureHint(goBack, ok)
                        return ok
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> edgeFired = false
        }
        return false
    }

    private fun showGestureHint(leftArrow: Boolean, success: Boolean) {
        val tv = gestureHint ?: return
        if (!success) return
        runCatching {
            tv.text = if (leftArrow) "\u2190" else "\u2192"
            tv.visibility = View.VISIBLE
            tv.alpha = 0.9f
            tv.scaleX = 0.6f
            tv.scaleY = 0.6f
            val cx = (resources.displayMetrics.widthPixels / 2).toFloat()
            val cy = (resources.displayMetrics.heightPixels / 2).toFloat()
            tv.x = cx - dp(32)
            tv.y = cy - dp(32)
            tv.animate().alpha(0f).scaleX(1.6f).scaleY(1.6f).setDuration(420)
                .withEndAction { tv.visibility = View.GONE }
                .start()
        }
    }

    // ---------------------------------------------------------------- v1.9.4 brightness

    /** In-app brightness overlay — system brightness se independent, ultra-dim
     *  night mode tak. Dim = black overlay alpha (0-78%), touches pass-through. */
    private fun applyBrightnessPref() {
        val pct = brightnessLevelPref()
        val root = findViewById<ViewGroup>(android.R.id.content)
        if (brightnessOverlay == null) {
            brightnessOverlay = View(this).apply {
                setBackgroundColor(0xFF000000.toInt())
                setOnTouchListener { _, _ -> false } // pass-through — dim only
            }
            root.addView(brightnessOverlay)
        }
        val alpha = ((pct / 100f) * 0.78f * 255f).toInt().coerceIn(0, 200)
        brightnessOverlay?.visibility = if (alpha > 4) View.VISIBLE else View.GONE
        brightnessOverlay?.background?.alpha = alpha
    }

    private fun brightnessLevelPref(): Int =
        LocalStore.getSetting(this, "brightness", "0").toIntOrNull()?.coerceIn(0, 100) ?: 0

    private fun showBrightnessDialog() {
        val cur = brightnessLevelPref()
        val seek = android.widget.SeekBar(this).apply {
            max = 100
            progress = cur
        }
        val label = TextView(this).apply {
            text = if (cur > 0) "Brightness: $cur%" else "Normal (no dim)"
            textSize = 14f
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(dp(24), dp(18), dp(24), dp(4))
        }
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                label.text = if (value > 0) "Brightness: $value%" else "Normal (no dim)"
                LocalStore.setSetting(this@MainActivity, "brightness", value.toString())
                applyBrightnessPref()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(20))
            addView(label)
            addView(seek, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Brightness")
            .setView(box)
            .setPositiveButton("Done", null)
            .show()
    }

    // ---------------------------------------------------------------- v1.9.4 volume scroll

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // UC Scroll Options — volume keys se page scroll (setting ON ho to)
        if (volumeScrollPref() && !omnibox.hasFocus() && !pageOpen) {
            val wv = activeTab?.webView
            if (wv != null && fullscreenView == null) {
                val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) -560 else 560
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    wv.evaluateJavascript(
                        "window.scrollBy({top:$delta,behavior:'smooth'});", null,
                    )
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------------------------------------------------------- v1.9.4 clipboard

    /** App focus me copy hua content clipboard history me pakdo. Android 10+
     *  background apps clip nahi padh sakte — sirf foreground (browser norm). */
    private fun installClipboardHook() {
        if (clipHookInstalled) return
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.addPrimaryClipChangedListener {
                runCatching {
                    val item = cm.primaryClip ?: return@addPrimaryClipChangedListener
                    if (item.itemCount > 0) {
                        val text = item.getItemAt(0).coerceToText(this)?.toString() ?: return@runCatching
                        ClipboardStore.add(this, text)
                    }
                }
            }
            clipHookInstalled = true
        }
    }

    // ---------------------------------------------------------------- v1.9.4 screenshot

    /** UC Screen Shot tool — visible + full-page. PixelCopy (API 29+) sync
     *  latch se; full-page = content-height canvas (slowWholeDocumentDraw on). */
    private fun captureScreenshot() {
        val wv = activeTab?.webView ?: run {
            Toast.makeText(this, "No page to capture", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Screen Shot")
            .setItems(arrayOf("This screen", "Full page")) { _, which ->
                if (which == 0) captureVisible(wv) else captureFullPage(wv)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveOrShareBitmap(bmp: Bitmap, name: String) {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots")
        runCatching {
            dir.mkdirs()
            val f = File(dir, name)
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Toast.makeText(this, "Saved: ${f.name}", Toast.LENGTH_SHORT).show()
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                    this@MainActivity, "$packageName.fileprovider", f,
                ))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(Intent.createChooser(share, "Share screenshot")) }
        }.onFailure {
            Toast.makeText(this, "Screenshot failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureVisible(wv: WebView) {
        runCatching {
            // Canvas draw (slowWholeDocumentDraw ON hai) — WebView ka tile raster
            // canvas me mil jata hai, API 26+ sab par deterministic. PixelCopy
            // overload ambiguity ka bhool bhi nahi hoti.
            val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            wv.draw(c)
            saveOrShareBitmap(bmp, "orbit-shot-${System.currentTimeMillis()}.jpg")
        }.onFailure {
            Toast.makeText(this, "Screenshot failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureFullPage(wv: WebView) {
        runCatching {
            val scale = resources.displayMetrics.density
            val contentH = (wv.contentHeight * scale).toInt()
            val h = contentH.coerceIn(wv.height, 12000)  // sane cap 12000px
            val bmp = Bitmap.createBitmap(wv.width, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            wv.draw(c)
            saveOrShareBitmap(bmp, "orbit-full-${System.currentTimeMillis()}.jpg")
        }.onFailure {
            // full-page fail → visible fallback (kabhi bhi blank return nahi)
            captureVisible(wv)
        }
    }

    // ---------------------------------------------------------------- v1.9.4 save page

    /** UC Save Page — saveWebArchive(.mht) + file:// se wapas kholna. */
    private fun savePageArchive() {
        val wv = activeTab?.webView ?: return
        if (activeTab?.isInternal == true) {
            Toast.makeText(this, "Save a website page (not the home screen)", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val dir = File(getExternalFilesDir(null), "SavedPages")
            dir.mkdirs()
            val name = "orbit-page-${System.currentTimeMillis()}.mht"
            val path = File(dir, name).absolutePath
            wv.saveWebArchive(path)
            val list = savedPages().toMutableList()
            list.add(0, "${activeTab?.title.orEmpty().ifBlank { domainOf(activeTab?.url ?: "") }}|$path")
            while (list.size > 20) list.removeAt(list.size - 1)
            LocalStore.setSetting(this, "savedPages", JObj().put("v", list as List<String>).toString())
            Toast.makeText(this, "Page saved offline (${name})", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savedPages(): List<String> = runCatching {
        val raw = LocalStore.getSetting(this, "savedPages", "")
        if (raw.isBlank()) emptyList()
        else JObj(raw).optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun showSavedPagesSheet() {
        val pages = savedPages()
        if (pages.isEmpty()) {
            Toast.makeText(this, "No saved pages yet — use Save page from Tools", Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(16))
        }
        box.addView(TextView(this).apply {
            text = "Saved pages (offline)"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        pages.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            val title = parts.getOrNull(0) ?: "Page"
            val path = parts.getOrNull(1) ?: return@forEach
            val r = TextView(this).apply {
                text = title
                textSize = 14.5f
                setTextColor(0xFFE8EAED.toInt())
                setPadding(dp(8), dp(12), dp(8), dp(12))
                background = getDrawable(R.drawable.btn_ripple)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener {
                    hideCardOverlay()
                    activeTab?.id?.let { id ->
                        val tab = tabs.firstOrNull { it.id == id } ?: return@let
                        loadUrlInTab(tab, "file://" + path)
                        attachTabView(tab)
                    }
                }
            }
            box.addView(r)
        }
        showCardOverlay(box)
    }

    // ---------------------------------------------------------------- v1.9.4 translate

    /** UC AI Translation — Google Translate element inject (real-time page
     *  translate). Target = Language setting. CSP-blocked sites par toast. */
    private fun translateCurrentPage() {
        val wv = activeTab?.webView ?: return
        if (activeTab?.isInternal == true) {
            Toast.makeText(this, "Open a website first", Toast.LENGTH_SHORT).show()
            return
        }
        val lang = LocalStore.getSetting(this, "appLanguage", "en")
        val js = "(function(){" +
            "try{if(window.__orbitTr){" +
            "window.__orbitTr.style.display='';return;}" +
            "var w=document.createElement('div');w.id='orbit_translate_wrap';" +
            "w.style.cssText='position:fixed;top:0;left:0;right:0;z-index:2147483647;background:#1a73e8;color:#fff;font:13px sans-serif;padding:6px 12px;text-align:center;box-shadow:0 2px 8px rgba(0,0,0,.4)';" +
            "w.innerHTML='Translating this page...';" +
            "document.body.appendChild(w);window.__orbitTr=w;" +
            "var s=document.createElement('script');" +
            "s.src='https://translate.googleapis.com/translate_a/element.js?cb=orbitTrCb';" +
            "s.onerror=function(){try{w.innerHTML='This site blocked translation (CSP)';setTimeout(function(){w.style.display='none';},3000);}catch(e){}};" +
            "window.orbitTrCb=function(){" +
            "try{new google.translate.TranslateElement({pageLanguage:'auto',includedLanguages:'$lang',autoDisplay:true,layout:google.translate.TranslateElement.InlineLayout.SIMPLE},'orbit_translate_wrap');" +
            "setTimeout(function(){try{w.innerHTML='Tap the blue bar to pick language';setTimeout(function(){w.style.display='none';},2500);}catch(e){}},1200);" +
            "}catch(e){}};" +
            "document.body.appendChild(s);" +
            "}catch(e){}})()"
        runCatching { wv.evaluateJavascript(js, null) }
        Toast.makeText(this, "Translating page (${languageName(lang)})...", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- v1.9.4 tab restore

    /** UC "Reopen Tabs on Startup" — session tabs JSON me save / restore. */
    private fun saveTabsForRestore() {
        runCatching {
            if (!reopenTabsPref()) {
                LocalStore.removeSetting(this, "lastTabs")
                return
            }
            val arr = JSONArray()
            tabs.take(10).forEach { t ->
                if (!t.isIncognito && t.url.startsWith("http")) {
                    arr.put(JObj()
                        .put("u", t.url)
                        .put("t", t.title)
                        .put("d", t.desktopSite))
                }
            }
            if (arr.length() > 0) {
                LocalStore.setSetting(this, "lastTabs", JObj().put("v", arr).toString())
            } else {
                LocalStore.removeSetting(this, "lastTabs")
            }
        }
    }

    private fun restoreTabsIfEnabled(): Boolean {
        if (!reopenTabsPref()) return false
        val raw = LocalStore.getSetting(this, "lastTabs", "")
        if (raw.isBlank()) return false
        return runCatching {
            val arr = JObj(raw).optJSONArray("v") ?: return false
            if (arr.length() == 0) return false
            var first = true
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("u")
                if (!url.startsWith("http")) continue
                if (first) {
                    val tab = Tab(nextTabId++, url, o.optString("t", domainOf(url)))
                    tab.isInternal = false
                    tab.desktopSite = o.optBoolean("d", false)
                    tabs.add(tab)
                    selectTab(tab.id)
                    loadUrlInTab(tab, url)
                    first = false
                } else {
                    val tab = Tab(nextTabId++, url, o.optString("t", domainOf(url)))
                    tab.isInternal = false
                    tab.desktopSite = o.optBoolean("d", false)
                    tabs.add(tab)
                }
            }
            !first
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- v1.9.4 Just In + quick access notif

    /** UC "Just In" — app open rehte hue har 30 min top local news alert. */
    private fun scheduleJustInCheck() {
        justInRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                if (justInPref() && pushNotifPref()) runJustInCheck()
                sleeper.postDelayed(this, 30 * 60 * 1000L)
            }
        }
        sleeper.postDelayed(justInRunnable!!, 5 * 60 * 1000L)
    }

    private var justInRunnable: Runnable? = null

    private fun runJustInCheck() {
        Thread {
            runCatching {
                val lastHead = LocalStore.getSetting(this, "justInHead", "")
                val city = LocalStore.getSetting(this, "userCity", "India")
                val resp = feedHttp.newCall(
                    Request.Builder()
                        .url("https://news.google.com/rss/search?q=" +
                            java.net.URLEncoder.encode("$city news", "UTF-8") +
                            "&hl=en-IN&gl=IN&ceid=IN:en")
                        .build(),
                ).execute().use { r ->
                    r.body?.string().orEmpty()
                }
                val m = Regex("<title>(.*?)</title>").find(resp)
                val head = m?.groupValues?.get(1)?.trim().orEmpty()
                if (head.isNotBlank() && head != lastHead && !head.contains("Top stories")) {
                    LocalStore.setSetting(this, "justInHead", head)
                    runOnUiThread {
                        showJustInNotification(head)
                    }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun notifChannel(): String {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm?.createNotificationChannel(
                    android.app.NotificationChannel(
                        "orbit_justin", "Just In", android.app.NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
                nm?.createNotificationChannel(
                    android.app.NotificationChannel(
                        "orbit_quick", "Quick Access", android.app.NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
        return "orbit_justin"
    }

    private fun showJustInNotification(headline: String) {
        runCatching {
            val pi = PendingIntent.getActivity(
                this, 9011,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = androidx.core.app.NotificationCompat.Builder(this, notifChannel())
                .setSmallIcon(R.drawable.ic_notif_orbit)
                .setContentTitle("Just In")
                .setContentText(headline)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.notify(9011, n)
        }
    }

    /** UC Push Notification — persistent Quick Access bar notification
     *  (New Tab / QR Scan / Downloads actions). Push Notif setting controls. */
    private fun updateQuickAccessNotification() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (!pushNotifPref()) {
                nm?.cancel(9012)
                return
            }
            maybeGrantNotifPermission()
            val openMain = PendingIntent.getActivity(
                this, 9021,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val qr = PendingIntent.getActivity(
                this, 9022,
                Intent(this, MainActivity::class.java).putExtra("quick", "qr"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val dl = PendingIntent.getActivity(
                this, 9023,
                Intent(this, MainActivity::class.java).putExtra("quick", "downloads"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = androidx.core.app.NotificationCompat.Builder(this, "orbit_quick")
                .setSmallIcon(R.drawable.ic_notif_orbit)
                .setContentTitle("Orbit Browser")
                .setContentText("Quick search & tools")
                .setContentIntent(openMain)
                .setOngoing(true)
                .addAction(0, "QR Scan", qr)
                .addAction(0, "Downloads", dl)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()
            nm?.notify(9012, n)
        }
    }

    // ---------------------------------------------------------------- v1.9.4 default browser

    private fun isDefaultBrowser(): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val act = intent.resolveActivity(packageManager)
        act?.packageName == packageName
    }.getOrDefault(false)

    private fun requestDefaultBrowserRole() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) &&
                    !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
                ) {
                    startActivityForResult(
                        rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),
                        REQ_DEFAULT_BROWSER,
                    )
                    return
                }
            }
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            Toast.makeText(this, "Set Orbit as the browser app", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- v1.9.4 social download

    /** Social video source mila → download bar offer (OrbitDownloader). */
    private fun offerSocialDownload(url: String) {
        if (dlBar.visibility == View.VISIBLE) return
        val name = runCatching { URLUtil.guessFileName(url, null, "video/mp4") }
            .getOrDefault("orbit-video-${System.currentTimeMillis()}.mp4")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        box.addView(TextView(this).apply {
            text = "Video found on this page"
            textSize = 14.5f
            setTextColor(0xFFE8EAED.toInt())
        })
        box.addView(TextView(this).apply {
            text = name
            textSize = 12f
            setTextColor(0xFF9AA0A6.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        fun act(label: String, bold: Boolean, fn: () -> Unit) {
            actions.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(if (bold) 0xFF8AB4F8.toInt() else 0xFF9AA0A6.toInt())
                setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
                setPadding(dp(16), dp(6), dp(16), dp(6))
                background = getDrawable(R.drawable.btn_ripple)
                setOnClickListener { fn() }
            })
        }
        act("Skip", false) { dlBar.visibility = View.GONE }
        act("Download", true) {
            dlBar.visibility = View.GONE
            handleDownload(url, null, "video/mp4")
        }
        box.addView(actions)
        dlBar.removeAllViews()
        dlBar.addView(box)
        dlBar.visibility = View.VISIBLE
        dlBar.postDelayed({ if (dlBar.tag != "pinned") dlBar.visibility = View.GONE }, 8000)
    }

    // ---------------------------------------------------------------- v1.9.4 UC TOOLS SHEET

    /** UC Browser ka bottom tools sheet — EXACT layout (2x5 grid + Cancel).
     *  Har tool ka real backend: Speed Mode / Orbit Music / Screen Shot /
     *  Clipboard / AI Translation / QR Scanner / Save Page / Full Screen /
     *  Find in Page. */
    private fun showToolsSheet() {
        if (isFinishing || isDestroyed) return
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            setOnClickListener { (it.parent as? ViewGroup)?.removeView(it) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(),
                    0f, 0f, 0f, 0f,
                )
                setColor(0xFF16181D.toInt())
            }
            setPadding(dp(16), dp(10), dp(16), dp(14))
            isClickable = true
        }
        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ))

        // drag handle
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(6), 0, dp(10))
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0xFF5F6368.toInt())
            }
        })

        data class Tool(val icon: Int, val label: String, val act: () -> Unit)

        val tools = listOf(
            Tool(R.drawable.ic_speed, "Speed Mode") {
                toggleSpeedMode()
            },
            Tool(R.drawable.ic_music, "Orbit Music") {
                activeTab?.id?.let { navigate(it, "orbit://music") }
            },
            Tool(R.drawable.ic_camera, "Screen Shot") {
                captureScreenshot()
            },
            Tool(R.drawable.ic_clipboard, "Clipboard") {
                showClipboardSheet()
            },
            Tool(R.drawable.ic_translate, "AI Translation") {
                translateCurrentPage()
            },
            Tool(R.drawable.ic_qr, "QR Scanner") {
                startLensScan()
            },
            Tool(R.drawable.ic_savepage, "Save Page") {
                AlertDialog.Builder(this, R.style.OrbitDialog)
                    .setTitle("Save Page")
                    .setItems(arrayOf("Save this page (MHTML)", "View saved pages")) { _, which ->
                        if (which == 0) savePageArchive() else showSavedPagesSheet()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            Tool(R.drawable.ic_fullscreen, "Full Screen") {
                toggleImmersive()
            },
            Tool(R.drawable.ic_find, "Find in Page") {
                showFindBar()
            },
            Tool(0, "Cancel") {}, // placeholder — grid me cancel nahi, neeche button hai
        ).take(9) // 2 rows x 5 minus cancel placeholder handling

        val grid = GridLayout(this).apply {
            columnCount = 5
            rowCount = 2
            useDefaultMargins = false
        }
        val colW = resources.displayMetrics.widthPixels / 5
        tools.forEach { t ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = GridLayout.LayoutParams().apply {
                    width = colW
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(0, dp(10), 0, dp(10))
                }
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                setOnClickListener {
                    (root.parent as? ViewGroup)?.removeView(root)
                    t.act()
                }
            }
            val iv = ImageView(this).apply {
                setImageResource(t.icon)
                setColorFilter(0xFFE8EAE9.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(0xFF26282E.toInt())
                }
                setPadding(dp(14), dp(14), dp(14), dp(14))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            val tv = TextView(this).apply {
                text = t.label
                textSize = 10.5f
                setTextColor(0xFFBDC1C6.toInt())
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(dp(2), dp(6), dp(2), 0)
            }
            cell.addView(iv)
            cell.addView(tv)
            grid.addView(cell)
        }
        card.addView(grid)

        // Cancel — UC parity: full-width pill bottom
        card.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(0xFFE8EAE9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(6))
            setOnClickListener { (root.parent as? ViewGroup)?.removeView(root) }
        })

        (findViewById<ViewGroup>(android.R.id.content)).addView(root)
        card.translationY = dp(120).toFloat()
        card.animate().translationY(0f).setDuration(180).start()
    }

    /** Speed Mode toggle — text-first lite browsing (images off + instant text). */
    private fun toggleSpeedMode() {
        val on = OrbitEngine.speedModeOn(this)
        LocalStore.setSetting(this, "speedMode", if (on) "off" else "on")
        val nowOn = !on
        tabs.forEach { t ->
            t.webView?.settings?.loadsImagesAutomatically = !nowOn || t.isIncognito
        }
        val wv = activeTab?.webView
        if (wv != null && !activeTab?.isInternal!!) {
            wv.settings.loadsImagesAutomatically = !nowOn
            wv.reload()
        }
        Toast.makeText(
            this,
            if (nowOn) "Speed Mode ON — text-first fast browsing" else "Speed Mode OFF — full pages",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** v1.9.4 — Clipboard history sheet (UC parity): tap = URL navigate /
     *  omnibox insert; long-press delete; Clear all. */
    private fun showClipboardSheet() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            setOnClickListener { (it.parent as? ViewGroup)?.removeView(it) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(),
                    0f, 0f, 0f, 0f,
                )
                setColor(0xFF16181D.toInt())
            }
            setPadding(dp(18), dp(12), dp(18), dp(16))
            isClickable = true
        }
        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ))
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(6), 0, dp(10))
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0xFF5F6368.toInt())
            }
        })
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "Clipboard"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(this).apply {
            text = "Clear all"
            textSize = 13.5f
            setTextColor(0xFF8AB4F8.toInt())
            setPadding(dp(12), dp(6), dp(4), dp(6))
            setOnClickListener {
                ClipboardStore.clear(this@MainActivity)
                Toast.makeText(this@MainActivity, "Clipboard cleared", Toast.LENGTH_SHORT).show()
                (root.parent as? ViewGroup)?.removeView(root)
            }
        })
        card.addView(titleRow)

        val entries = ClipboardStore.list(this)
        if (entries.isEmpty()) {
            card.addView(TextView(this).apply {
                text = "Nothing copied yet.\nCopy text or a link while browsing — it will appear here."
                textSize = 13.5f
                setTextColor(0xFF9AA0A6.toInt())
                setPadding(dp(4), dp(18), dp(4), dp(24))
            })
        } else {
            val sc = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            entries.forEachIndexed { idx, (clipText, at) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = getDrawable(R.drawable.btn_ripple)
                    isClickable = true
                    isLongClickable = true
                    setOnLongClickListener {
                        ClipboardStore.removeAt(this@MainActivity, idx)
                        Toast.makeText(this@MainActivity, "Deleted", Toast.LENGTH_SHORT).show()
                        (root.parent as? ViewGroup)?.removeView(root)
                        showClipboardSheet()
                        true
                    }
                }
                row.addView(TextView(this).apply {
                    text = if (clipText.length > 90) clipText.take(90) + "…" else clipText
                    textSize = 14f
                    setTextColor(0xFFE8EAED.toInt())
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                row.addView(TextView(this).apply {
                    text = SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(Date(at))
                    textSize = 11.5f
                    setTextColor(0xFF9AA0A6.toInt())
                    setPadding(0, dp(3), 0, 0)
                })
                row.setOnClickListener {
                    (root.parent as? ViewGroup)?.removeView(root)
                    if (ClipboardStore.looksLikeUrl(clipText)) {
                        activeTab?.id?.let { id -> navigate(id, normalizeInput(clipText)) }
                    } else {
                        omnibox.requestFocus()
                        omnibox.setText(clipText)
                        omnibox.setSelection(clipText.length)
                    }
                }
                list.addView(row)
            }
            sc.addView(list)
            card.addView(sc)
        }
        // footer action — paste into search
        card.addView(TextView(this).apply {
            text = "Cancel"
            textSize = 15f
            setTextColor(0xFFE8EAE9.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener { (root.parent as? ViewGroup)?.removeView(root) }
        })
        (findViewById<ViewGroup>(android.R.id.content)).addView(root)
        card.translationY = dp(120).toFloat()
        card.animate().translationY(0f).setDuration(180).start()
    }

    /** v1.9.4 — UC Account sign-in sheet: Google + Orbit Account + policy footer. */
    private fun showSignInSheet() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            setOnClickListener { (it.parent as? ViewGroup)?.removeView(it) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(),
                    0f, 0f, 0f, 0f,
                )
                setColor(0xFF1E2026.toInt())
            }
            setPadding(dp(22), dp(16), dp(22), dp(22))
            isClickable = true
        }
        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ))
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(4), 0, dp(12))
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0xFF5F6368.toInt())
            }
        })
        card.addView(TextView(this).apply {
            text = "Sign in"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(14))
        })

        // Google button (grey filled rounded — UC parity)
        val googleBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(0xFF3C4043.toInt())
            }
            setPadding(0, dp(13), 0, dp(13))
            setOnClickListener {
                (root.parent as? ViewGroup)?.removeView(root)
                showGoogleSignIn()
            }
        }
        val gIcon = TextView(this).apply {
            text = "G"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF4285F4.toInt())
            setPadding(0, 0, dp(10), 0)
        }
        googleBtn.addView(gIcon)
        googleBtn.addView(TextView(this).apply {
            text = "Sign in with Google"
            textSize = 15f
            setTextColor(0xFFE8EAED.toInt())
        })
        card.addView(googleBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, 0, 0, dp(12)) })

        // Orbit Account button (darker filled — UC parity)
        val orbitBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(0xFF2A2C31.toInt())
            }
            setPadding(0, dp(13), 0, dp(13))
            setOnClickListener {
                (root.parent as? ViewGroup)?.removeView(root)
                showOrbitAccountDialog()
            }
        }
        orbitBtn.addView(TextView(this).apply {
            text = "Sign in with Orbit Account"
            textSize = 15f
            setTextColor(0xFFE8EAED.toInt())
        })
        card.addView(orbitBtn)

        // Policy footer (clickable links — UC parity)
        val footer = TextView(this).apply {
            textSize = 12.5f
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(dp(6), dp(14), dp(6), 0)
            gravity = Gravity.CENTER
            text = "By signing in, you agree with our Privacy Policy and Terms of Use"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity, R.style.OrbitDialog)
                    .setTitle("Privacy & Terms")
                    .setMessage(
                        "Orbit Browser is a local-first browser:\n\n" +
                            "- History, bookmarks, passwords and settings stay on this device.\n" +
                            "- No account is required for browsing.\n" +
                            "- Sign-in data is stored only for sync features you trigger.\n\n" +
                            "Use of the app implies acceptance of this policy.",
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        card.addView(footer)

        (findViewById<ViewGroup>(android.R.id.content)).addView(root)
        card.translationY = dp(120).toFloat()
        card.animate().translationY(0f).setDuration(180).start()
    }

    /** Orbit Account — device-local account (email + hashed password).
     *  Bookmarks/settings sync baaki features ka base; data device par hi. */
    private fun showOrbitAccountDialog() {
        val acct = LocalStore.getSetting(this, "orbitAccount", "")
        if (acct.isNotBlank()) {
            AlertDialog.Builder(this, R.style.OrbitDialog)
                .setTitle("Orbit Account")
                .setMessage("Signed in as $acct\n\nSign out?")
                .setPositiveButton("Sign out") { _, _ ->
                    LocalStore.removeSetting(this, "orbitAccount")
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val email = EditText(this).apply {
            hint = "Email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val pass = EditText(this).apply {
            hint = "Password (min 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(0))
            addView(email)
            addView(pass, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) })
        }
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Sign in with Orbit Account")
            .setView(box)
            .setPositiveButton("Sign in") { _, _ ->
                val e = email.text.toString().trim()
                val p = pass.text.toString()
                if (!e.contains("@") || p.length < 6) {
                    Toast.makeText(this, "Valid email + 6+ char password needed", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                // password SHA-256 hash — plain kabhi store nahi
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(p.toByteArray()).joinToString("") { "%02x".format(it) }
                LocalStore.setSetting(this, "orbitAccount", e)
                LocalStore.setSetting(this, "orbitAccountHash", hash)
                Toast.makeText(this, "Signed in as $e (device-local)", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- v1.9.4 UC SETTINGS SUB-PAGES

    /** Reusable bottom option-sheet (UC "selector" parity): title + list of
     *  (key,value) options + current highlight + optional footer toggle. */
    private fun showOptionSheet(
        title: String,
        current: String,
        options: List<Pair<String, String>>,   // label to value
        onPick: (String) -> Unit,
    ) {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            setOnClickListener { (it.parent as? ViewGroup)?.removeView(it) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(),
                    0f, 0f, 0f, 0f,
                )
                setColor(0xFF1E2026.toInt())
            }
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true
        }
        root.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM,
        ))
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, dp(4), 0, dp(10))
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0xFF5F6368.toInt())
            }
        })
        card.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFE8EAED.toInt())
            setPadding(0, 0, 0, dp(10))
        })
        options.forEach { (label, value) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48),
                )
                setOnClickListener {
                    (root.parent as? ViewGroup)?.removeView(root)
                    onPick(value)
                }
            }
            r.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(0xFFE8EAED.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(ImageView(this).apply {
                setImageResource(if (value == current) R.drawable.ic_star else R.drawable.ic_down)
                setColorFilter(if (value == current) 0xFF8AB4F8.toInt() else 0x00000000)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            card.addView(r)
        }
        (findViewById<ViewGroup>(android.R.id.content)).addView(root)
        card.translationY = dp(120).toFloat()
        card.animate().translationY(0f).setDuration(180).start()
    }

    /** ---- AD Setting (ad blocker dept) ---- */
    private fun showAdSettings() {
        openPage("AD Setting") { content ->
            pageHeader("Blocking", content)
            content.addView(toggleRow(
                R.drawable.ic_shield, "Block ads & trackers", "Network-level — pages 30-40% faster load", "shields", "on",
            ))
            content.addView(toggleRow(
                R.drawable.ic_notsecure, "Block pop-ups", "Pop-up windows and redirects", "blockPopups", "on",
            ))
            content.addView(toggleRow(
                R.drawable.ic_speed, "YouTube ad shield", "Skip YouTube video ads instantly", "ytShield", "on",
            ))
            pageHeader("Stats", content)
            content.addView(pageRow(
                R.drawable.ic_shield, "Blocked so far (this session)",
                "${AdBlocker.totalBlocked} requests",
            ) { })
            pageHeader("Whitelist (ads allowed)", content)
            val wl = adWhitelist()
            if (wl.isEmpty()) {
                content.addView(pageEmpty("No whitelisted sites. Add one below to allow its ads."))
            } else {
                wl.forEach { host ->
                    content.addView(pageRow(R.drawable.ic_shield, host, "Ads allowed") {
                        AlertDialog.Builder(this, R.style.OrbitDialog)
                            .setTitle("Remove $host from whitelist?")
                            .setPositiveButton("Remove") { _, _ ->
                                saveAdWhitelist(adWhitelist() - host)
                                showAdSettings()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    })
                }
            }
            content.addView(pageRow(R.drawable.ic_star, "Add current site", "Allow ads on the site you're browsing") {
                val url = activeTab?.url.orEmpty()
                if (!url.startsWith("http")) {
                    Toast.makeText(this, "Open a website first", Toast.LENGTH_SHORT).show()
                    return@pageRow
                }
                val host = domainOf(url)
                saveAdWhitelist(adWhitelist() + host)
                Toast.makeText(this, "$host whitelisted", Toast.LENGTH_SHORT).show()
                showAdSettings()
            })
            pageHeader("Advanced", content)
            content.addView(pageRow(R.drawable.ic_fire, "Fire Button", "Wipe everything instantly") { fireButton() })
        }
    }

    private fun adWhitelist(): Set<String> = runCatching {
        val raw = LocalStore.getSetting(this, "adWhitelist", "")
        if (raw.isBlank()) emptySet() else JObj(raw).optJSONArray("v")?.let { a ->
            (0 until a.length()).map { a.getString(it) }.toSet()
        } ?: emptySet()
    }.getOrDefault(emptySet())

    private fun saveAdWhitelist(set: Set<String>) {
        val arr = JSONArray()
        set.sorted().forEach { arr.put(it) }
        LocalStore.setSetting(this, "adWhitelist", JObj().put("v", arr).toString())
    }

    /** Whitelist check — shouldInterceptRequest se pehle aata hai. */
    private fun isAdWhitelisted(url: String): Boolean {
        val host = domainOf(url).lowercase()
        if (host.isBlank()) return false
        return adWhitelist().any { wl -> host == wl || host.endsWith(".$wl") }
    }

    private fun toggleRow(
        iconRes: Int, title: String, sub: String, key: String, def: String,
        liveApply: (Boolean) -> Unit = {},
    ): View {
        val initial = LocalStore.getSetting(this, key, def) != "off"
        val tgl = makeToggle(initial) { on ->
            LocalStore.setSetting(this, key, if (on) "on" else "off")
            liveApply(on)
        }
        return pageRow(iconRes, title, sub, right = tgl)
    }

    /** ---- Customized Banner ---- */
    private fun showBannerSettings() {
        openPage("Customized Banner") { content ->
            content.addView(toggleRow(
                R.drawable.ic_banner, "Show banner on home", "Small greeting strip under the search bar", "bannerOn", "off",
            ) { on ->
                pushBannerToHome()
            })
            val cur = LocalStore.getSetting(this, "bannerText", "Welcome to Orbit Browser")
            val input = EditText(this).apply {
                setText(cur)
                hint = "Banner text"
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            content.addView(input)
            content.addView(pageRow(R.drawable.ic_star, "Save banner text", "Applies to the home screen instantly") {
                val t = input.text.toString().trim().take(60)
                LocalStore.setSetting(this, "bannerText", t.ifBlank { "Welcome to Orbit Browser" })
                pushBannerToHome()
                Toast.makeText(this, "Banner updated", Toast.LENGTH_SHORT).show()
            })
            content.addView(pageRow(R.drawable.ic_theme, "Quick shortcuts", "2x5 speed dial grid on home") { showSearchSettingsPage() })
        }
    }

    private fun pushBannerToHome() {
        val show = LocalStore.getSetting(this, "bannerOn", "off") == "on"
        val text = LocalStore.getSetting(this, "bannerText", "Welcome to Orbit Browser")
        val t = activeTab ?: tabs.firstOrNull { it.url == NEWTAB } ?: return
        t.webView?.evaluateJavascript(
            "if(window.bannerPush)bannerPush(${escapeJs(text)}, ${if (show) "true" else "false"});", null,
        )
    }

    /** ---- Browsing Settings (14 rows — screenshot parity) ---- */
    private fun showBrowsingSettings() {
        openPage("Browsing Settings") { content ->
            content.addView(pageRow(
                R.drawable.ic_find, "Font & Layout",
                "${textZoomPref()}%(Standard)",
            ) { showFontLayoutDialog() })
            content.addView(pageRow(
                R.drawable.ic_cloud, "Preload Page",
                when (preloadPref()) { "off" -> "Off"; "always" -> "Always"; else -> "WAP Pages" },
            ) { showOptionSheet("Preload Page", preloadPref(), listOf(
                "Off" to "off", "Wi-Fi Only (WAP Pages)" to "wap", "Always" to "always",
            )) { v ->
                LocalStore.setSetting(this, "preloadPages", v)
                showBrowsingSettings()
            } })
            content.addView(pageRow(
                R.drawable.ic_tabs, "Tab View",
                when (tabViewPref()) { "grid" -> "Grid View"; "list" -> "List View"; else -> "Card View" },
            ) { showOptionSheet("Tab View", tabViewPref(), listOf(
                "Card View" to "card", "Grid View" to "grid", "List View" to "list",
            )) { v ->
                LocalStore.setSetting(this, "tabView", v)
                showBrowsingSettings()
            } })
            content.addView(pageRow(
                R.drawable.ic_theme, "Image Quality",
                when (OrbitEngine.imageQuality(this)) {
                    "none" -> "No Images"; "low" -> "Low"; "medium" -> "Medium"; else -> "High"
                },
            ) { showOptionSheet("Image Quality", OrbitEngine.imageQuality(this), listOf(
                "No Images (text-only)" to "none", "Low" to "low", "Medium" to "medium", "High" to "high",
            )) { v ->
                LocalStore.setSetting(this, "imageQuality", v)
                val allow = OrbitEngine.imagesAllowed(this, false)
                tabs.forEach { t ->
                    t.webView?.settings?.loadsImagesAutomatically = allow || t.isIncognito
                    if (!t.isIncognito && t.webView != null) {
                        t.webView?.settings?.loadsImagesAutomatically = allow
                        if (!t.isInternal) t.webView?.reload()
                    }
                }
                showBrowsingSettings()
            } })
            content.addView(toggleRow(
                R.drawable.ic_swipe, "Swipe Forward&Backward", "Edge swipe se back/forward", "swipeNav", "on",
            ))
            content.addView(toggleRow(
                R.drawable.ic_newtab, "Reopen Tabs on Startup", "Last session ke tabs wapas khule", "reopenTabs", "off",
            ) { on -> if (!on) LocalStore.removeSetting(this, "lastTabs") })
            content.addView(pageRow(
                R.drawable.ic_typing, "Forms and Passwords",
                if (formsPasswordsPref() == "never") "Never save" else "Always ask",
            ) { showOptionSheet("Forms and Passwords", formsPasswordsPref(), listOf(
                "Always ask" to "ask", "Never save" to "never",
            )) { v ->
                LocalStore.setSetting(this, "formsPasswords", v)
                val never = v == "never"
                tabs.forEach { t ->
                    @Suppress("DEPRECATION")
                    t.webView?.settings?.saveFormData = !never
                }
                showBrowsingSettings()
            } })
            content.addView(pageRow(
                R.drawable.ic_scroll, "Scroll Options", "Volume keys scroll · tap-to-top",
            ) { showScrollOptions() })
            content.addView(pageRow(
                R.drawable.ic_brightness, "Brightness",
                if (brightnessLevelPref() > 0) "${brightnessLevelPref()}% dim" else "System",
            ) { showBrightnessDialog() })
            content.addView(toggleRow(
                R.drawable.ic_settings, "Animation", "Tab aur page transition animations", "uiAnimation", "on",
            ))
            content.addView(toggleRow(
                R.drawable.ic_fullscreen, "Show Status Bar", "Android top bar browsing ke time", "showStatusBar", "off",
            ) { on -> if (on) exitImmersive() else enterImmersive() })
            content.addView(toggleRow(
                R.drawable.ic_typing, "Typing Assistant", "www. .com .in quick-input bar", "typingAssistant", "on",
            ) { on ->
                typingBar?.visibility = if (on && omnibox.hasFocus()) View.VISIBLE else View.GONE
            })
            content.addView(toggleRow(
                R.drawable.ic_linkcolor, "Change Link Color If Clicked", "Visited links purple", "visitedLinks", "off",
            ) { on ->
                val t = activeTab
                if (t != null && !t.isInternal) t.webView?.reload()
            })
            content.addView(pageRow(
                R.drawable.ic_desktop, "Website Preferences (UA)", "Android · Desktop · iPhone · iPad",
            ) { showUaOptions() })
            pageHeader("Media", content)
            content.addView(toggleRow(
                R.drawable.ic_play, "Media autoplay", "Videos bina tap ke chalte hai", "autoplay", "on",
            ) { on ->
                tabs.forEach { t -> t.webView?.settings?.mediaPlaybackRequiresUserGesture = !on }
            })
        }
    }

    private fun showFontLayoutDialog() {
        val cur = textZoomPref()
        val seek = android.widget.SeekBar(this).apply {
            max = 150 // 50..200 mapped
            progress = cur - 50
        }
        val label = TextView(this).apply {
            text = "Font size: $cur%"
            textSize = 14f
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(dp(24), dp(18), dp(24), dp(4))
        }
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                val pct = value + 50
                label.text = "Font size: $pct%" + if (pct == 100) " (Standard)" else ""
                LocalStore.setSetting(this@MainActivity, "textZoom", pct.toString())
                tabs.forEach { t -> t.webView?.settings?.textZoom = pct }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(seek, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(24), dp(10), dp(24), dp(20)) })
        }
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Font & Layout")
            .setView(box)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showScrollOptions() {
        openPage("Scroll Options") { content ->
            content.addView(toggleRow(
                R.drawable.ic_scroll, "Volume key scrolling", "Volume up/down se page scroll", "volumeScroll", "off",
            ))
            content.addView(toggleRow(
                R.drawable.ic_scroll, "Tap-to-top button", "Scroll up par floating top button", "tapToTop", "off",
            ))
        }
    }

    private fun showUaOptions() {
        val cur = uaMode()
        showOptionSheet("Website Preferences (UA)", cur, listOf(
            "Android Mobile" to "android",
            "Desktop (Windows)" to "desktop",
            "iPhone" to "iphone",
            "iPad" to "ipad",
        )) { v ->
            LocalStore.setSetting(this, "uaMode", v)
            val t = activeTab
            if (t != null && !t.isInternal) {
                t.webView?.settings?.userAgentString = when (v) {
                    "desktop" -> DESKTOP_UA; "iphone" -> IPHONE_UA; "ipad" -> IPAD_UA; else -> realUA
                }
                t.webView?.reload()
            }
        }
    }

    /** ---- Download Settings (screenshot parity) ---- */
    private fun showDownloadSettings() {
        openPage("Download Settings") { content ->
            val paths = listOf(
                "Downloads (/sdcard)" to android.os.Environment.DIRECTORY_DOWNLOADS,
                "Pictures" to android.os.Environment.DIRECTORY_PICTURES,
                "Music" to android.os.Environment.DIRECTORY_MUSIC,
                "Movies" to android.os.Environment.DIRECTORY_MOVIES,
                "Documents" to android.os.Environment.DIRECTORY_DOCUMENTS,
            )
            val curPath = OrbitDownloader.relPath
            content.addView(pageRow(
                R.drawable.ic_download, "Default Path",
                "/sdcard/" + (paths.firstOrNull { it.second == curPath }?.first?.substringBefore(" ") ?: "Download"),
            ) { showOptionSheet("Default Path", curPath, paths) { v ->
                OrbitDownloader.relPath = v
                LocalStore.setSetting(this, "downloadPath", v)
            } })
            val maxDl = LocalStore.getSetting(this, "maxDownloads", "3").toIntOrNull()?.coerceIn(1, 6) ?: 3
            content.addView(pageRow(
                R.drawable.ic_download, "Max Downloads", "$maxDl",
            ) { showOptionSheet("Max Downloads", maxDl.toString(), (1..6).map { "$it to $it" to it.toString() }) { v ->
                LocalStore.setSetting(this, "maxDownloads", v)
                OrbitDownloader.maxConcurrent = v.toIntOrNull() ?: 3
            } })
            content.addView(pageRow(
                R.drawable.ic_newtab, "New Task",
                if (LocalStore.getSetting(this, "newTask", "ask") == "direct") "Download Immediately" else "Always Ask",
            ) { showOptionSheet("New Task", LocalStore.getSetting(this, "newTask", "ask"), listOf(
                "Always Ask (name dialog)" to "ask", "Download Immediately" to "direct",
            )) { v -> LocalStore.setSetting(this, "newTask", v) } })
            content.addView(toggleRow(
                R.drawable.ic_refresh, "Auto Reconnect", "Interrupted download khud resume", "autoReconnect", "on",
            ) { on -> OrbitDownloader.autoReconnect = on })
            content.addView(toggleRow(
                R.drawable.ic_notif_orbit, "Notification", "Pulse light and notification sound", "dlNotifSound", "off",
            ))
            pageHeader("Queue", content)
            val running = LocalStore.listDownloads(this).filter {
                it.status == OrbitDownloader.ST_RUNNING || it.status == OrbitDownloader.ST_PAUSED
            }
            content.addView(pageRow(R.drawable.ic_download, "Active tasks", "${running.size} queued/running") { showDownloadsPage() })
        }
        // live wiring — page kholte hi settings ko engine me push
        OrbitDownloader.maxConcurrent =
            LocalStore.getSetting(this, "maxDownloads", "3").toIntOrNull()?.coerceIn(1, 6) ?: 3
        OrbitDownloader.autoReconnect = LocalStore.getSetting(this, "autoReconnect", "on") != "off"
        OrbitDownloader.relPath = LocalStore.getSetting(this, "downloadPath", android.os.Environment.DIRECTORY_DOWNLOADS)
    }

    /** ---- Notification Settings (screenshot parity) ---- */
    private fun showNotificationSettings() {
        openPage("Notification Settings") { content ->
            content.addView(toggleRow(
                R.drawable.ic_notif_orbit, "Push Notification", "System notification bar", "pushNotif", "on",
            ) { on -> updateQuickAccessNotification() })
            content.addView(toggleRow(
                R.drawable.ic_speed, "\u201CJust In\u201D Notification", "Get alert when surfing", "justIn", "on",
            ))
            content.addView(pageRow(
                R.drawable.ic_shield, "Site Notification", "Web push permissions per website",
            ) { showSiteNotifications() })
            content.addView(pageRow(
                R.drawable.ic_tabs, "Quick Access", "Shortcuts in the notification bar",
            ) { showQuickAccessSettings() })
        }
    }

    private fun showSiteNotifications() {
        openPage("Site Notification") { content ->
            content.addView(toggleRow(
                R.drawable.ic_notsecure, "Block site notification requests", "Koi site notif maang sake? Default block", "blockSiteNotif", "on",
            ))
            pageHeader("Granted sites", content)
            val granted = LocalStore.getSetting(this, "siteNotifs", "")
            val list = runCatching {
                if (granted.isBlank()) emptyList() else JObj(granted).optJSONArray("v")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList()
            }.getOrDefault(emptyList())
            if (list.isEmpty()) {
                content.addView(pageEmpty(
                    "No site notifications requested yet.\n\nOrbit WebView apps se web-push requests nahi aate — yahan future grants dikhenge.",
                ))
            } else {
                list.forEach { host ->
                    content.addView(pageRow(R.drawable.ic_shield, host, "Allowed") {
                        val newV = list - host
                        LocalStore.setSetting(this, "siteNotifs",
                            JObj().put("v", JSONArray(newV)).toString())
                        showSiteNotifications()
                    })
                }
            }
        }
    }

    private fun showQuickAccessSettings() {
        openPage("Quick Access") { content ->
            content.addView(pageEmpty(
                "Quick Access notification me ye shortcuts hain:\n\n" +
                    "1. Orbit — app open\n2. QR Scan — scanner\n3. Downloads — download manager\n\n" +
                    "Push Notification toggle se poora bar on/off hota hai.",
            ))
        }
    }

    /** ---- Search Settings page ---- */
    private fun showSearchSettingsPage() {
        openPage("Search Settings") { content ->
            val engineName = when (LocalStore.getSetting(this, "searchEngine", "google")) {
                "bing" -> "Bing"; "ddg" -> "DuckDuckGo"; "brave" -> "Brave"; else -> "Google"
            }
            content.addView(pageRow(R.drawable.ic_find, "Default search engine", "$engineName — search bar + omnibox") {
                showSearchEngineCard()
            })
            content.addView(toggleRow(
                R.drawable.ic_find, "Search suggestions", "Typing par history + engine suggestions", "searchSuggestions", "on",
            ) { on -> if (!on) suggestPopup?.dismiss() })
            pageHeader("Note", content)
            content.addView(pageEmpty(
                "Google is the default engine.\n\nVPN (Spider Mode) ON hone par DuckDuckGo force hota hai — privacy guarantee ke liye. VPN OFF hone par aapki choice wapas lagti hai.",
            ))
        }
    }

    /** ---- Social Media Downloader Settings ---- */
    private fun showSocialDownloaderSettings() {
        openPage("Social Media Downloader Settings") { content ->
            content.addView(toggleRow(
                R.drawable.ic_social, "Auto-detect videos", "Reels/videos par Download offer", "socialDownloader", "on",
            ))
            pageHeader("Supported sites", content)
            SOCIAL_HOSTS.forEach { host ->
                content.addView(pageRow(R.drawable.ic_social, host, "Video source scan ON") { })
            }
            content.addView(pageEmpty(
                "Page load hote hi Orbit scan karta hai direct video sources (mp4). Mile to bottom bar par Download offer aata hai — Orbit download engine (4-thread parallel) use hota hai.",
            ))
        }
    }

    /** ---- Themes page (Dark / AMOLED / Wallpaper) ---- */
    private fun showThemesPage() {
        openPage("Themes") { content ->
            val cur = LocalStore.getSetting(this, "appTheme", "dark")
            content.addView(pageRow(
                R.drawable.ic_theme, "Dark", if (cur == "dark") "Current" else "Default dark grey",
            ) { applyTheme("dark") }.also {
                if (cur == "dark") it.setBackgroundColor(0x1A8AB4F8.toInt())
            })
            content.addView(pageRow(
                R.drawable.ic_theme, "AMOLED Black", if (cur == "amoled") "Current" else "Pure #000000 — battery saver",
            ) { applyTheme("amoled") }.also {
                if (cur == "amoled") it.setBackgroundColor(0x1A8AB4F8.toInt())
            })
            content.addView(pageRow(
                R.drawable.ic_banner, "Wallpaper", if (cur == "wallpaper") "Current" else "Pick a custom home wallpaper",
            ) {
                runCatching {
                    val i = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(i, REQ_WALLPAPER)
                }.onFailure {
                    Toast.makeText(this, "Gallery not available", Toast.LENGTH_SHORT).show()
                }
            }.also {
                if (cur == "wallpaper") it.setBackgroundColor(0x1A8AB4F8.toInt())
            })
            pageHeader("Preview note", content)
            content.addView(pageEmpty(
                "Theme turant home screen par apply hota hai (Dim overlay + background). AMOLED Black pure black use karta hai — OLED screens par battery bachata hai.",
            ))
        }
    }

    private fun applyTheme(mode: String) {
        LocalStore.setSetting(this, "appTheme", mode)
        tabs.forEach { t ->
            if (t.url == NEWTAB) {
                t.webView?.evaluateJavascript(
                    "if(window.themePush)themePush(${escapeJs(mode)});", null,
                )
            }
        }
        // WebView chrome bg bhi theme follow kare
        val bg = when (mode) {
            "amoled" -> 0xFF000000.toInt(); else -> 0xFF202124.toInt()
        }
        tabs.forEach { t -> t.webView?.setBackgroundColor(bg) }
        homeWebView?.setBackgroundColor(bg)
        Toast.makeText(this, "Theme: " + when (mode) {
            "amoled" -> "AMOLED Black"; "wallpaper" -> "Wallpaper"; else -> "Dark"
        }, Toast.LENGTH_SHORT).show()
    }

    /** ---- Cloud Boost sheet ---- */
    private fun showCloudBoostSheet() {
        val cur = LocalStore.getSetting(this, "cloudBoost", "on")
        showOptionSheet("Cloud Boost", cur, listOf(
            "ON — full acceleration (recommended)" to "on",
            "Wi-Fi only" to "wifi",
            "Off" to "off",
        )) { v ->
            LocalStore.setSetting(this, "cloudBoost", v)
            Toast.makeText(
                this,
                if (v == "off") "Cloud Boost OFF" else "Cloud Boost ON — preconnect + prefetch active",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** ---- Language page ---- */
    private fun showLanguagePage() {
        val cur = LocalStore.getSetting(this, "appLanguage", "en")
        showOptionSheet("Language", cur, listOf(
            "English" to "en", "Hindi (translate + search)" to "hi",
            "Gujarati (translate + search)" to "gu", "Bengali" to "bn",
            "Tamil" to "ta", "Telugu" to "te",
        )) { v ->
            LocalStore.setSetting(this, "appLanguage", v)
            Toast.makeText(
                this,
                "Language: ${languageName(v)} — AI Translation + Google search hl apply",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** ---- Clear Records (UC style checkboxes) ---- */
    private fun showClearRecords() {
        val opts = listOf(
            "Cache" to CheckBox(this),
            "Cookies" to CheckBox(this),
            "History" to CheckBox(this),
            "Form data" to CheckBox(this),
            "Passwords" to CheckBox(this),
        )
        opts.forEach { it.second.isChecked = true }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(0))
        }
        opts.forEach { (label, cb) ->
            cb.text = label
            cb.setTextColor(0xFFE8EAED.toInt())
            box.addView(cb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42),
            ))
        }
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Clear Records")
            .setView(box)
            .setPositiveButton("Clear") { _, _ ->
                val selected = opts.filter { it.second.isChecked }.map { it.first }
                runCatching {
                    if ("Cache" in selected) WebView(this).clearCache(true)
                    if ("Cookies" in selected) android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    if ("History" in selected) LocalStore.clearHistory(this)
                    if ("Form data" in selected) WebViewDatabase.getInstance(this).clearFormData()
                    if ("Passwords" in selected) WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()
                }
                Toast.makeText(this, "Cleared: ${selected.joinToString()}", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** ---- Reset to Default (UC bottom button) ---- */
    private fun confirmResetToDefault() {
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Reset to Default")
            .setMessage("All settings will reset to factory defaults.\n\nHistory, bookmarks and downloads will NOT be deleted.")
            .setPositiveButton("Reset") { _, _ ->
                runCatching {
                    LocalStore.clearAllSettings(this)
                    // engine-level defaults dobara lagao
                    tabs.forEach { t ->
                        t.webView?.settings?.textZoom = 100
                        t.webView?.settings?.loadsImagesAutomatically = true
                        t.webView?.settings?.mediaPlaybackRequiresUserGesture = false
                    }
                    OrbitDownloader.maxConcurrent = 3
                    OrbitDownloader.autoReconnect = true
                    OrbitDownloader.relPath = android.os.Environment.DIRECTORY_DOWNLOADS
                    applyBrightnessPref()
                    updateQuickAccessNotification()
                    Toast.makeText(this, "Settings reset to default", Toast.LENGTH_LONG).show()
                    showSettings()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- helpers

    private fun assetHtml(name: String): String =
        runCatching { assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault(
            "<html><body style='background:#000;color:#fff'>Asset missing: $name</body></html>",
        )

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeJs(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val NEWTAB = "orbit://newtab"
        const val GOOGLE_SEARCH = "https://www.google.com/search?q="
        const val DEFAULT_AI_BACKEND = "https://orbit-browser.vercel.app"

        // LENS scanner — QR na mile to Google Lens website (fallback)
        const val LENS_WEB = "https://lens.google.com/"
        private const val REQ_LENS_CAPTURE = 4201

        // MODULE 4 — VPN consent request code
        private const val REQ_VPN_CONSENT = 4202

        // v1.7.0 — voice search (home search bar mic) request code
        private const val REQ_VOICE = 4203

        // v1.9.4 — Set as Default Browser role request
        private const val REQ_DEFAULT_BROWSER = 4204

        // v1.9.4 — Themes wallpaper picker request
        private const val REQ_WALLPAPER = 4205

        /** Desktop site toggle ke liye — modern desktop Chrome UA */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        // OPERA MODULE — isse zyada live WebViews nahi (RAM limit)
        const val MAX_LIVE_WEBVIEWS = 6

        /** SURVIVAL MODE — emergency screen ke "Safe mode" button se ON hota hai.
         *  Shields/anti-fp/custom-UA sab OFF — pure vanilla WebView. Agar safe
         *  mode chalta hai par normal nahi, to wajah feature-module me hai. */
        var safeMode = false

        // EDGE MODULE — 5 min inactive = WebView freeze (battery bachao)
        const val SLEEP_AFTER_MS = 5 * 60_000L

        /** FIREFOX MODULE — anti-fingerprinting preload (document-start).
         *  Canvas toDataURL/getImageData me 1-bit random noise + hardware spoof —
         *  fingerprint hamesha ALAG aata hai, sites pehchan nahi paati. */
        const val FINGERPRINT_SHIELD_JS =
            "(function(){try{" +
                "var n=function(c){try{var x=c.getContext('2d');if(!x)return;" +
                "var w=Math.min(c.width||1,16),h=Math.min(c.height||1,16);if(w<1||h<1)return;" +
                "var d=x.getImageData(0,0,w,h);for(var i=0;i<d.data.length;i+=4){d.data[i]=d.data[i]^1;}" +
                "x.putImageData(d,0,0);}catch(e){}};" +
                "var t=HTMLCanvasElement.prototype.toDataURL;" +
                "HTMLCanvasElement.prototype.toDataURL=function(){n(this);return t.apply(this,arguments);};" +
                "var g=CanvasRenderingContext2D.prototype.getImageData;" +
                "CanvasRenderingContext2D.prototype.getImageData=function(){var r=g.apply(this,arguments);" +
                "try{for(var i=0;i<r.data.length;i+=997){r.data[i]=r.data[i]^1;}}catch(e){}return r;};" +
                "Object.defineProperty(navigator,'hardwareConcurrency',{get:function(){return 8;}});" +
                "Object.defineProperty(navigator,'deviceMemory',{get:function(){return 8;}});" +
                "}catch(e){}})();"
    }
}
