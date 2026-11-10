package com.orbit.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Orbit Browser — native Android browser.
 * Chrome-jaisa: multi-tabs, omnibox, bookmarks, history, AI search, private mode.
 * Har data DEVICE par hi rehta hai (LocalStore). External sites ProxyEngine se
 * fetch hokar WebView me render hoti hain — koi external browser app nahi chahiye.
 */
class MainActivity : Activity() {

    private class Tab(val id: Long, var url: String, var title: String) {
        var webView: WebView? = null
        var isInternal: Boolean = true
    }

    private val tabs = mutableListOf<Tab>()
    private var activeTab: Tab? = null
    private var nextTabId = 1L
    private var privateMode = false

    private lateinit var container: FrameLayout
    private lateinit var omnibox: EditText
    private lateinit var tabStrip: HorizontalScrollView
    private lateinit var tabStripRow: LinearLayout

    private var aiJob: Thread? = null

    // ---------------------------------------------------------------- lifecycle

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 15 (targetSdk 35) edge-to-edge FORCE karta hai — isliye Chrome-jaisa
        // insets handling: toolbar status bar ke NEECHE baithega, status bar area me
        // window ka black background dikhega (koi overlap nahi). Keyboard (IME) ke
        // saath bhi content resize hota hai. API 26-29 me theme ka black statusBarColor
        // pehle se kaam karta hai.
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

        container = findViewById(R.id.container)
        omnibox = findViewById(R.id.omnibox)
        tabStrip = findViewById(R.id.tab_strip)
        tabStripRow = findViewById(R.id.tab_strip_row)

        omnibox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                navigateInput(omnibox.text.toString())
                true
            } else false
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { activeTab?.webView?.let { if (it.canGoBack()) it.goBack() } }
        findViewById<ImageButton>(R.id.btn_fwd).setOnClickListener { activeTab?.webView?.let { if (it.canGoForward()) it.goForward() } }
        findViewById<ImageButton>(R.id.btn_reload).setOnClickListener { activeTab?.webView?.reload() }
        findViewById<ImageButton>(R.id.btn_bookmark).setOnClickListener { toggleBookmark() }
        findViewById<ImageButton>(R.id.btn_private).setOnClickListener {
            privateMode = !privateMode
            Toast.makeText(this, if (privateMode) "Private mode ON — kuch save nahi hoga" else "Private mode OFF", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btn_tabs).setOnClickListener { showTabSwitcher() }
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { showMenu() }

        // pehla tab — clean new tab screen
        newTab()
        checkForUpdate(silent = true)
    }

    override fun onBackPressed() {
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

    private fun newTab() {
        val tab = Tab(nextTabId++, NEWTAB, "New Tab")
        tab.isInternal = true
        tabs.add(tab)
        selectTab(tab.id)
    }

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
        val tab = tabs.removeAt(idx)
        tab.webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        if (tabs.isEmpty()) {
            finish()
        } else {
            selectTab(tabs[(idx - 1).coerceAtLeast(0)].id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun selectTab(id: Long) {
        val tab = tabs.first { it.id == id }
        if (tab.webView == null) {
            tab.webView = buildWebView(tab)
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
        }
        activeTab = tab
        refreshOmnibox()
        renderTabStrip()
        if (tab.isInternal && tab.url == NEWTAB) {
            loadInternal(tab, NEWTAB)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(tab: Tab): WebView {
        val wv = WebView(this)
        wv.setBackgroundColor(0xFF000000.toInt())
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.userAgentString = ProxyEngine.UA
        wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.addJavascriptInterface(Bridge(tab), "OrbitBridge")
        wv.webViewClient = OrbitWebViewClient(tab)
        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!tab.isInternal && !title.isNullOrBlank()) {
                    tab.title = title
                    refreshOmnibox()
                    renderTabStrip()
                    if (!privateMode) LocalStore.addHistory(this@MainActivity, tab.url, title)
                }
            }
        }
        return wv
    }

    private fun renderTabStrip() {
        tabStripRow.removeAllViews()
        tabs.forEach { tab ->
            val chip = TextView(this).apply {
                text = if (tab.title.isBlank()) domainOf(tab.url) else tab.title
                setSingleLine()
                textSize = 12f
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = getDrawable(R.drawable.tab_chip)
                setTextColor(if (tab === activeTab) 0xFFFFFFFF.toInt() else 0xFF9E9E9E.toInt())
                setOnClickListener { selectTab(tab.id) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity, R.style.OrbitDialog)
                        .setTitle("Tab band karein?")
                        .setMessage(text)
                        .setPositiveButton("Band karo") { _, _ -> closeTab(tab.id) }
                        .setNegativeButton("Nahi", null)
                        .show()
                    true
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(6)
            tabStripRow.addView(chip, lp)
        }
        tabStrip.post { tabStrip.fullScroll(View.FOCUS_RIGHT) }
    }

    // ---------------------------------------------------------------- navigation

    private fun navigateInput(raw: String) {
        val input = raw.trim()
        if (input.isEmpty()) return
        val tab = activeTab ?: return
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
        refreshOmnibox()
        renderTabStrip()
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
        refreshOmnibox()
        renderTabStrip()
    }

    private fun loadUrlInTab(tab: Tab, url: String) {
        tab.isInternal = false
        val wv = tab.webView ?: buildWebView(tab).also { tab.webView = it }
        // WebView hi Chromium hai — DIRECT load: cookies, JS, redirects, login sab
        // native browser jaisa chalega. (Purana proxy-fetch approach Google jaise
        // JS-heavy sites ko todta tha — browser browser ki tarah chalna chahiye.)
        wv.loadUrl(url)
        refreshOmnibox()
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
                refreshOmnibox()
                renderTabStrip()
            }
        }

        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME
    }

    private inner class OrbitWebViewClient(val tab: Tab) : WebViewClient() {
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
                refreshOmnibox()
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (view == null || url.isNullOrEmpty() || tab.isInternal) return
            tab.url = url
            tab.title = view.title?.ifBlank { null } ?: domainOf(url)
            refreshOmnibox()
            renderTabStrip()
            if (!privateMode) LocalStore.addHistory(this@MainActivity, url, tab.title)
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            // sirf main frame — subresource errors page todte nahi
            if (request?.isForMainFrame == true) {
                showErrorPage(view ?: return, request.url.toString(), error?.description?.toString() ?: "Page load nahi hua")
            }
        }
    }

    // ---------------------------------------------------------------- chrome (omnibox etc.)

    private fun refreshOmnibox() {
        val tab = activeTab ?: return
        omnibox.setText(if (tab.isInternal) tab.url else tab.url)
        omnibox.clearFocus()
    }

    private fun domainOf(url: String): String = runCatching {
        java.net.URI(url).host ?: url
    }.getOrDefault(url)

    private fun toggleBookmark() {
        val tab = activeTab ?: return
        if (tab.isInternal) {
            Toast.makeText(this, "Internal page bookmark nahi hota", Toast.LENGTH_SHORT).show()
            return
        }
        val added = LocalStore.toggleBookmark(this, tab.url, tab.title)
        Toast.makeText(this, if (added) "Bookmark ho gaya" else "Bookmark hat gaya", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- dialogs (history/bookmarks/settings/menu/tabs)

    private fun showTabSwitcher() {
        val items = tabs.map { it.title.ifBlank { domainOf(it.url) } }.toTypedArray()
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Tabs (${tabs.size})")
            .setItems(items) { _, which -> selectTab(tabs[which].id) }
            .setPositiveButton("Naya tab") { _, _ -> newTab() }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun showMenu() {
        val opts = arrayOf("New tab", "AI Search", "Bookmarks", "History", "Settings", "Check update", "About Orbit")
        AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Orbit")
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> newTab()
                    1 -> showAiSearchPrompt()
                    2 -> showBookmarks()
                    3 -> showHistory()
                    4 -> showSettings()
                    5 -> checkForUpdate(silent = false)
                    6 -> showAbout()
                }
            }
            .show()
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
                    "• Real browser: Chromium engine, Google search built-in\n" +
                    "• Aapka data device par hi — koi server nahi\n" +
                    "• AI search optional (menu -> AI Search, Settings me backend set karo)\n" +
                    "• OTA updates: background download, ek tap install\n\n" +
                    "v${BuildConfig.VERSION_NAME}",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkForUpdate(silent: Boolean) {
        Thread {
            val latest = UpdateChecker.fetchLatest()
            runOnUiThread {
                if (latest == null) {
                    if (!silent) Toast.makeText(this, "Update check nahi ho paya (network?)", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                if (UpdateChecker.isUpdateAvailable(latest)) {
                    AlertDialog.Builder(this, R.style.OrbitDialog)
                        .setTitle("Orbit ${latest.versionName} available hai")
                        .setMessage(
                            (latest.notes.ifBlank { "Naya version ready hai." }) +
                                "\n\n• Background me download hoga, phir INSTALL ka button khud khul jayega\n" +
                                "• Install hote hi purana version REPLACE ho jayega (aapka data safe rahega)",
                        )
                        .setPositiveButton("Update karo") { _, _ -> UpdateChecker.downloadAndInstall(this, latest) }
                        .setNegativeButton("Baad me", null)
                        .show()
                } else if (!silent) {
                    Toast.makeText(this, "Orbit up-to-date hai (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
    }
}
