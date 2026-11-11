package com.orbit.browser.plugins

import android.view.ViewGroup
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.orbit.browser.render.ContentHost
import androidx.core.content.ContextCompat

/** PageSnapshot → JS event payload. Contract twin of src/core/orbit.ts. */
private fun ContentHost.PageSnapshot.toJS(): JSObject =
    JSObject()
        .put("url", url)
        .put("title", title)
        .put("progress", progress)
        .put("loading", loading)
        .put("canGoBack", canGoBack)
        .put("canGoForward", canGoForward)
        .put("crashed", crashed)

/**
 * OrbitRender — Module 1 native bridge.
 * Tiny on purpose: chrome/content slot sync + navigation primitives only.
 * UI stays in React; rendering stays native; this is the only seam.
 */
@CapacitorPlugin(name = "OrbitRender")
class OrbitRenderPlugin : Plugin() {

    private var host: ContentHost? = null

    private fun ensureHost(ephemeral: Boolean): ContentHost {
        val existing = host
        if (existing != null && existing.ephemeral != ephemeral) {
            // Mode switch (normal ↔ incognito): the old renderer must die
            // first — its cache/cookies/etc. must not leak into the other.
            existing.destroy()
            host = null
        }
        host?.let { return it }
        val root = bridge.activity.findViewById<ViewGroup>(android.R.id.content)
        val created = ContentHost(bridge.activity, root, object : ContentHost.Listener {
            override fun onPageState(state: ContentHost.PageSnapshot) {
                notifyListeners("page", state.toJS())
            }
        }, ephemeral)
        host = created
        return created
    }

    @PluginMethod
    fun open(call: PluginCall) {
        val url = call.getString("url")
        if (url.isNullOrBlank()) {
            call.reject("url is required")
            return
        }
        val topPx = call.getInt("topPx") ?: 0
        val ephemeral = call.getBoolean("ephemeral") == true
        bridge.activity.runOnUiThread {
            val h = ensureHost(ephemeral)
            h.setTopInset(topPx)
            h.load(url)
            call.resolve()
        }
    }

    @PluginMethod
    fun setSlotInset(call: PluginCall) {
        val topPx = call.getInt("topPx")
        if (topPx == null || topPx < 0) {
            call.reject("topPx must be a non-negative integer")
            return
        }
        bridge.activity.runOnUiThread {
            host?.setTopInset(topPx)
            call.resolve()
        }
    }

    @PluginMethod
    fun goBack(call: PluginCall) {
        val consumed = host?.goBack() ?: false
        call.resolve(JSObject().put("consumed", consumed))
    }

    @PluginMethod
    fun goForward(call: PluginCall) {
        val consumed = host?.goForward() ?: false
        call.resolve(JSObject().put("consumed", consumed))
    }

    @PluginMethod
    fun reload(call: PluginCall) {
        bridge.activity.runOnUiThread {
            host?.reload()
            call.resolve()
        }
    }

    @PluginMethod
    fun close(call: PluginCall) {
        bridge.activity.runOnUiThread {
            host?.destroy()
            host = null
            call.resolve()
        }
    }

    @PluginMethod
    fun getState(call: PluginCall) {
        val snapshot = host?.snapshot()
        if (snapshot == null) call.resolve() else call.resolve(snapshot.toJS())
    }

    /**
     * Module 4 — Tor/Orbot routing. Rules like "127.0.0.1:8118" (Orbot's
     * HTTP port) route ALL WebView traffic through the proxy; an empty
     * list clears the override. Chromium note: the proxy override is
     * app-wide for the WebView stack — the React layer only enables it
     * for incognito sessions and clears it on exit.
     */
    @PluginMethod
    fun setProxy(call: PluginCall) {
        val rules = call.getArray("rules")?.toList()
            ?.filterIsInstance<String>()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            call.reject("Proxy override is not supported on this WebView")
            return
        }
        val executor = ContextCompat.getMainExecutor(bridge.context)
        val controller = ProxyController.getInstance()
        try {
            if (rules.isEmpty()) {
                controller.clearProxyOverride(executor) { call.resolve() }
            } else {
                val builder = ProxyConfig.Builder()
                for (rule in rules) builder.addProxyRule(rule)
                controller.setProxyOverride(builder.build(), executor) { call.resolve() }
            }
        } catch (e: Exception) {
            call.reject("Proxy update failed: ${e.message}")
        }
    }

    override fun handleOnDestroy() {
        host?.destroy()
        host = null
    }
}
