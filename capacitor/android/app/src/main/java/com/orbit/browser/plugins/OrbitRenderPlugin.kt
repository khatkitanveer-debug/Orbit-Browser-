package com.orbit.browser.plugins

import android.view.ViewGroup
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.orbit.browser.render.ContentHost

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

    private fun ensureHost(): ContentHost {
        host?.let { return it }
        val root = bridge.activity.findViewById<ViewGroup>(android.R.id.content)
        val created = ContentHost(bridge.activity, root, object : ContentHost.Listener {
            override fun onPageState(state: ContentHost.PageSnapshot) {
                notifyListeners("page", state.toJS())
            }
        })
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
        bridge.activity.runOnUiThread {
            val h = ensureHost()
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

    override fun handleOnDestroy() {
        host?.destroy()
        host = null
    }
}
