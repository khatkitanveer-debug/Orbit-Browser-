package com.orbit.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Orbit VPN — free community relay network (v1.4.0).
 *
 * Tor-jaisa browser-scoped relay: WebView ka saara http(s) traffic ek public
 * community relay ke through jata hai — websites ko relay ka IP dikhta hai,
 * user ka nahi. Browser-scoped hai (system-wide nahi) — Tor browser bhi apne
 * browser traffic ko hi route karta hai.
 *
 * Free relay sources (public, no login, no key):
 *   • proxylist.geonode.com  — JSON + country + uptime
 *   • TheSpeedX/PROXY-List   — raw ip:port (GitHub)
 *   • proxyscrape.com        — raw ip:port
 *
 * Validation har relay par:
 *   1) TCP connect latency (parallel, 1.5s cap)
 *   2) REAL "CONNECT example.com:443" handshake — sirf wahi relay chunta hai
 *      jo HTTPS tunneling allow karta hai (warna browsing toot jati)
 *
 * Proxy override androidx.webkit ProxyController se hota hai — sirf Orbit ke
 * WebViews par lagta hai. Rule list me DIRECT fallback always hai, isliye
 * relay mar bhi jaye to browsing kabhi black-hole nahi hoti.
 */
object RelayEngine {

    data class Relay(val host: String, val port: Int, val country: String, val latencyMs: Int)

    /** off | connecting | on */
    @Volatile var state: String = "off"
    @Volatile var statusText: String = "Not connected"
    @Volatile var relays: List<Relay> = emptyList()
    @Volatile var activeRelay: Relay? = null

    /** UI hook (main thread) — sheet/menu/settings switches refresh karne ke liye */
    var onUiChanged: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    // v1.4.4 — SLOW SITE FIX: free relays kabhi-kabhi degrade ho jate hain (kisi
    // website par slow response). Watchdog har 8 min me active relay ki health
    // re-check karta hai; handshake fail ya >1200ms ho to auto-refresh (faster
    // relay swap). Page-stall par MainActivity swapIfSlow() bhi bula sakta hai
    // (3 min throttle — refresh storm na ho).
    @Volatile private var appCtx: Context? = null
    @Volatile private var lastSwap = 0L
    @Volatile private var watchdogRun = false
    private var watchdogTask: Runnable? = null

    private fun startWatchdog() {
        watchdogRun = true
        watchdogTask?.let { main.removeCallbacks(it) }
        val task = object : Runnable {
            override fun run() {
                if (!watchdogRun) return
                val r = activeRelay ?: return
                Thread {
                    val t0 = System.currentTimeMillis()
                    val ok = runCatching { connectHandshakeOk(r.host, r.port) }.getOrDefault(false)
                    val ms = System.currentTimeMillis() - t0
                    if (watchdogRun && (!ok || ms > 1200)) {
                        lastSwap = System.currentTimeMillis()
                        appCtx?.let { ctx ->
                            statusText = "Relay slow — switching to a faster one…"
                            notifyUi()
                            refresh(ctx, true)
                        }
                    }
                }.also { it.isDaemon = true }.start()
                main.postDelayed(this, 8 * 60 * 1000L)
            }
        }
        watchdogTask = task
        main.postDelayed(task, 8 * 60 * 1000L)
    }

    private fun stopWatchdog() {
        watchdogRun = false
        watchdogTask?.let { main.removeCallbacks(it) }
        watchdogTask = null
    }

    /** Page-stall hook — 9s me page 15% cross na kare to relay swap try karo
     *  (throttled: 3 min me max ek baar). VPN OFF par koi asar nahi. */
    fun swapIfSlow(ctx: Context) {
        if (state != "on") return
        val now = System.currentTimeMillis()
        if (now - lastSwap < 3 * 60 * 1000L) return
        lastSwap = now
        statusText = "Site slow — trying a faster relay…"
        notifyUi()
        refresh(ctx, true)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ---- cached relay list (1h fresh — re-connect fast) ----
    private const val CACHE_MAX_AGE = 60 * 60 * 1000L

    fun isEnabled(ctx: Context): Boolean =
        LocalStore.getSetting(ctx, "vpn", "off") == "on"

    fun proxySupported(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
    }.getOrDefault(false)

    /** App start par — agar user ne ON chhoda tha to silently wapas connect. */
    fun autoRestore(ctx: Context) {
        if (isEnabled(ctx) && state == "off") connect(ctx)
    }

    fun connect(ctx: Context) {
        appCtx = ctx.applicationContext
        LocalStore.setSetting(ctx, "vpn", "on")
        if (!proxySupported()) {
            state = "off"
            statusText = "This WebView doesn't support proxy routing"
            notifyUi()
            return
        }
        worker?.interrupt()
        state = "connecting"
        statusText = "Finding the fastest relays…"
        notifyUi()
        worker = Thread { runCatching { pipeline() } }.also { it.start() }
    }

    fun disconnect(ctx: Context) {
        LocalStore.setSetting(ctx, "vpn", "off")
        stopWatchdog()
        worker?.interrupt()
        worker = null
        state = "off"
        statusText = "Not connected"
        activeRelay = null
        notifyUi()
        main.post { clearProxy() }
    }

    /** ProxyController (webkit 1.11.0) signature: (ProxyConfig, Executor, Runnable) —
     *  completion callback Runnable hai, ValueCallback nahi. Explicit SAM vals —
     *  inline lambdas Kotlin SAM-overload inference todenge. */
    private val directExecutor = Executor { r -> r.run() }
    private val noopCallback = Runnable { }

    private fun clearProxy() {
        runCatching { ProxyController.getInstance().clearProxyOverride(directExecutor, noopCallback) }
    }

    /** Manual refresh — list dobara fetch + test karo (on ho to re-route bhi). */
    fun refresh(ctx: Context, wasOn: Boolean) {
        relays = emptyList()
        if (wasOn) connect(ctx)
        else {
            state = "connecting"
            statusText = "Refreshing relay list…"
            notifyUi()
            worker?.interrupt()
            worker = Thread { runCatching { pipeline(testOnly = true) } }.also { it.start() }
        }
    }

    /** Sheet me kisi relay row par tap — usi ko active bana do. */
    fun useRelay(relay: Relay) {
        state = "on"
        activeRelay = relay
        statusText = "Protected · ${relay.country} · ${relay.latencyMs}ms"
        notifyUi()
        applyProxy(relay)
        startWatchdog()
    }

    // ------------------------------------------------------------ pipeline

    private fun pipeline(testOnly: Boolean = false) {
        val tested = validateCandidates()
        relays = tested
        if (tested.isEmpty()) {
            state = "off"
            statusText = "No working relay right now — tap Refresh"
            notifyUi()
            return
        }
        if (testOnly) {
            state = if (activeRelay != null) "on" else "off"
            statusText = if (activeRelay != null) {
                val r = activeRelay!!
                "Protected · ${r.country} · ${r.latencyMs}ms"
            } else "Not connected — turn the switch on"
            notifyUi()
            return
        }
        // best (sabse fast CONNECT-ok) relay activate
        useRelay(tested[0])
    }

    private fun validateCandidates(): List<Relay> {
        val candidates = fetchCandidates()
        if (candidates.isEmpty()) return emptyList()
        statusText = "Testing ${candidates.size} relays…"
        notifyUi()

        // 1) parallel TCP-latency filter (cap 1500ms)
        val pool = Executors.newFixedThreadPool(14)
        val fast: List<Relay> = try {
            val futures = candidates.map { c ->
                pool.submit(java.util.concurrent.Callable<Relay?> {
                    val t0 = System.currentTimeMillis()
                    val s = Socket()
                    try {
                        s.connect(InetSocketAddress(c.host, c.port), 1500)
                        s.close()
                        Relay(c.host, c.port, c.country, (System.currentTimeMillis() - t0).toInt())
                    } catch (e: Exception) {
                        try { s.close() } catch (e2: Exception) {}
                        null
                    }
                })
            }
            val results: List<Relay?> = futures.map { f -> runCatching { f.get() }.getOrNull() }
            results.filterNotNull().sortedBy { it.latencyMs }.take(24)
        } finally {
            pool.shutdown()
        }

        // 2) REAL CONNECT handshake — top 24 me se pehle 6 jo HTTPS tunnel dete hain
        val ok = mutableListOf<Relay>()
        for (r in fast) {
            if (ok.size >= 6) break
            if (connectHandshakeOk(r.host, r.port)) ok.add(r)
        }
        saveCache(ok)
        return ok
    }

    /** CONNECT example.com:443 HTTP/1.1 — "200" mile to HTTPS tunneling works. */
    private fun connectHandshakeOk(host: String, port: Int): Boolean {
        var s: Socket? = null
        return try {
            s = Socket()
            s.connect(InetSocketAddress(host, port), 1500)
            s.soTimeout = 4000
            val out = s.getOutputStream()
            out.write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n".toByteArray())
            out.flush()
            val buf = ByteArray(256)
            val n = s.getInputStream().read(buf)
            val head = String(buf, 0, maxOf(0, n))
            head.contains(" 200")
        } catch (e: Exception) {
            false
        } finally {
            runCatching { s?.close() }
        }
    }

    // ------------------------------------------------------------ sources

    private fun fetchCandidates(): List<Relay> {
        val fresh = loadCache()
        if (fresh.isNotEmpty()) return fresh

        val seen = HashSet<String>()
        val out = mutableListOf<Relay>()
        fun add(r: Relay?) {
            if (r == null) return
            val key = r.host + ":" + r.port
            if (seen.add(key) && out.size < 120) out.add(r)
        }
        // 3 sources parallel — jo bhi pehle aaye wo hi hai (phone par bhi 8s cap)
        val t1 = Thread { runCatching { fetchGeonode().forEach { add(it) } } }
        val t2 = Thread { runCatching { fetchPlain("https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt", "Relay").forEach { add(it) } } }
        val t3 = Thread { runCatching { fetchPlain("https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=8000&country=all", "Relay").forEach { add(it) } } }
        t1.start(); t2.start(); t3.start()
        t1.join(8000); t2.join(8000); t3.join(8000)
        return out
    }

    /** geonode JSON — country milta hai isliye primary source */
    private fun fetchGeonode(): List<Relay> {
        val url = "https://proxylist.geonode.com/api/proxy-list?limit=50&page=1&sort_by=lastChecked&sort_type=desc&protocols=http%2Chttps"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string() ?: return emptyList()
        }
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<Relay>()
        for (i in 0 until minOf(arr.length(), 50)) {
            val o = arr.optJSONObject(i) ?: continue
            val ip = o.optString("ip", "").trim()
            val port = o.optString("port", "").trim().toIntOrNull() ?: continue
            if (ip.isEmpty() || port !in 1..65535) continue
            out.add(Relay(ip, port, o.optString("country", "??").uppercase(), 0))
        }
        return out
    }

    /** raw ip:port lists */
    private fun fetchPlain(url: String, country: String): List<Relay> {
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string() ?: return emptyList()
        }
        val out = mutableListOf<Relay>()
        for (line in body.lines()) {
            val t = line.trim()
            val idx = t.lastIndexOf(':')
            if (idx <= 0 || idx == t.length - 1) continue
            val host = t.substring(0, idx)
            val port = t.substring(idx + 1).toIntOrNull() ?: continue
            if (host.isEmpty() || port !in 1..65535) continue
            out.add(Relay(host, port, country, 0))
            if (out.size >= 60) break
        }
        return out
    }

    // ------------------------------------------------------------ cache + proxy apply

    private fun cacheFile(ctx: Context) = java.io.File(ctx.filesDir, "vpn_relays.json")

    private fun loadCache(): List<Relay> = try {
        // RelayEngine object ke paas Context nahi hota — MainActivity connect() se
        // pehle setRelayCacheDir() call hota hai. Direct read fail = empty list.
        val f = cacheDirRef
        if (f == null || !f.exists()) emptyList()
        else {
            val o = JSONObject(f.readText())
            if (System.currentTimeMillis() - o.optLong("ts", 0) > CACHE_MAX_AGE) emptyList()
            else {
                val arr = o.optJSONArray("relays") ?: emptyListSafe()
                (0 until minOf(arr.length(), 10)).mapNotNull { i ->
                    val r = arr.optJSONObject(i) ?: return@mapNotNull null
                    Relay(r.optString("host"), r.optInt("port"), r.optString("country", "Relay"), r.optInt("latency", 0))
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun emptyListSafe(): org.json.JSONArray = org.json.JSONArray()

    private fun saveCache(list: List<Relay>) {
        val f = cacheDirRef ?: return
        runCatching {
            val arr = org.json.JSONArray()
            list.forEach { r -> arr.put(JSONObject().put("host", r.host).put("port", r.port).put("country", r.country).put("latency", r.latencyMs)) }
            f.writeText(JSONObject().put("ts", System.currentTimeMillis()).put("relays", arr).toString())
        }
    }

    fun setRelayCacheDir(ctx: Context) {
        cacheDirRef = java.io.File(ctx.filesDir, "vpn_relays.json")
    }

    @Volatile
    private var cacheDirRef: java.io.File? = null

    /** WebView (sab tabs) ka traffic is relay se route karo + DIRECT fallback. */
    private fun applyProxy(relay: Relay) {
        main.post {
            runCatching {
                val cfg = ProxyConfig.Builder()
                    .addProxyRule("${relay.host}:${relay.port}")
                    .addDirect()
                    .build()
                ProxyController.getInstance().setProxyOverride(cfg, directExecutor, noopCallback)
            }
        }
    }

    private fun notifyUi() {
        main.post { runCatching { onUiChanged?.invoke() } }
    }
}
