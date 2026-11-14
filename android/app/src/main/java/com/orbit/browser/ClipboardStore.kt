package com.orbit.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v1.9.4 UC TOOLS MODULE — Clipboard history (UC Browser "Clipboard" parity).
 *
 * App focus me jab bhi kuch copy hota hai (page text, link) — entry capture
 * hoti hai. Android 10+ limitation: background apps clipboard nahi padh sakte,
 * isliye capture sirf jab Orbit foreground me hai (browsers sab yahi karte hai).
 *
 * Storage: filesDir/clipboard.json — dedup (same text dobara nahi), cap 30.
 * Tap = URL ho to navigate, warna omnibox/copy. UC jaisa zero-friction.
 */
object ClipboardStore {

    private class Entry(val text: String, val at: Long)

    private const val CAP = 30

    @Synchronized
    fun list(ctx: Context): List<Pair<String, Long>> {
        val f = File(ctx.filesDir, "clipboard.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.optString("t") to o.optLong("at")
            }.filter { it.first.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(ctx: Context, text: String) {
        val t = text.trim()
        if (t.isBlank() || t.length > 8000) return
        val cur = mutableListOf<Entry>()
        val f = File(ctx.filesDir, "clipboard.json")
        runCatching {
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).forEach { i ->
                    val o = arr.getJSONObject(i)
                    cur.add(Entry(o.optString("t"), o.optLong("at")))
                }
            }
        }
        // dedup — same text phir se copy hua to top par bhejo (naya timestamp)
        cur.removeAll { it.text == t }
        cur.add(0, Entry(t, System.currentTimeMillis()))
        while (cur.size > CAP) cur.removeAt(cur.size - 1)
        runCatching {
            val arr = JSONArray()
            cur.forEach { arr.put(JSONObject().put("t", it.text).put("at", it.at)) }
            f.writeText(arr.toString())
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, "clipboard.json").delete() }
    }

    @Synchronized
    fun removeAt(ctx: Context, index: Int) {
        val f = File(ctx.filesDir, "clipboard.json")
        runCatching {
            val arr = JSONArray(f.readText())
            val out = JSONArray()
            (0 until arr.length()).forEach { i -> if (i != index) out.put(arr.get(i)) }
            f.writeText(out.toString())
        }
    }

    /** Text URL jaisa dikhta hai? (clipboard tap → navigate ka decision) */
    fun looksLikeUrl(t: String): Boolean {
        val s = t.trim()
        if (s.contains(' ') || s.length > 2048) return false
        if (s.startsWith("http://") || s.startsWith("https://")) return true
        if (s.startsWith("www.") && s.contains('.')) return true
        val dot = s.indexOf('.')
        return dot in 1 until s.length - 1 &&
            !s.substring(0, dot).contains('.') &&
            s.substring(dot + 1).take(3).all { c -> c.isLetter() }
    }
}
