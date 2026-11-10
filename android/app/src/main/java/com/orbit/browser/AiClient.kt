package com.orbit.browser

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Orbit AI Search client — user ke APNE deployed Orbit web backend se baat karta hai
 * (jaise https://aapka-orbit.vercel.app). Backend /api/ai/search ka SSE protocol:
 *   data: {"type":"sources",...} -> data: {"type":"delta",...}* -> data: {"type":"done"}
 * Query sirf answer banane ke liye use hoti hai — history backend ko nahi jati.
 */
object AiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    interface Listener {
        fun onSources(json: String)
        fun onDelta(text: String)
        fun onDone()
        fun onError(message: String)
    }

    fun search(backend: String, query: String, listener: Listener) {
        val base = backend.trimEnd('/')
        val url = "$base/api/ai/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("User-Agent", ProxyEngine.UA)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    listener.onError("Backend ne HTTP ${resp.code} diya")
                    return
                }
                val src = resp.body?.source() ?: run { listener.onError("Empty response"); return }
                val sb = StringBuilder()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val payload = line.removePrefix("data:").trim()
                        val o = runCatching { org.json.JSONObject(payload) }.getOrNull() ?: continue
                        when (o.optString("type")) {
                            "sources" -> listener.onSources(payload)
                            "delta" -> {
                                val t = o.optString("text", "")
                                if (t.isNotEmpty()) listener.onDelta(t)
                            }
                            "done" -> { listener.onDone(); return }
                            "error" -> { listener.onError(o.optString("message", "unknown")); return }
                        }
                    }
                }
                if (sb.isNotEmpty()) listener.onDone() else listener.onDone()
            }
        } catch (e: Exception) {
            listener.onError(e.message ?: "network error")
        }
    }
}
