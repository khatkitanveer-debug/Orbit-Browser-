package com.orbit.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import android.widget.CheckBox
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
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

    private val tabs = mutableListOf<Tab>()
    private var activeTab: Tab? = null
    private var nextTabId = 1L

    private lateinit var toolbar: LinearLayout
    private lateinit var container: FrameLayout
    private lateinit var omnibox: EditText
    private lateinit var omniIcon: ImageView
    private lateinit var btnReload: ImageButton
    private lateinit var btnClear: ImageButton
    // v1.4.6 — nullable (lateinit nahi): agar kabhi init order slip ho jaye to
    // NPE/UninitializedPropertyAccessException IMPOSSIBLE — safe-call silently skip
    private var ringLoader: OrbitRingLoader? = null
    // v1.4.4 — ring ab omnibox ke andar NAHI, search bar ke NEECHE floating overlay
    private var ringWrap: FrameLayout? = null
    private lateinit var pullRefresh: SwipeRefreshLayout
    // v1.4.1 — active tab load state (ring loader + lock-icon visibility ke liye)
    private var pageLoading = false
    private lateinit var tabCount: TextView
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
        omnibox = findViewById(R.id.omnibox)
        omniIcon = findViewById(R.id.omni_icon)
        btnReload = findViewById(R.id.btn_reload)
        ringLoader = findViewById(R.id.ring_loader)
        ringWrap = findViewById(R.id.ring_wrap)
        pullRefresh = findViewById(R.id.pull_refresh)
        tabCount = findViewById(R.id.tab_count)
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

        btnReload.setOnClickListener {
            val wv = activeTab?.webView ?: return@setOnClickListener
            if (wv.progress in 1..99) wv.stopLoading() else wv.reload()
        }
        // btn_tabs XML me FrameLayout hai (Chrome tab-count button — square + number
        // andar). ImageButton cast yahin ClassCastException deta tha = 4 releases ka
        // launch-crash (v1.1 se). Click-only target, isliye View cast — kabhi nahi tootega.
        findViewById<View>(R.id.btn_tabs).setOnClickListener { showTabSwitcher() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu() }

        // v1.4.1 — HOME button: kisi bhi site se ek tap me Orbit home (newtab)
        findViewById<ImageButton>(R.id.btn_home).setOnClickListener {
            activeTab?.id?.let { navigate(it, NEWTAB) }
        }

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
        newTab()
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
    }

    // ---------------------------------------------------------- survival mode

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
            Toast.makeText(this, "Incognito tab — nothing is saved to history", Toast.LENGTH_SHORT).show()
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
        // MODULE 3 — agar yahi tab media play kar raha tha to service band
        PlaybackCoordinator.webViewDestroyed(tab.webView)
        tab.webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
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
        if (tab.webView == null) {
            tab.webView = buildWebView(tab)
            // OPERA MODULE — evicted tab wapas aaya to URL dobara load (WebView fresh hai)
            if (tab.evicted) {
                tab.evicted = false
                if (!tab.isInternal && tab.url != NEWTAB) tab.webView?.loadUrl(tab.url)
            }
        }
        // MODULE 3 v1.3.3 FIX — media chala raha WebView ko container se DETACH
        // MAT karo! WebView detach = Chromium "page hidden" = audio/video pause.
        // (Pehle removeAllViews() owner ko bhi nikaal deta tha — tab switch par
        // YouTube ruk jata tha.) Ab owner attached rehta hai, active WebView
        // uske UPAR add hota hai; wapas switch par owner phir top par aa jata hai.
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
        activeTab = tab
        tab.lastActive = System.currentTimeMillis()
        // OPERA MODULE — RAM limit: sabse zyada purane inactive WebViews freeze/destroy
        evictExcessTabs()
        refreshChrome()
        if (tabSwitcher.visibility == View.VISIBLE) renderSwitcherCards()
        if (tab.isInternal && tab.url == NEWTAB) {
            loadInternal(tab, NEWTAB)
        }
    }

    /** Har chrome element ek jagah — toolbar, count, icon, PTR, hint */
    private fun refreshChrome() {
        val tab = activeTab ?: return
        tabCount.text = tabs.size.coerceAtMost(99).toString()
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
        if (tab.isIncognito) {
            omnibox.hint = "Incognito — Search or type URL"
            toolbar.setBackgroundColor(0xFF3C4043.toInt())
        } else {
            omnibox.hint = getString(R.string.search_hint)
            toolbar.setBackgroundColor(0xFF202124.toInt())
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
        // MODULE 3 v1.3.3 — BackgroundWebView: Chromium ko screen-lock/app-
        // background par bhi VISIBLE batata hai. Plain WebView window visibility
        // GONE leke media pipeline suspend kar deta tha (asli pause-yahan-hota-hai
        // wala fix — dekho render/BackgroundWebView.kt).
        val wv = BackgroundWebView(this)
        wv.setBackgroundColor(0xFF202124.toInt())
        // v1.4.0 — Settings toggles ab LIVE apply hote hain (JavaScript/popups/cookies/images).
        // Internal pages (newtab) JS-ON force hote hain loadInternal() me.
        wv.settings.javaScriptEnabled = jsEnabledPref()
        wv.settings.domStorageEnabled = true
        // GOOGLE SERVICES FIX: real WebView UA (engine version sach me match karta
        // hai) + third-party cookies + multiple windows — bina inke Google login,
        // search aur YouTube ke flows block/broken rehte the.
        wv.settings.userAgentString = when {
            tab.desktopSite -> DESKTOP_UA
            // SURVIVAL safe mode — bilkul vanilla UA, kuch bhi custom nahi
            safeMode -> runCatching { WebSettings.getDefaultUserAgent(this) }.getOrDefault(realUA)
            else -> realUA
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
        wv.settings.textZoom = (resources.configuration.fontScale * 100f).toInt().coerceIn(50, 250)
        wv.settings.mediaPlaybackRequiresUserGesture = !autoplayPref()
        // MODULE 3 FIX (screen-lock playback) — Chromium apne MEDIA PIPELINE me
        // video pause kar deta hai jab screen off / app background hoti hai. Ye
        // page-JS visibility-lock se NAHI rukta (pipeline-level pause hai).
        // Renderer ko IMPORTANT + whenVisible=false dene se WebView background
        // me bhi media chalne deta hai — Chrome/JioSphere parity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        wv.settings.databaseEnabled = true
        wv.settings.loadsImagesAutomatically = imagesPref()
        wv.settings.allowFileAccess = false
        // MODULE 3 (background playback) — har WebView ka apna media sink.
        // Document-start bootstrap: viewport (M1) + media-watch (M3) + fingerprint
        // shield (agar on) SAB EK combined script me — addDocumentStartJavaScript
        // har call par pichhle scripts REPLACE karta hai, isliye ek hi call.
        wv.addJavascriptInterface(PlaybackSink(wv), "OrbitMediaSink")
        wv.addJavascriptInterface(Bridge(tab), "OrbitBridge")
        val bootstrapJs = buildString {
            append(ViewportBootstrap.SCRIPT)
            append("\n;")
            append(MediaWatchBootstrap.SCRIPT)
            if (RemoteConfig.ytShieldEnabled()) {
                // v1.3.4 — YouTube Ad Shield: player JSON se adPlacements strip +
                // skip-button autoclick + ad containers hide. Kill-switch:
                // ota/hotfix.json me "ytShield": false push karke bina APK off.
                append("\n;")
                append(YouTubeAdShield.SCRIPT)
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
            wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            wv.settings.domStorageEnabled = false
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
                // Chrome jaisa: loading me pill ka button X (stop) ban jata hai
                btnReload.setImageResource(if (newProgress in 1..99) R.drawable.ic_close else R.drawable.ic_refresh)
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
        return wv
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
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        tab.url = url
        when {
            url == NEWTAB -> loadInternal(tab, NEWTAB)
            url.startsWith("orbit://search?q=") -> {
                val q = java.net.URLDecoder.decode(url.removePrefix("orbit://search?q="), "UTF-8")
                loadInternal(tab, "orbit://search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            }
            url.startsWith("orbit://") -> loadInternal(tab, url)
            else -> loadUrlInTab(tab, url)
        }
        refreshChrome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadInternal(tab: Tab, url: String) {
        tab.isInternal = true
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
            url == NEWTAB -> wv.loadDataWithBaseURL("https://orbit.local/", assetHtml("newtab.html"), "text/html", "UTF-8", NEWTAB)
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
        val wv = tab.webView ?: buildWebView(tab).also { tab.webView = it }
        // WebView hi Chromium hai — DIRECT load: cookies, JS, redirects, login sab
        // native browser jaisa chalega.
        wv.settings.javaScriptEnabled = jsEnabledPref()
        wv.loadUrl(url)
        refreshChrome()
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
                            navigate(it, GOOGLE_SEARCH + java.net.URLEncoder.encode(t, "UTF-8"))
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
        divider()
        row(R.drawable.ic_history, "History") { showHistory() }
        row(R.drawable.ic_star, "Bookmarks") { showBookmarks() }
        row(R.drawable.ic_download, "Downloads") { showDownloadsPage() }
        row(R.drawable.ic_star, "Add bookmark") { toggleCurrentBookmark() }
        row(R.drawable.ic_find, "Find in page") { showFindBar() }
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
        row(R.drawable.ic_ai, "AI Search") { showAiSearchPrompt() }
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
    private fun showSettings() {
        openPage("Settings") { content ->
            fun toggleRow(iconRes: Int, title: String, sub: String, key: String, def: String, liveApply: (Boolean) -> Unit = {}) {
                val initial = LocalStore.getSetting(this, key, def) != "off"
                val tgl = makeToggle(initial) { on ->
                    LocalStore.setSetting(this, key, if (on) "on" else "off")
                    liveApply(on)
                }
                content.addView(pageRow(iconRes, title, sub, right = tgl))
            }

            pageHeader("Basics", content)
            // Search engine picker
            val engine = LocalStore.getSetting(this, "searchEngine", "google")
            val engineName = when (engine) {
                "bing" -> "Bing"; "ddg" -> "DuckDuckGo"; "brave" -> "Brave"; else -> "Google"
            }
            content.addView(pageRow(R.drawable.ic_find, "Search engine", engineName) { showSearchEngineCard() })
            // VPN row — live status (hook se update hota hai)
            val vpnSub = TextView(this).apply {
                text = RelayEngine.statusText
                textSize = 12f
                setTextColor(0xFF9AA0A6.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val vpnRow = pageRow(R.drawable.ic_vpn, "VPN", RelayEngine.statusText, right = null) { showVpnSheet() }
            // pageRow apna subtitle khud banata hai — hook ke liye usko pakdo
            val vpnCol = vpnRow.getChildAt(1) as? LinearLayout
            val vpnSubInRow = vpnCol?.getChildAt(1) as? TextView ?: vpnSub
            vpnUiHooks.add { vpnSubInRow.text = RelayEngine.statusText }
            content.addView(vpnRow)
            // v1.4.9 — silent update toggle (pehla Install tap bhi ise ON kar deta hai)
            toggleRow(R.drawable.ic_down, "Auto-install updates", "Update downloads silently and the installer opens by itself", "autoInstall", "off")
            toggleRow(R.drawable.ic_shield, "Shields (block ads & trackers)", "Network-level ad + tracker blocking", "shields", "on")
            toggleRow(R.drawable.ic_lock, "Anti-fingerprint", "Canvas noise + hardware spoof", "antiFp", "on")
            toggleRow(R.drawable.ic_notsecure, "Block pop-ups", "Block pop-up windows and redirects", "blockPopups", "on")
            toggleRow(R.drawable.ic_lock, "Accept cookies", "Sites remember your sign-ins", "cookiesEnabled", "on") { on ->
                runCatching {
                    CookieManager.getInstance().setAcceptCookie(on)
                    tabs.forEach { t ->
                        t.webView?.let { CookieManager.getInstance().setAcceptThirdPartyCookies(it, on) }
                    }
                }
            }
            toggleRow(R.drawable.ic_settings, "Load images", "Show images on web pages", "imagesEnabled", "on") { on ->
                tabs.forEach { t -> t.webView?.settings?.loadsImagesAutomatically = on }
            }
            toggleRow(R.drawable.ic_settings, "JavaScript", "Run scripts on sites (recommended: on)", "jsEnabled", "on") { on ->
                // live reload non-internal active tab — warna purana page purani setting par hi rahega
                val t = activeTab
                if (t != null && !t.isInternal) t.webView?.let { wv ->
                    wv.settings.javaScriptEnabled = on
                    wv.reload()
                }
            }
            content.addView(pageRow(R.drawable.ic_trash, "Clear browsing data", "History, cookies, cache") { showClearDataCard() })
            content.addView(pageRow(R.drawable.ic_fire, "Fire Button", "Wipe everything instantly") { fireButton() })

            pageHeader("Advanced", content)
            toggleRow(R.drawable.ic_desktop, "Desktop site default", "New tabs open as desktop", "desktopDefault", "off")
            toggleRow(R.drawable.ic_settings, "Media autoplay", "Play videos without tapping", "autoplay", "on") { on ->
                tabs.forEach { t -> t.webView?.settings?.mediaPlaybackRequiresUserGesture = !on }
            }
            toggleRow(R.drawable.ic_update, "Sleeping tabs", "Freeze inactive tabs to save battery", "sleepTabs", "on")
            toggleRow(R.drawable.ic_settings, "Memory saver", "Free up RAM from old tabs", "memorySaver", "on")

            pageHeader("Sign in & sync", content)
            content.addView(pageRow(R.drawable.ic_google, "Sign in with Google", "Use your phone's Google account") { showGoogleSignIn() })
            content.addView(pageRow(R.drawable.ic_sync, "Orbit Sync", "Bookmarks + history backup") { showSync() })

            pageHeader("Orbit AI", content)
            content.addView(pageRow(R.drawable.ic_ai, "AI backend", LocalStore.getSetting(this, "aiBackend", DEFAULT_AI_BACKEND).ifBlank { "Not set" }) { showAiBackendCard() })

            pageHeader("About", content)
            content.addView(pageRow(R.drawable.ic_update, "Check for updates", "Orbit v${BuildConfig.VERSION_NAME}") { runAutoUpdate(manual = true) })
            content.addView(pageRow(R.drawable.ic_about, "About Orbit", "Fast. Private. Yours.") { showAbout() })
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
                hideCardOverlay()
                Toast.makeText(this, "Search engine: $name", Toast.LENGTH_SHORT).show()
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
                val cur = LocalStore.getSetting(this, "searchEngine", "google")
                if (cur != "ddg") {
                    prevEngine = cur
                    LocalStore.setSetting(this, "searchEngine", "ddg")
                    refreshChrome()
                }
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
    private fun imagesPref(): Boolean = LocalStore.getSetting(this, "imagesEnabled", "on") != "off"
    private fun autoplayPref(): Boolean = LocalStore.getSetting(this, "autoplay", "on") != "off"

    /** Settings me chuna hua search engine (default Google) */
    private fun searchEngineUrl(): String = when (LocalStore.getSetting(this, "searchEngine", "google")) {
        "bing" -> "https://www.bing.com/search?q="
        "ddg" -> "https://duckduckgo.com/?q="
        "brave" -> "https://search.brave.com/search?q="
        else -> GOOGLE_SEARCH
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

    /** v1.3.5 — Chrome-jaisa omnibox focus mode: typing ke waqt lock/shield/star/reload
     *  icons hide + X dikhao. Suggestions popup aur icons ka "mix" isi se fixed. */
    private fun applyOmniFocus(focused: Boolean) {
        if (focused) {
            omniIcon.visibility = View.GONE
            shieldBadge.visibility = View.GONE
            btnReload.visibility = View.GONE
            btnStar.visibility = View.GONE
            btnClear.visibility = if (omnibox.text.isBlank()) View.GONE else View.VISIBLE
        } else {
            omniIcon.visibility = if (pageLoading) View.INVISIBLE else View.VISIBLE
            btnReload.visibility = View.VISIBLE
            btnClear.visibility = View.GONE
            updateShieldBadge()
            activeTab?.let { updateBookmarkStar(it) }
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
        if (LocalStore.getSetting(this, "memorySaver", "on") == "off") return
        // MODULE 3 FIX — media owner tab eviction se OUT: video background me
        // chal rahi ho to uska WebView kabhi destroy nahi hoga
        var live = tabs.filter { it !== activeTab && it.webView != null && !PlaybackCoordinator.isMediaOwner(it.webView) }
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
            live = tabs.filter { it !== activeTab && it.webView != null && !PlaybackCoordinator.isMediaOwner(it.webView) }
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
                // v1.4.9 — SILENT AUTO-INSTALL: user ne ek baar Install dabaya to
                // (autoInstall=on) agli updates ka Orbit dialog NAHI aata — APK
                // background me download hote hi system installer khud khul jata hai.
                // (Google Play Protect ka scan screen Google ka hai — wo code se nahi
                // hat sakta, par playProtectGuideOnce se ek baar OFF kar sakte hai.)
                if (LocalStore.getSetting(this, "autoInstall", "off") == "on") {
                    Toast.makeText(this, "Orbit ${info.versionName} downloaded — installing update…", Toast.LENGTH_SHORT).show()
                    UpdateChecker.installWithStatus(this, apk)
                    return@runOnUiThread
                }
                AlertDialog.Builder(this, R.style.OrbitDialog)
                    .setTitle("Orbit ${info.versionName} is ready")
                    .setMessage("Tap Install — the old version will be replaced (your data stays safe).\n\nFrom the next update on, Orbit installs silently by itself.")
                    .setPositiveButton("Install") { _, _ ->
                        // v1.4.9 — pehla Install tap = agli updates silent (user request:
                        // "1st time aaye wo thik hai, uske baad silent update")
                        LocalStore.setSetting(this, "autoInstall", "on")
                        UpdateChecker.installWithStatus(this, apk)
                        showPlayProtectGuideOnce()
                    }
                    .setNeutralButton("Open APK in browser") { _, _ ->
                        UpdateChecker.openApkInBrowser(this, info.apkUrl)
                    }
                    .setNegativeButton("Later", null)
                    .show()
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
