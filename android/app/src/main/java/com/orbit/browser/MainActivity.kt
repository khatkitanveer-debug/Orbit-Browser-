package com.orbit.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import java.io.ByteArrayInputStream

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
    private lateinit var progress: ProgressBar
    private lateinit var pullRefresh: SwipeRefreshLayout
    private lateinit var tabCount: TextView
    private lateinit var tabSwitcher: FrameLayout
    private lateinit var switcherGrid: GridLayout
    private lateinit var switcherTitle: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView

    private var aiJob: Thread? = null
    private var menuPopup: PopupWindow? = null
    private var suggestPopup: PopupWindow? = null
    private lateinit var shieldBadge: TextView

    // EDGE MODULE — sleeping tabs ka timer
    private val sleeper = Handler(Looper.getMainLooper())

    /** System WebView ka REAL UA — "; wv" token hata kar. Fake/stale UA (purana
     *  Chrome/126 claim) Google ka bot-detection trigger karta tha — login/search
     *  par "browser not secure" aata tha. Real UA = engine version match. */
    private val realUA: String by lazy {
        WebSettings.getDefaultUserAgent(this).replace("; wv", "")
    }

    // ---------------------------------------------------------------- lifecycle

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        progress = findViewById(R.id.progress)
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

        // CHROME MODULE — omnibox typing par history/bookmark suggestions
        omnibox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (omnibox.hasFocus()) showSuggestions(s?.toString() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        omnibox.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) suggestPopup?.dismiss()
        }

        omnibox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                navigateInput(omnibox.text.toString())
                true
            } else false
        }
        omnibox.setSelectAllOnFocus(true)

        btnReload.setOnClickListener {
            val wv = activeTab?.webView ?: return@setOnClickListener
            if (wv.progress in 1..99) wv.stopLoading() else wv.reload()
        }
        findViewById<ImageButton>(R.id.btn_tabs).setOnClickListener { showTabSwitcher() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu() }

        // Chrome pull-to-refresh
        pullRefresh.setColorSchemeColors(0xFF8AB4F8.toInt())
        pullRefresh.setOnRefreshListener {
            activeTab?.webView?.reload()
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
        // silent auto-update: naya version mila to background me khud download,
        // complete hote hi sirf INSTALL button aata hai (Chrome updater jaisa)
        runAutoUpdate(manual = false)
    }

    override fun onBackPressed() {
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
            closeTab(activeTab!!.id)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        // Default browser ban gaya to external links yahan aate hain
        val data = intent?.data?.toString() ?: return
        if (data.startsWith("http")) newTabWithUrl(data)
    }

    // ---------------------------------------------------------------- tabs

    private fun newTab(incognito: Boolean = false) {
        val tab = Tab(nextTabId++, NEWTAB, "New Tab")
        tab.isInternal = true
        tab.isIncognito = incognito
        tabs.add(tab)
        selectTab(tab.id)
        if (incognito) {
            Toast.makeText(this, "Incognito tab — history me kuch save nahi hoga", Toast.LENGTH_SHORT).show()
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
        tab.webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        if (tabSwitcher.visibility == View.VISIBLE) renderSwitcherCards()
        refreshChrome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun selectTab(id: Long) {
        val tab = tabs.first { it.id == id }
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
        if (!omnibox.hasFocus()) {
            omnibox.setText(tab.url)
            omnibox.clearFocus()
        }
        pullRefresh.isEnabled = !tab.isInternal
        if (tab.isIncognito) {
            omnibox.hint = "Incognito — Search karo ya URL likho"
            toolbar.setBackgroundColor(0xFF3C4043.toInt())
        } else {
            omnibox.hint = getString(R.string.search_hint)
            toolbar.setBackgroundColor(0xFF202124.toInt())
        }
        updateOmniIcon(tab)
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
            tab.thumb?.recycle()
            tab.thumb = bmp
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tab: Tab): WebView {
        val wv = WebView(this)
        wv.setBackgroundColor(0xFF202124.toInt())
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        // GOOGLE SERVICES FIX: real WebView UA (engine version sach me match karta
        // hai) + third-party cookies + multiple windows — bina inke Google login,
        // search aur YouTube ke flows block/broken rehte the.
        wv.settings.userAgentString = if (tab.desktopSite) DESKTOP_UA else realUA
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.settings.javaScriptCanOpenWindowsAutomatically = true
        wv.settings.setSupportMultipleWindows(true)
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.addJavascriptInterface(Bridge(tab), "OrbitBridge")
        // FIREFOX MODULE — anti-fingerprinting: document-start pe script inject
        // (kisi bhi page script se PEHLE) — canvas noise + hardware spoof
        if (antiFpEnabled()) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(wv, FINGERPRINT_SHIELD_JS, setOf("*"))
            }
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
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                progress.progress = newProgress
                // Chrome jaisa: loading me pill ka button X (stop) ban jata hai
                btnReload.setImageResource(if (newProgress in 1..99) R.drawable.ic_close else R.drawable.ic_refresh)
            }

            // target=_blank / window.open — Chrome ki tarah NAYE TAB me
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val temp = WebView(view!!.context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("http")) newTabWithUrl(url)
                        v?.post { v.destroy() }
                        return true
                    }
                }
                (resultMsg!!.obj as WebView.WebViewTransport).webView = temp
                resultMsg.sendToTarget()
                return true
            }
        }
        // find-in-page counts
        wv.setFindListener { ordinal, matches, done ->
            if (findBar.visibility == View.VISIBLE) {
                findCount.text = if (matches > 0) "${ordinal + 1}/$matches" else "0/0"
            }
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
                if (looksLikeUrl) "https://$x" else GOOGLE_SEARCH + java.net.URLEncoder.encode(x, "UTF-8")
            }
        }
    }

    private fun navigate(tabId: Long, url: String) {
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
        tab.title = when {
            url == NEWTAB -> "New Tab"
            url.startsWith("orbit://search") -> "Orbit AI Search"
            else -> "Orbit"
        }
        val wv = tab.webView ?: buildWebView(tab).also { tab.webView = it }
        when {
            url == NEWTAB -> wv.loadDataWithBaseURL("https://orbit.local/", assetHtml("newtab.html"), "text/html", "UTF-8", NEWTAB)
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
            wv.evaluateJavascript("window.__aiError('Pehle Settings me apna Orbit AI backend URL set karo (menu -> Settings)')", null)
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
            if (!isFinishing) tab.webView?.evaluateJavascript(js, null)
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
            // newtab search bar — default GOOGLE search (browser ki tarah)
            runOnUiThread {
                navigate(tab.id, GOOGLE_SEARCH + java.net.URLEncoder.encode(q, "UTF-8"))
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
            if (!url.isNullOrEmpty() && url != "https://orbit.local/" && !url.startsWith("data:")) {
                tab.url = url
                refreshChrome()
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            pullRefresh.isRefreshing = false
            updateShieldBadge()
            if (view == null || url.isNullOrEmpty() || tab.isInternal) return
            tab.url = url
            tab.title = view.title?.ifBlank { null } ?: domainOf(url)
            refreshChrome()
            // Incognito tab — history me KUCH nahi jata (Chrome parity)
            if (!tab.isIncognito) LocalStore.addHistory(this@MainActivity, url, tab.title)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // sirf main frame — subresource errors page todte nahi
            if (request?.isForMainFrame == true) {
                showErrorPage(view ?: return, request.url.toString(), error?.description?.toString() ?: "Page load nahi hua")
            }
        }
    }

    // ---------------------------------------------------------------- chrome (omnibox etc.)

    private fun domainOf(url: String): String = runCatching {
        java.net.URI(url).host ?: url
    }.getOrDefault(url)

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

    /** Chrome tab switcher: 2-column cards grid — thumbnail/letter avatar + title + X */
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
            // thumbnail (ya letter avatar)
            val thumbHolder = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(96),
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
                textSize = 26f
                setTextColor(0xFFE8EAED.toInt())
                if (tab.thumb != null) visibility = View.GONE
            }
            thumbHolder.addView(iv)
            thumbHolder.addView(letter)
            card.addView(thumbHolder)

            // title + close row
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(6), dp(0), dp(0))
            }
            val title = TextView(this).apply {
                text = (if (tab.sleeping) "😴 " else "") +
                    (if (tab.isIncognito) "[Incognito] " else "") +
                    tab.title.ifBlank { domainOf(tab.url) }
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFFE8EAED.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val x = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(0xFF9AA0A6.toInt())
                background = getDrawable(R.drawable.btn_ripple)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                setOnClickListener {
                    closeTabInternal(tab)
                    if (tabs.isEmpty()) { hideTabSwitcher(); newTab() }
                }
            }
            row.addView(title)
            row.addView(x)
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

    /** Chrome-jaisa 3-dot menu — btn_menu ke neeche se slide hoke khulta hai, right-aligned. */
    private fun showMenu() {
        if (menuPopup?.isShowing == true) { menuPopup?.dismiss(); return }
        val anchor = findViewById<View>(R.id.btn_menu)
        val content = layoutInflater.inflate(R.layout.popup_menu, null)
        val list = content.findViewById<LinearLayout>(R.id.menu_items)

        val tab = activeTab
        data class Item(val icon: Int, val label: String, val action: () -> Unit)
        val items = listOf(
            Item(R.drawable.ic_newtab, "New tab") { newTab() },
            Item(R.drawable.ic_private, "Incognito tab") { newTab(incognito = true) },
            null, // divider
            Item(R.drawable.ic_history, "History") { showHistory() },
            Item(R.drawable.ic_star, "Bookmarks") { showBookmarks() },
            Item(R.drawable.ic_ai, "AI Search") { showAiSearchPrompt() },
            Item(R.drawable.ic_find, "Find in page") { showFindBar() },
            null, // divider
            Item(R.drawable.ic_desktop, if (tab?.desktopSite == true) "✓ Desktop site" else "Desktop site") { toggleDesktopSite() },
            Item(R.drawable.ic_share, "Share karo") { sharePage() },
            Item(R.drawable.ic_shield, if (shieldsEnabled()) "✓ Shields (ads block)" else "Shields (ads block)") { toggleShields(); renderSwitcherCards() },
            Item(R.drawable.ic_lock, if (antiFpEnabled()) "✓ Anti-fingerprint" else "Anti-fingerprint") { toggleAntiFp() },
            Item(R.drawable.ic_fire, "Fire Button (sab data clear)") { fireButton() },
            null, // divider
            Item(R.drawable.ic_update, "Check update") { runAutoUpdate(manual = true) },
            Item(R.drawable.ic_settings, "Settings") { showSettings() },
            Item(R.drawable.ic_about, "About Orbit") { showAbout() },
        )

        for (item in items) {
            if (item == null) {
                val div = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        setMargins(dp(6), dp(4), dp(6), dp(4))
                    }
                    setBackgroundColor(0xFF3C4043.toInt())
                }
                list.addView(div)
                continue
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), 0, dp(20), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
                isFocusable = true
            }
            val iv = ImageView(this).apply {
                setImageResource(item.icon)
                setColorFilter(0xFFE8EAE9.toInt())
                layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
            }
            val tv = TextView(this).apply {
                text = item.label
                setTextColor(0xFFE8EAE9.toInt())
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(20)
                }
            }
            row.addView(iv)
            row.addView(tv)
            row.setOnClickListener {
                menuPopup?.dismiss()
                item.action()
            }
            list.addView(row)
        }

        menuPopup = PopupWindow(
            content,
            dp(262),
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
            Toast.makeText(this, "Internal page share nahi hota", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tab.url)
            putExtra(Intent.EXTRA_SUBJECT, tab.title)
        }
        runCatching { startActivity(Intent.createChooser(i, "Share karo")) }
    }

    // ---------------------------------------------------------------- modules (v1.2.0)

    private fun shieldsEnabled(): Boolean = LocalStore.getSetting(this, "shields", "on") != "off"

    private fun antiFpEnabled(): Boolean = LocalStore.getSetting(this, "antiFp", "on") != "off"

    private fun toggleShields() {
        val on = shieldsEnabled()
        LocalStore.setSetting(this, "shields", if (on) "off" else "on")
        Toast.makeText(this, if (on) "Shields OFF — ads dikhenge" else "Shields ON — ads/trackers block", Toast.LENGTH_SHORT).show()
    }

    private fun toggleAntiFp() {
        val on = antiFpEnabled()
        LocalStore.setSetting(this, "antiFp", if (on) "off" else "on")
        Toast.makeText(
            this,
            if (on) "Anti-fingerprint OFF (naye tabs me)" else "Anti-fingerprint ON (naye tabs me)",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Brave Shields badge — omnibox pill me blocked count */
    private fun updateShieldBadge() {
        val n = activeTab?.blockedCount ?: 0
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            if (n > 0 && shieldsEnabled()) {
                shieldBadge.text = "🛡 $n"
                shieldBadge.visibility = View.VISIBLE
            } else {
                shieldBadge.visibility = View.GONE
            }
        }
    }

    private fun showShieldStats() {
        val tab = activeTab
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("🛡 Orbit Shields")
            .setMessage(
                "Is page par ${tab?.blockedCount ?: 0} ads/trackers block hue.\n" +
                    "Is session me total ${AdBlocker.totalBlocked} blocked.\n\n" +
                    "Network-level blocking — matched requests server tak jaane se pehle drop hote hain.",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /** DDG FIRE BUTTON — cookies + cache + storage + history, sab ek jhatke me */
    private fun fireButton() {
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("🔥 Fire Button")
            .setMessage("Cookies, cache, site data aur history — SAB clear ho jayega, sab tabs band. Pakka?")
            .setPositiveButton("🔥 Jala do") { _, _ -> doFire() }
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
            Toast.makeText(this, "🔥 Sab data jala diya — fresh start", Toast.LENGTH_SHORT).show()
        }.start()
    }

    /** OPERA MODULE lite — max live WebViews, baaki oldest ka WebView destroy
     *  (tab khud zinda rehta hai — URL yaad hai, select par recreate ho jata hai) */
    private fun evictExcessTabs() {
        var live = tabs.filter { it !== activeTab && it.webView != null }
        for (tab in live.sortedBy { it.lastActive }) {
            if (live.size < MAX_LIVE_WEBVIEWS) break
            captureThumb(tab)
            tab.webView?.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            tab.webView = null
            tab.evicted = true
            live = tabs.filter { it !== activeTab && it.webView != null }
        }
    }

    /** EDGE MODULE — har 30s check: 5 min se inactive WebViews freeze (onPause)
     *  — JS/timers/battery sab ruk jata hai. Select karte hi onResume. */
    private val sleepCheck = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            for (tab in tabs) {
                if (tab === activeTab || tab.sleeping || tab.webView == null || tab.isInternal) continue
                if (now - tab.lastActive > SLEEP_AFTER_MS) {
                    tab.webView?.onPause()
                    tab.sleeping = true
                }
            }
            sleeper.postDelayed(this, 30_000)
        }
    }

    /** CHROME MODULE — omnibox suggestions: history + bookmarks, ranking ke saath */
    private fun showSuggestions(q: String) {
        val query = q.trim().lowercase()
        if (suggestPopup?.isShowing == true) suggestPopup?.dismiss()
        if (query.length < 2) return
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
        if (merged.isEmpty()) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.menu_bg)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        for ((url, meta) in merged.entries.take(6)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = getDrawable(R.drawable.btn_ripple)
                isClickable = true
            }
            val iv = ImageView(this).apply {
                setImageResource(if (meta.second) R.drawable.ic_star else R.drawable.ic_history)
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
                text = meta.first
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFFE8EAED.toInt())
            }
            val u = TextView(this).apply {
                text = url
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFF9AA0A6.toInt())
            }
            col.addView(t)
            col.addView(u)
            row.addView(iv)
            row.addView(col)
            row.setOnClickListener {
                suggestPopup?.dismiss()
                omnibox.clearFocus()
                val tab = activeTab ?: return@setOnClickListener
                navigate(tab.id, url)
            }
            content.addView(row)
        }
        suggestPopup = PopupWindow(content, omnibox.width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(10).toFloat()
            isOutsideTouchable = true
            showAsDropDown(omnibox, 0, dp(2))
        }
    }

    /** AI search optional hai — menu se. Default search Google hai. */
    private fun showAiSearchPrompt() {
        val input = EditText(this)
        input.hint = "AI se kya poochna hai?"
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Orbit AI Search")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val q = input.text.toString().trim()
                val tab = activeTab ?: return@setPositiveButton
                if (q.isNotEmpty()) navigate(tab.id, "orbit://search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHistory() {
        val items = LocalStore.listHistory(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "History khali hai", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { it.title.ifBlank { it.url } }.toTypedArray()
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("History")
            .setItems(labels) { _, which -> newTabWithUrl(items[which].url) }
            .setNeutralButton("Clear") { _, _ ->
                LocalStore.clearHistory(this)
                Toast.makeText(this, "History clear", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun showBookmarks() {
        val items = LocalStore.listBookmarks(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "Koi bookmark nahi", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { it.title.ifBlank { it.url } }.toTypedArray()
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Bookmarks")
            .setItems(labels) { _, which -> newTabWithUrl(items[which].url) }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun showSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val backendInput = view.findViewById<EditText>(R.id.input_backend)
        backendInput.setText(LocalStore.getSetting(this, "aiBackend", DEFAULT_AI_BACKEND))
        val versionText = view.findViewById<TextView>(R.id.text_version)
        versionText.text = "Orbit Browser v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n" +
            "Data 100% aapke device me — history, bookmarks, sab local."
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                LocalStore.setSetting(this, "aiBackend", backendInput.text.toString().trim())
                Toast.makeText(this, "Settings save ho gayi", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Orbit Browser")
            .setMessage(
                "Orbit — Fast. Private. Aapka.\n\n" +
                    "• Real browser: Chromium engine, Google services built-in\n" +
                    "• Chrome jaisa UI: tab switcher, omnibox, find, desktop site\n" +
                    "• Aapka data device par hi — koi server nahi\n" +
                    "• Incognito tab — history me kuch nahi\n" +
                    "• OTA updates: background download, ek tap install\n\n" +
                    "v${BuildConfig.VERSION_NAME}",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Fully silent auto-update — launch par bhi, menu check par bhi same flow:
     * update mila → background download (Orbit ke cache me) → sirf INSTALL wala
     * dialog. Koi "Update karo?" puchha-woochha nahi, koi Chrome/Downloads nahi.
     */
    private fun runAutoUpdate(manual: Boolean) {
        UpdateChecker.autoUpdate(this, manual) { info ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                AlertDialog.Builder(this, R.style.OrbitDialog)
                    .setTitle("Orbit ${info.versionName} ready hai")
                    .setMessage("Install dabao — purana version replace ho jayega (aapka data safe rahega).")
                    .setPositiveButton("Install") { _, _ ->
                        // cached APK izzat se use karo; missing ho to SILENT no-op
                        // NAHI — user ko batao aur download dobara shuru karo
                        val apk = UpdateChecker.cachedApk(this, info.versionName)
                        if (apk != null) {
                            UpdateChecker.installWithStatus(this, apk)
                        } else {
                            Toast.makeText(this, "APK cache me nahi mila — dobara download ho raha hai…", Toast.LENGTH_LONG).show()
                            runAutoUpdate(manual = true)
                        }
                    }
                    .show()
            }
        }
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

        /** Desktop site toggle ke liye — modern desktop Chrome UA */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        // OPERA MODULE — isse zyada live WebViews nahi (RAM limit)
        const val MAX_LIVE_WEBVIEWS = 6

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
