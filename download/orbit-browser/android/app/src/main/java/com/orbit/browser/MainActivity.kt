package com.orbit.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu

class MainActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var etTopUrl: EditText
    private lateinit var btnMenu: ImageButton
    private lateinit var webView: WebView
    private lateinit var homeContainer: LinearLayout
    private lateinit var etCenterSearch: EditText
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View Binding
        topBar = findViewById(R.id.topBar)
        etTopUrl = findViewById(R.id.etTopUrl)
        btnMenu = findViewById(R.id.btnMenu)
        webView = findViewById(R.id.webView)
        homeContainer = findViewById(R.id.homeContainer)
        etCenterSearch = findViewById(R.id.etCenterSearch)
        progressBar = findViewById(R.id.progressBar)

        configureWebView()
        setupSearchListeners()
        setupMenuListener()
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                etTopUrl.setText(url)
                showWebState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etTopUrl.setText(url)
            }
        }
    }

    private fun setupSearchListeners() {
        // Center Omnibox Search listener (Homepage)
        etCenterSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = etCenterSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    loadQueryOrUrl(query)
                }
                true
            } else false
        }

        // Top App Bar Search/URL listener (Browsing Mode)
        etTopUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = etTopUrl.text.toString().trim()
                if (query.isNotEmpty()) {
                    loadQueryOrUrl(query)
                }
                true
            } else false
        }
    }

    private fun loadQueryOrUrl(input: String) {
        val isUrl = android.util.Patterns.WEB_URL.matcher(input).matches() || 
                    input.startsWith("http://") || 
                    input.startsWith("https://")

        val targetUrl = if (isUrl) {
            if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        } else {
            "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
        }

        showWebState()
        webView.loadUrl(targetUrl)
    }

    private fun showWebState() {
        homeContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showHomeState() {
        webView.visibility = View.GONE
        homeContainer.visibility = View.VISIBLE
        etTopUrl.setText("")
        etCenterSearch.setText("")
    }

    private fun setupMenuListener() {
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.apply {
                add(0, 1, 0, "New tab")
                add(0, 2, 1, "New incognito tab")
                add(0, 3, 2, "History")
                add(0, 4, 3, "Downloads")
                add(0, 5, 4, "Bookmarks")
                add(0, 6, 5, "Desktop site")
                add(0, 7, 6, "Orbit Shield")
                add(0, 8, 7, "Settings")
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showHomeState()
                    else -> true
                }
                true
            }
            popup.show()
        }
    }

    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else if (webView.visibility == View.VISIBLE) {
            showHomeState()
        } else {
            super.onBackPressed()
        }
    }
}
