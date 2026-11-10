package com.orbit.browser

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Orbit ka native page proxy — web version (/api/proxy) ka Kotlin port.
 * Site ka HTML fetch karke rewrite karta hai: CSP meta hatata hai, <base> inject
 * karta hai, aur link/form interception script daalta hai jo OrbitBridge ke through
 * navigation wapas app me bhejta hai. Koi iframe nahi — direct render.
 */
object ProxyEngine {

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    class Result(val ok: Boolean, val html: String?, val finalUrl: String?, val error: String?, val contentType: String?)

    fun isPrivateHost(url: String): Boolean = runCatching {
        val h = java.net.URI(url).host?.lowercase() ?: return@runCatching true
        h == "localhost" || h.endsWith(".local") || h.endsWith(".internal") ||
            h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")
    }.getOrDefault(true)

    fun fetch(url: String): Result {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
                .build()
            client.newCall(req).execute().use { resp ->
                val ctype = resp.header("Content-Type") ?: "text/html"
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return Result(false, null, null, "HTTP ${resp.code}", ctype)
                }
                val isHtml = ctype.contains("text/html") || ctype.contains("xhtml")
                if (!isHtml) {
                    // HTML nahi (image/pdf/json) — caller direct URL load karega
                    return Result(true, null, url, null, ctype)
                }
                Result(true, transform(body, url), url, null, ctype)
            }
        } catch (e: Exception) {
            Result(false, null, null, e.message ?: "network error", null)
        }
    }

    /** HTML rewrite: CSP/meta remove, base inject, bridge script inject */
    fun transform(html: String, baseUrl: String): String {
        var out = html
        // CSP / frame-blocking metas hatao
        out = out.replace(Regex("<meta[^>]+http-equiv=[\"']?(content-security-policy|x-frame-options)[\"']?[^>]*>", RegexOption.IGNORE_CASE), "")
        // <base> inject (relative resources origin se hi load hon)
        val baseTag = "<base href=\"$baseUrl\">"
        out = if (out.contains("<head>", true)) {
            out.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>$baseTag")
        } else {
            baseTag + out
        }
        // bridge script sabse aakhir me
        val script = "<script>(function(){\n" + interceptScript() + "\n})();</script>"
        out = if (out.contains("</body>", true)) {
            out.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), script + "</body>")
        } else {
            out + script
        }
        return out
    }

    /**
     * Links/forms intercept karke OrbitBridge.navigate(url) bhejo.
     * External target=_blank / ctrl-click bhi app ke naye tab me khulega.
     */
    private fun interceptScript(): String = """
    function abs(u){try{return new URL(u, document.baseURI).href}catch(e){return null}}
    document.addEventListener('click', function(e){
      var el = e.target; while (el && el.nodeType === 1 && el.tagName !== 'A') { el = el.parentElement; }
      if (!el) return;
      var href = el.getAttribute('href');
      if (!href || href.charAt(0) === '#' || href.indexOf('javascript:') === 0) return;
      e.preventDefault(); e.stopPropagation();
      var u = abs(href); if (!u) return;
      var nt = (el.target === '_blank') || e.ctrlKey || e.metaKey;
      try { OrbitBridge.navigate(u, nt); } catch (err) { location.href = u; }
    }, true);
    document.addEventListener('submit', function(e){
      var f = e.target; if (!f || f.tagName !== 'FORM') return;
      var m = (f.getAttribute('method') || 'get').toLowerCase(); if (m !== 'get') return;
      e.preventDefault();
      try {
        var act = f.getAttribute('action') || document.baseURI;
        var u = new URL(act, document.baseURI);
        var fd = new FormData(f); var qs = new URLSearchParams();
        fd.forEach(function(v, k){ if (typeof v === 'string') qs.append(k, v); });
        u.search = qs.toString();
        OrbitBridge.navigate(u.href, false);
      } catch (err) {}
    }, true);
    """.trimIndent()
}
