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

    /** v1.4.0 — history page me single entry delete (index = listHistory ka index) */
    fun removeHistoryAt(ctx: Context, index: Int) {
        val items = listHistory(ctx)
        if (index !in items.indices) return
        items.removeAt(index)
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("ts", it.ts)) }
        file(ctx, "history.json").writeText(arr.toString())
    }

    /** MODULE 4 (sync) — custom ts ke saath upsert (remote merge apply) */
    fun putHistory(ctx: Context, url: String, title: String, ts: Long) {
        val items = listHistory(ctx)
        val ex = items.firstOrNull { it.url == url }
        if (ex != null) {
            if (ts <= ex.ts) return
            items.remove(ex)
        }
        items.add(0, HistoryItem(url, title, ts))
        val arr = JSONArray()
        items.take(MAX_HISTORY).forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("ts", it.ts)) }
        file(ctx, "history.json").writeText(arr.toString())
    }

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

    /** MODULE 4 (sync) — newer-ts upsert (remote merge apply) */
    fun putBookmark(ctx: Context, url: String, title: String, ts: Long) {
        val items = listBookmarks(ctx)
        val ex = items.firstOrNull { it.url == url }
        if (ex != null) {
            if (ts <= ex.ts) return
            items.remove(ex)
            items.add(0, Bookmark(url, title.ifBlank { url }, ts))
            saveBookmarks(ctx, items)
            return
        }
        items.add(0, Bookmark(url, title.ifBlank { url }, ts))
        saveBookmarks(ctx, items)
    }

    /** MODULE 4 (sync) — tombstone apply (remote deletion) */
    fun dropBookmark(ctx: Context, url: String) {
        val items = listBookmarks(ctx)
        val ex = items.firstOrNull { it.url == url } ?: return
        items.remove(ex)
        saveBookmarks(ctx, items)
    }

    /** v1.4.0 — bookmarks page me single delete (index = listBookmarks ka index) */
    fun removeBookmarkAt(ctx: Context, index: Int) {
        val items = listBookmarks(ctx)
        if (index !in items.indices) return
        items.removeAt(index)
        saveBookmarks(ctx, items)
    }

    // ---------- v1.4.4 downloads registry ----------

    /** Orbit downloader ka persistent record — downloads.json me. Live running
     *  tasks ka authoritative state OrbitDownloader memory me hota hai; ye store
     *  Downloads page ko restarts ke across populate karta hai. */
    data class DownloadItem(
        val id: Long,
        val url: String,
        val name: String,
        val mime: String,
        val total: Long,
        val received: Long,
        val status: String, // running | paused | done | failed
        val uri: String,    // content:// (MediaStore) ya file path
        val ts: Long,
    )

    fun listDownloads(ctx: Context): MutableList<DownloadItem> {
        val f = file(ctx, "downloads.json")
        val out = mutableListOf<DownloadItem>()
        if (!f.exists()) return out
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    DownloadItem(
                        o.getLong("id"), o.getString("url"), o.getString("name"),
                        o.getString("mime"), o.getLong("total"), o.getLong("received"),
                        o.getString("status"), o.optString("uri"), o.getLong("ts"),
                    )
                )
            }
        }
        return out
    }

    fun upsertDownload(ctx: Context, item: DownloadItem) {
        val items = listDownloads(ctx)
        val ex = items.firstOrNull { it.id == item.id }
        if (ex != null) items[items.indexOf(ex)] = item else items.add(0, item)
        saveDownloads(ctx, items)
    }

    fun removeDownload(ctx: Context, id: Long) {
        val items = listDownloads(ctx)
        items.removeAll { it.id == id }
        saveDownloads(ctx, items)
    }

    /** Downloads page ke header trash — done/failed entries saaf, running/paused untouched */
    fun clearFinishedDownloads(ctx: Context) {
        val items = listDownloads(ctx)
        items.removeAll { it.status == "done" || it.status == "failed" }
        saveDownloads(ctx, items)
    }

    fun saveDownloads(ctx: Context, items: List<DownloadItem>) {
        val arr = JSONArray()
        items.take(200).forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id).put("url", d.url).put("name", d.name)
                    .put("mime", d.mime).put("total", d.total).put("received", d.received)
                    .put("status", d.status).put("uri", d.uri).put("ts", d.ts)
            )
        }
        file(ctx, "downloads.json").writeText(arr.toString())
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
