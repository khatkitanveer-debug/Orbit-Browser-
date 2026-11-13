package com.orbit.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * v1.4.4 — Orbit Download Engine (Chrome-style UI + UC-style speed display).
 *
 * SPEED: multi-segment parallel download — file ko 4 parallel HTTP-Range
 * streams me todta hai (Chrome/IDM style). Single-stream fallback jab server
 * Range support na kare ya file chhoti ho. 64KB buffers + keep-alive.
 *
 * REAL-TIME UI: 500ms ticker — speed EMA-smoothed (bytes/sec), ETA seconds.
 * MainActivity inko "1.2 MB/s" + "45s left" (UC-style) format me dikhata hai.
 *
 * SAVE: API 29+ MediaStore.Downloads (public Downloads folder, koi permission
 * nahi chahiye); API 26-28 classic public dir (WRITE permission MainActivity
 * pehle maangta hai; fail ho to app-specific dir fallback).
 *
 * PAUSE/RESUME: HTTP Range + saved segment offsets (downloads/<id>/ state).
 * Registry: LocalStore.downloads — restart ke baad bhi Downloads page bhara
 * rahe; app kill par "running" records "paused" ho jate hain.
 */
object OrbitDownloader {

    const val ST_RUNNING = "running"
    const val ST_PAUSED = "paused"
    const val ST_DONE = "done"
    const val ST_FAILED = "failed"

    private const val SEG_THRESHOLD = 4L * 1024 * 1024  // 4MB se bade files par parallel
    private const val SEGMENTS = 4
    private const val BUFFER = 64 * 1024

    /** Download ka live state — UI seedha isko padhta hai. */
    class Task internal constructor(
        val id: Long,
        val url: String,
        val name: String,
        val mime: String,
        val ts: Long,
    ) {
        val total = AtomicLong(-1)      // -1 = unknown (no Content-Length)
        val received = AtomicLong(0)
        @Volatile var status: String = ST_RUNNING
        @Volatile var speed: Long = 0   // bytes/sec (EMA-smoothed)
        @Volatile var eta: Long = -1    // seconds; -1 = unknown
        @Volatile var uri: String = ""  // final location (content:// ya file path)
        @Volatile var pauseReq = false
        @Volatile var cancelReq = false
        internal var multiSeg = false
        internal val segs = mutableListOf<Seg>()
        internal lateinit var dir: File
        internal var lastSaved = 0L
    }

    internal class Seg(val index: Int, val start: Long, val end: Long) {
        @Volatile var done: Long = 0
    }

    /** Main thread hook — har 500ms tick + state change par fire. MainActivity
     *  download bar + Downloads page inko refresh karta hai. */
    @Volatile var uiHook: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val tasks = ConcurrentHashMap<Long, Task>()
    private val lastBytes = ConcurrentHashMap<Long, Long>()
    private var tickerRunning = false

    private val ua: String by lazy {
        runCatching { System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android) OrbitBrowser" }
            .getOrDefault("Mozilla/5.0 (Linux; Android) OrbitBrowser")
    }

    // ------------------------------------------------------------ lifecycle

    /** App start par — pichhli session ke "running" records ko "paused" banao
     *  (part files bachte hain, resume Range-based possible rehta hai). */
    fun init(ctx: Context) {
        val items = LocalStore.listDownloads(ctx)
        var changed = false
        items.forEach { d ->
            if (d.status == ST_RUNNING) {
                LocalStore.upsertDownload(ctx, d.copy(status = ST_PAUSED))
                changed = true
            }
        }
        if (changed) runCatching { notifyUi() }
    }

    fun runningTask(): Task? =
        tasks.values.filter { it.status == ST_RUNNING }.maxByOrNull { it.ts }
            ?: tasks.values.filter { it.status == ST_PAUSED }.maxByOrNull { it.ts }

    /** Downloads page rows — live task (ya null agar sirf persisted record hai) */
    fun taskById(id: Long): Task? = tasks[id]

    // ------------------------------------------------------------ start

    fun start(ctx: Context, url: String, name: String, mime: String): Task? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val t = Task(nextId(), url, uniqueName(name), mime.ifBlank { "application/octet-stream" }, System.currentTimeMillis())
        t.dir = File(ctx.filesDir, "downloads/${t.id}").also { it.mkdirs() }
        tasks[t.id] = t
        persist(ctx, t)
        notifyUi()
        Thread { runCatching { probe(ctx, t) } }
            .also { it.isDaemon = true }
            .start()
        return t
    }

    /** data: URLs (base64) — koi network nahi, seedha file publish. */
    fun startDataUrl(ctx: Context, dataUrl: String, name: String): Task? {
        val t = Task(nextId(), dataUrl.take(120), uniqueName(name), "application/octet-stream", System.currentTimeMillis())
        t.dir = File(ctx.filesDir, "downloads/${t.id}").also { it.mkdirs() }
        tasks[t.id] = t
        try {
            val payload = dataUrl.substringAfter("data:", "")
            val b64 = payload.substringAfter("base64,", payload)
            val bytes = android.util.Base64.decode(
                b64.substringBefore(",").trim().replace("\\s".toRegex(), ""),
                android.util.Base64.DEFAULT,
            )
            val f = File(t.dir, "final")
            f.writeBytes(bytes)
            t.total.set(bytes.size.toLong())
            t.received.set(bytes.size.toLong())
            publish(ctx, t, f)
        } catch (e: Exception) {
            fail(ctx, t, e.message ?: "Decode failed")
            tasks.remove(t.id)
            LocalStore.removeDownload(ctx, t.id)
            runCatching { t.dir.deleteRecursively() }
            return null
        }
        return t
    }

    /** Probe: size + Range support nikalo, segments banao, worker threads chalao.
     *  HEAD 405/501 de to GET + Range-0-0 fallback (Content-Range se total). */
    private fun probe(ctx: Context, t: Task) {
        var len = -1L
        var ranges = false
        val h = open(t.url) ?: return fail(ctx, t, "Cannot connect")
        try {
            h.requestMethod = "HEAD"
            h.connectTimeout = 10000
            h.readTimeout = 15000
            h.connect()
            val code = h.responseCode
            if (code in 200..299) {
                len = h.contentLengthLong
                ranges = h.getHeaderField("Accept-Ranges")?.contains("bytes") == true
            }
        } catch (e: Exception) {
            // HEAD fail (405/501/network) — GET probe fallback neeche
        } finally {
            runCatching { h.disconnect() }
        }
        if (len <= 0 || !ranges) {
            val g = open(t.url) ?: return fail(ctx, t, "Cannot connect")
            try {
                g.requestMethod = "GET"
                g.connectTimeout = 10000
                g.readTimeout = 15000
                g.setRequestProperty("Range", "bytes=0-0")
                g.connect()
                val code = g.responseCode
                if (code == 206) {
                    // "bytes 0-0/12345" → total
                    val cr = g.getHeaderField("Content-Range")
                    val tot = cr?.substringAfter('/')?.trim()?.toLongOrNull()
                    if (tot != null && tot > 0) {
                        len = tot
                        ranges = true
                    }
                } else if (code == 200) {
                    len = g.contentLengthLong
                }
            } catch (e: Exception) {
                // ignore — len/-1 par single-stream chalega
            } finally {
                runCatching { g.disconnect() }
            }
        }
        if (len > 0) t.total.set(len)
        val total = t.total.get()
        t.multiSeg = ranges && total > SEG_THRESHOLD
        if (t.multiSeg) {
            val chunk = total / SEGMENTS
            for (i in 0 until SEGMENTS) {
                val s = i * chunk
                val e = if (i == SEGMENTS - 1) total - 1 else (s + chunk - 1)
                t.segs.add(Seg(i, s, e))
            }
        } else {
            t.segs.add(Seg(0, 0, if (total > 0) total - 1 else Long.MAX_VALUE))
        }
        saveSegs(t)
        startWorkers(ctx, t)
        startTicker()
    }

    private fun startWorkers(ctx: Context, t: Task) {
        t.segs.forEach { seg ->
            // complete segment (relative done >= length) dobara spawn NAHI —
            // resume/pause cycles par zaroori (end absolute hai, done relative)
            val complete = seg.end != Long.MAX_VALUE && seg.done >= seg.end - seg.start + 1
            if (complete || t.status != ST_RUNNING) return@forEach
            Thread { runCatching { worker(ctx, t, seg) } }
                .also { it.isDaemon = true }
                .start()
        }
    }

    /** Ek segment stream — Range GET. HAR seg file me SIRF apne segment ke
     *  bytes (relative write) — assemble simple concat hai isliye. */
    private fun worker(ctx: Context, t: Task, seg: Seg) {
        val part = File(t.dir, "seg${seg.index}.part")
        var conn: HttpURLConnection? = null
        try {
            val from = seg.start + seg.done
            conn = URL(t.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", ua)
            if (seg.start > 0 || seg.done > 0) {
                conn.setRequestProperty("Range", "bytes=$from-${if (seg.end == Long.MAX_VALUE) "" else seg.end.toString()}")
            }
            conn.connect()
            val code = conn.responseCode
            val stream = when {
                code == 206 -> conn.inputStream
                // Server Range IGNORE karke 200 deta hai:
                //  • seg 0 → poora file isi stream me aayega (single-stream mode)
                //  • baaki segs → kuch nahi likhna; apne aap complete mark karo
                code == 200 -> {
                    if (seg.start > 0) {
                        seg.done = seg.end - seg.start + 1
                        maybeFinish(ctx, t)
                        return
                    }
                    t.total.set(conn.contentLengthLong.toLong())
                    conn.inputStream
                }
                code == 416 -> { seg.done = seg.end - seg.start + 1; maybeFinish(ctx, t); return }
                else -> throw java.io.IOException("HTTP $code")
            }
            val rf = RandomAccessFile(part, "rw")
            rf.setLength(seg.done)
            rf.seek(seg.done)
            val buf = ByteArray(BUFFER)
            stream.use { input ->
                rf.use { out ->
                    while (seg.end == Long.MAX_VALUE || seg.done <= seg.end - seg.start) {
                        if (t.cancelReq || t.pauseReq || t.status != ST_RUNNING) return
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        seg.done += n
                        t.received.addAndGet(n.toLong())
                    }
                }
            }
            // stream khatam — segment poora hua ya server ne beech me band kiya?
            if (seg.end == Long.MAX_VALUE || seg.done >= seg.end - seg.start + 1) {
                if (seg.end != Long.MAX_VALUE) seg.done = seg.end - seg.start + 1
                maybeFinish(ctx, t)
            } else if (!t.pauseReq && !t.cancelReq && t.status == ST_RUNNING) {
                fail(ctx, t, "Connection ended early")
            }
        } catch (e: Exception) {
            if (!t.cancelReq && !t.pauseReq && t.status == ST_RUNNING) fail(ctx, t, e.message ?: "Network error")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** Sab segments complete? → assemble + publish */
    private fun maybeFinish(ctx: Context, t: Task) {
        synchronized(t.segs) {
            val complete = t.segs.all { it.done >= it.end - it.start + 1 || it.end == Long.MAX_VALUE }
            if (!complete || t.status != ST_RUNNING) return
            // double-entry guard
            if (t.status != ST_RUNNING) return
        }
        synchronized(this) {
            if (t.status != ST_RUNNING) return
            t.status = "assembling"
        }
        try {
            val final = File(t.dir, "final")
            FileOutputStream(final).use { out ->
                t.segs.sortedBy { it.index }.forEach { seg ->
                    val f = File(t.dir, "seg${seg.index}.part")
                    // 200-fallback/empty segments (done=0, no bytes) skip — warna
                    // missing-file exception poore assemble ko maar deta
                    if (seg.done > 0 && f.exists() && f.length() > 0) {
                        f.inputStream().use { it.copyTo(out, BUFFER) }
                    }
                }
            }
            t.total.set(t.received.get())
            publish(ctx, t, final)
        } catch (e: Exception) {
            fail(ctx, t, e.message ?: "Assemble failed")
        }
    }

    /** Final file → public Downloads folder. API 29+ MediaStore; neeche classic dir. */
    private fun publish(ctx: Context, t: Task, f: File) {
        val mime = t.mime.ifBlank { "application/octet-stream" }
        var location = ""
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, t.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) throw java.io.IOException("MediaStore insert failed")
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(f).use { it.copyTo(out, BUFFER) }
                } ?: throw java.io.IOException("MediaStore stream failed")
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }.let {
                    ctx.contentResolver.update(uri, it, null, null)
                }
                location = uri.toString()
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                var out = File(dir, t.name)
                if (out.exists()) {
                    val dot = t.name.lastIndexOf('.')
                    val base = if (dot > 0) t.name.substring(0, dot) else t.name
                    val ext = if (dot > 0) t.name.substring(dot) else ""
                    var i = 1
                    while (out.exists()) { out = File(dir, "$base ($i)$ext"); i++ }
                }
                FileInputStream(f).use { it.copyTo(out.outputStream(), BUFFER) }
                location = out.absolutePath
            }
        } catch (e: Exception) {
            // public save fail (permission/edge) — app-specific dir fallback
            val fb = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), t.name)
            runCatching { FileInputStream(f).use { it.copyTo(fb.outputStream(), BUFFER) } }
            location = fb.absolutePath
        }
        t.uri = location
        t.status = ST_DONE
        t.speed = 0
        t.eta = -1
        persist(ctx, t)
        cleanupParts(t)
        main.post {
            Toast.makeText(ctx, "Download complete: ${t.name}", Toast.LENGTH_SHORT).show()
            notifyUi()
        }
    }

    private fun fail(ctx: Context, t: Task, msg: String) {
        if (t.status == ST_DONE || t.status == ST_FAILED) return
        t.status = ST_FAILED
        persist(ctx, t)
        main.post {
            Toast.makeText(ctx, "Download failed: ${t.name}", Toast.LENGTH_SHORT).show()
            notifyUi()
        }
    }

    // ------------------------------------------------------------ control

    fun pause(ctx: Context, t: Task) {
        if (t.status != ST_RUNNING) return
        t.pauseReq = true
        t.status = ST_PAUSED
        t.speed = 0
        t.eta = -1
        saveSegs(t)
        persist(ctx, t)
        notifyUi()
    }

    fun resume(ctx: Context, t: Task) {
        if (t.status != ST_PAUSED) return
        t.pauseReq = false
        t.status = ST_RUNNING
        startWorkers(ctx, t)
        startTicker()
        persist(ctx, t)
        notifyUi()
    }

    fun cancel(ctx: Context, t: Task) {
        t.cancelReq = true
        t.status = "canceled"
        tasks.remove(t.id)
        runCatching { t.dir.deleteRecursively() }
        LocalStore.removeDownload(ctx, t.id)
        notifyUi()
    }

    /** Paused record (restart ke baad) dobara chalao — segs file se offsets load. */
    fun resumeFromStore(ctx: Context, d: LocalStore.DownloadItem): Boolean {
        if (d.status != ST_PAUSED || !d.url.startsWith("http")) return false
        if (tasks.containsKey(d.id)) return false
        val t = Task(d.id, d.url, d.name, d.mime, d.ts)
        t.dir = File(ctx.filesDir, "downloads/${t.id}")
        val segFile = File(t.dir, "segs.json")
        if (!t.dir.exists() || !segFile.exists()) return false
        runCatching {
            val o = JSONObject(segFile.readText())
            t.total.set(o.optLong("total", -1))
            t.multiSeg = o.optBoolean("multi", false)
            val arr = o.optJSONArray("segs") ?: return false
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val seg = Seg(s.getInt("i"), s.getLong("s"), s.getLong("e"))
                seg.done = s.getLong("d")
                t.segs.add(seg)
            }
        }.onFailure { return false }
        t.received.set(t.segs.sumOf { it.done })
        tasks[t.id] = t
        resume(ctx, t)
        return true
    }

    // ------------------------------------------------------------ ticker (speed + ETA)

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        main.postDelayed(object : Runnable {
            override fun run() {
                val active = tasks.values.any { it.status == ST_RUNNING }
                tasks.values.forEach { t ->
                    if (t.status != ST_RUNNING) return@forEach
                    val now = System.currentTimeMillis()
                    val last = lastBytes[t.id] ?: t.received.get()
                    val delta = t.received.get() - last
                    lastBytes[t.id] = t.received.get()
                    val inst = (delta * 2).coerceAtLeast(0) // 500ms tick → per-sec
                    t.speed = if (t.speed == 0L) inst else (t.speed * 60 + inst * 40) / 100
                    val total = t.total.get()
                    t.eta = if (total > 0 && t.speed > 2048) (total - t.received.get()) / t.speed else -1
                    // har 2s par registry update (Downloads page fresh rahe)
                    if (now - t.lastSaved > 2000) t.lastSaved = now
                }
                notifyUi()
                tickerRunning = if (active) {
                    main.postDelayed(this, 500)
                    true
                } else {
                    false
                }
            }
        }, 500)
    }

    private fun notifyUi() { main.post { runCatching { uiHook?.invoke() } } }

    // ------------------------------------------------------------ persistence helpers

    private fun persist(ctx: Context, t: Task) {
        LocalStore.upsertDownload(
            ctx,
            LocalStore.DownloadItem(
                t.id, t.url, t.name, t.mime, t.total.get(), t.received.get(),
                t.status, t.uri, t.ts,
            ),
        )
    }

    private fun saveSegs(t: Task) {
        // lateinit dir abhi unset ho to chup-chaap skip (start se pehle saveSegs call nahi hota,
        // par safety-first — ::t.dir.isInitialized Kotlin me variable-ref support nahi karta)
        val dir = runCatching { t.dir }.getOrNull() ?: return
        runCatching {
            val arr = JSONArray()
            t.segs.forEach { s ->
                arr.put(JSONObject().put("i", s.index).put("s", s.start).put("e", s.end).put("d", s.done))
            }
            File(dir, "segs.json")
                .writeText(JSONObject().put("total", t.total.get()).put("multi", t.multiSeg).put("segs", arr).toString())
        }
    }

    private fun cleanupParts(t: Task) {
        runCatching {
            t.segs.forEach { File(t.dir, "seg${it.index}.part").delete() }
            File(t.dir, "segs.json").delete()
            File(t.dir, "final").delete()
        }
    }

    private fun nextId(): Long = System.currentTimeMillis()

    private fun uniqueName(name: String): String {
        var n = name.ifBlank { "orbit-download" }
        if (n == "downloadfile.bin" || n == "orbit-download") n = "orbit-file-${System.currentTimeMillis() % 10000}"
        return n.take(120)
    }

    private fun open(url: String): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply { setRequestProperty("User-Agent", ua) }
    }.getOrNull()

    // ------------------------------------------------------------ open file

    /** Downloaded file kholo — content:// (MediaStore) ya FileProvider path. */
    fun openTask(ctx: Context, d: LocalStore.DownloadItem): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            if (d.uri.startsWith("content:")) {
                intent.setDataAndType(Uri.parse(d.uri), d.mime.ifBlank { "application/octet-stream" })
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                val f = File(d.uri)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", f,
                )
                intent.setDataAndType(uri, d.mime.ifBlank { mimeFor(f.name) })
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    // ------------------------------------------------------------ formatters (UC-style)

    fun fmtSize(bytes: Long): String = when {
        bytes < 0 -> "unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024f)
        bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024f / 1024f)
        else -> String.format("%.2f GB", bytes / 1024f / 1024f / 1024f)
    }

    fun fmtSpeed(bps: Long): String = when {
        bps < 1024 -> "$bps B/s"
        bps < 1024 * 1024 -> String.format("%.0f KB/s", bps / 1024f)
        bps < 1024L * 1024 * 1024 -> String.format("%.1f MB/s", bps / 1024f / 1024f)
        else -> String.format("%.2f GB/s", bps / 1024f / 1024f / 1024f)
    }

    fun fmtEta(sec: Long): String = when {
        sec < 0 -> ""
        sec < 60 -> "${sec}s left"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s left"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m left"
    }
}
