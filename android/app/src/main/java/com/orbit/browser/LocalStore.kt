package com.orbit.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Orbit ka 100% LOCAL storage — history/bookmarks/settings sab [filesDir] me
 * JSON files ke roop me. Koi server nahi, koi cloud nahi (jab tak user khud na chahe).
 */
object LocalStore {

    data class HistoryItem(val url: String, val title: String, val ts: Long)
    data class Bookmark(val url: String, val title: String, val ts: Long)

    private const val MAX_HISTORY = 500

    private fun file(ctx: Context, name: String): File = File(ctx.filesDir, name)

    // ---------- history ----------

    fun listHistory(ctx: Context): MutableList<HistoryItem> {
        val f = file(ctx, "history.json")
        val out = mutableListOf<HistoryItem>()
        if (!f.exists()) return out
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(HistoryItem(o.getString("url"), o.getString("title"), o.getLong("ts")))
            }
        }
        return out
    }

    fun addHistory(ctx: Context, url: String, title: String) {
        val items = listHistory(ctx)
        items.add(0, HistoryItem(url, title, System.currentTimeMillis()))
        val arr = JSONArray()
        items.take(MAX_HISTORY).forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("ts", it.ts)) }
        file(ctx, "history.json").writeText(arr.toString())
    }

    fun clearHistory(ctx: Context) = file(ctx, "history.json").delete()

    // ---------- bookmarks ----------

    fun listBookmarks(ctx: Context): MutableList<Bookmark> {
        val f = file(ctx, "bookmarks.json")
        val out = mutableListOf<Bookmark>()
        if (!f.exists()) return out
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Bookmark(o.getString("url"), o.getString("title"), o.getLong("ts")))
            }
        }
        return out
    }

    fun isBookmarked(ctx: Context, url: String): Boolean =
        listBookmarks(ctx).any { it.url == url }

    /** @return true agar ab bookmark hai (add hua), false agar hat gaya */
    fun toggleBookmark(ctx: Context, url: String, title: String): Boolean {
        val items = listBookmarks(ctx)
        val existing = items.firstOrNull { it.url == url }
        return if (existing != null) {
            items.remove(existing)
            saveBookmarks(ctx, items)
            false
        } else {
            items.add(0, Bookmark(url, title.ifBlank { url }, System.currentTimeMillis()))
            saveBookmarks(ctx, items)
            true
        }
    }

    private fun saveBookmarks(ctx: Context, items: List<Bookmark>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("ts", it.ts)) }
        file(ctx, "bookmarks.json").writeText(arr.toString())
    }

    // ---------- settings ----------

    // SETTINGS CACHE — shouldInterceptRequest HAR subresource request par
    // getSetting() bulata hai (ek page = 100+ requests). Bina cache ke har
    // request par disk read + JSON parse hota tha — jank/ANR ka source.
    private val settingsCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getSetting(ctx: Context, key: String, def: String): String {
        settingsCache[key]?.let { return it }
        val f = file(ctx, "settings.json")
        if (!f.exists()) return def
        val v = runCatching { JSONObject(f.readText()).optString(key, def) }.getOrDefault(def)
        settingsCache[key] = v
        return v
    }

    fun setSetting(ctx: Context, key: String, value: String) {
        settingsCache[key] = value
        val f = file(ctx, "settings.json")
        val obj = if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject()) else JSONObject()
        obj.put(key, value)
        f.writeText(obj.toString())
    }
}
