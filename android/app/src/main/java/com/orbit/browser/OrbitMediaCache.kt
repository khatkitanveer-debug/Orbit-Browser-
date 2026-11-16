package com.orbit.browser

import android.content.Context
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * v1.9.13 ORBIT MEDIA CACHE — offline-first media disk cache.
 *
 * USER: "Har open par video aur songs loading le rahe hai... offline cache add karo
 * take main screen loading na le browser offline ki tarha chalna chahiye jaha jaha
 * cache ke zaroorat hai add karo."
 *
 * WebView ka HTTP cache media streams ko reliably cache NAHI karta (Range requests +
 * CDN no-store headers) — isliye har app-open par wahi gaana/video dobara network se
 * aata tha. Ye layer shouldInterceptRequest me audio/video GETs ko pakad kar disk me
 * cache karti hai:
 *
 *   1. HIT (poora file disk me)  — ZERO network: 200/206 disk se serve. Offline me bhi
 *      gaana/video chalega (JioSaavn CDN URL deterministic hai — replay = cache hit).
 *   2. HIT (partial prefix)      — disk prefix turant + net continuation AppendStream
 *      (write-through): download jitna hua utna cache me PERSIST, agli baar wahi se resume.
 *   3. MISS (online)             — capture-through: OkHttp full GET, stream ko WebView ko
 *      dete hue disk me likhte hai (tee). ≤80MB capture, >80MB sirf stream.
 *   4. MISS (offline)            — null: WebView LOAD_CACHE_ELSE_NETWORK khud HTTP cache
 *      dekhta hai (OrbitEngine.cacheMode offline-aware hai).
 *
 * Range protocol proper hai: bytes=a- / bytes=a-b parse, Content-Range total parse,
 * 206 slices, seek prefix ke andar = disk se, prefix ke bahar = pass-through network.
 *
 * SAFETY:
 *   - Incognito tabs is layer se GUZARTE HI NAHI (caller guard) — privacy intact.
 *   - Ad-blocked URLs pehle hi block ho jate hai — ads kabhi cache nahi.
 *   - LRU eviction 250MB cap (atime oldest-first), capture 80MB hard cap.
 *   - Per-key session marker: concurrent double-continuation = corrupt append NAHI
 *     (dusra request disk-prefix-only serve hota hai); stale session 5min me steal.
 *   - Media read timeout 0 (player pause mid-stream par stream na toote).
 *
 * Core logic (parseRange/parseTotal/AppendStream/eviction) node me 1:1 port karke
 * validated hai — scripts/mediacache_test.js (5 scenarios, ALL PASS).
 */
object OrbitMediaCache {

    /** LRU cap — purane (atime) entries pehle evict. */
    private const val CAP_BYTES: Long = 250L * 1024 * 1024

    /** Isse bade file cache NAHI (sirf stream-through) — disk protection. */
    private const val MAX_CAPTURE: Long = 80L * 1024 * 1024

    private const val SESSION_TTL_MS: Long = 5 * 60 * 1000L

    private val AV_EXT = setOf(
        "mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "m4b", "amr",
        "mp4", "m4v", "webm", "mkv", "mov", "3gp",
    )

    private val MIME: Map<String, String> = mapOf(
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac",
        "wav" to "audio/wav", "ogg" to "audio/ogg", "oga" to "audio/ogg",
        "opus" to "audio/ogg", "flac" to "audio/flac", "m4b" to "audio/mp4",
        "amr" to "audio/amr", "mp4" to "video/mp4", "m4v" to "video/mp4",
        "webm" to "video/webm", "mkv" to "video/x-matroska",
        "mov" to "video/quicktime", "3gp" to "video/3gpp",
    )

    @Volatile private var dirRef: File? = null
    @Volatile private var mediaHttp: OkHttpClient? = null

    // ------------------------------------------------------------ url/url-class

    /** Audio/video URL? — path extension ya googlevideo-style mime= param. */
    fun isMediaUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        if (path.substringAfterLast('.', "") in AV_EXT) return true
        // YT googlevideo: extension-less, mime param me type batata hai
        return url.contains("mime=audio") || url.contains("mime=video")
    }

    private fun dir(ctx: Context): File {
        dirRef?.let { return it }
        val d = File(ctx.cacheDir, "media_cache").apply { mkdirs() }
        dirRef = d
        return d
    }

    private fun http(): OkHttpClient {
        mediaHttp?.let { return it }
        val c = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // media streaming — pause-safe
            .build()
        mediaHttp = c
        return c
    }

    /** SHA-256(url) ke pehle 24 hex — filesystem-safe cache key. */
    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }.substring(0, 24)

    private fun binF(ctx: Context, k: String) = File(dir(ctx), "$k.bin")
    private fun metaF(ctx: Context, k: String) = File(dir(ctx), "$k.meta")
    private fun tmpF(ctx: Context, k: String) = File(dir(ctx), "$k.${System.nanoTime()}.tmp")

    private class Meta(val total: Long, val mime: String, val atime: Long)

    /** meta: "total|mime|atime" — ek line, JSON dep nahi. */
    private fun readMeta(ctx: Context, k: String): Meta? = runCatching {
        val parts = metaF(ctx, k).readText().split('|')
        Meta(parts[0].trim().toLongOrNull() ?: 0L,
            parts.getOrNull(1)?.trim() ?: "",
            parts.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L)
    }.getOrNull()

    private fun writeMeta(ctx: Context, k: String, total: Long, mime: String, atime: Long) {
        runCatching { metaF(ctx, k).writeText("$total|$mime|$atime") }
    }

    private fun touch(ctx: Context, k: String, m: Meta) =
        writeMeta(ctx, k, m.total, m.mime, System.currentTimeMillis())

    // ------------------------------------------------------------ range parsing (test-validated)

    /** "bytes=a-b" / "bytes=a-" → (a, b) — b=-1 open end; invalid/multi → (0, -1). */
    fun parseRange(h: String?): Pair<Long, Long> {
        if (h.isNullOrBlank()) return 0L to -1L
        val m = Regex("^bytes=(\\d+)-(\\d*)$").find(h.trim()) ?: return 0L to -1L
        val s = m.groupValues[1].toLong()
        val e = if (m.groupValues[2].isEmpty()) -1L else m.groupValues[2].toLong()
        return s to e
    }

    /** "bytes 100-999/5000" → 5000; "*"/""/null → null. */
    fun parseTotal(cr: String?): Long? {
        if (cr == null) return null
        val slash = cr.lastIndexOf('/')
        if (slash < 0) return null
        val t = cr.substring(slash + 1).trim()
        if (t == "*" || t.isEmpty()) return null
        val v = t.toLongOrNull() ?: return null
        return if (v > 0) v else null
    }

    // ------------------------------------------------------------ streams

    /**
     * Disk prefix + net continuation, write-through — mediacache_test.js Scenario A/B.
     * Net portion sink me append hota hai (bin diskLen se aage likha jata hai).
     */
    private class AppendStream(
        private val diskIn: InputStream,
        private val netIn: InputStream,
        private val sink: FileOutputStream?,
        private val onDone: Runnable?,
    ) : InputStream() {
        private var phase = 0
        private var done = false

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (done) return -1
            if (len == 0) return 0
            if (phase == 0) {
                val n = diskIn.read(b, off, len)
                if (n >= 0) return n
                phase = 1
            }
            val n = netIn.read(b, off, len)
            if (n > 0) runCatching { sink?.write(b, off, n) }
            if (n < 0) {
                done = true
                finish()
                return -1
            }
            return n
        }

        // InputStream abstract single-byte read — bulk read ko delegate
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else (b[0].toInt() and 0xFF)
        }

        private fun finish() {
            runCatching { sink?.flush() }
            runCatching { sink?.close() }
            onDone?.run()
        }

        override fun close() {
            if (!done) {
                done = true
                // player cancel/mid-fail — jitna append hua PERSIST (resume next time)
                runCatching { sink?.flush() }
                runCatching { sink?.close() }
            }
            runCatching { diskIn.close() }
            runCatching { netIn.close() }
        }
    }

    /** Net body → WebView stream + disk tee (capture-through MISS path). */
    private class TeeStream(
        private val src: InputStream,
        @Volatile private var sink: FileOutputStream?,
        private val tmp: File?,
        private val bin: File?,
        private val meta: String?,
        private val ctx: Context?,
        private val k: String?,
    ) : InputStream() {
        private var written = 0L
        private var finished = false

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = src.read(b, off, len)
            if (n > 0) {
                val s = sink
                if (s != null) {
                    if (written + n > MAX_CAPTURE) {
                        // capture cap paar — sink band, streaming continue (protection)
                        runCatching { s.close() }
                        sink = null
                        runCatching { tmp?.delete() }
                    } else {
                        runCatching { s.write(b, off, n) }
                    }
                }
                written += n
            }
            if (n < 0) finish()
            return n
        }

        // InputStream abstract single-byte read — bulk read ko delegate
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) < 0) -1 else (b[0].toInt() and 0xFF)
        }

        private fun finish() {
            if (finished) return
            finished = true
            val s = sink
            sink = null
            runCatching { s?.flush() }
            runCatching { s?.close() }
            // commit: tmp → bin (atomic rename) + meta — locals pe copy (lambda smart-cast)
            val t = tmp; val b = bin; val c = ctx; val kk = k; val mm = meta
            if (s != null && t != null && b != null && c != null && kk != null) {
                runCatching {
                    if (t.renameTo(b)) {
                        writeMeta(c, kk, b.length(), mm ?: "application/octet-stream",
                            System.currentTimeMillis())
                        evictIfNeeded(c)
                    }
                }
            }
        }

        override fun close() {
            finish()
            runCatching { src.close() }
        }
    }

    // ------------------------------------------------------------ responses

    private fun resp200(mime: String, data: InputStream): WebResourceResponse =
        WebResourceResponse(mime, null, 200, "OK",
            mapOf("Accept-Ranges" to "bytes"), data)

    /** 206 slice — test Scenario C math: Content-Range "bytes s-e/total". */
    private fun resp206(mime: String, s: Long, end: Long, total: Long, data: InputStream): WebResourceResponse =
        WebResourceResponse(mime, null, 206, "Partial Content",
            mapOf("Content-Range" to "bytes $s-$end/$total",
                "Accept-Ranges" to "bytes"), data)

    // ------------------------------------------------------------ eviction (test Scenario E)

    /** 250MB cap paar ho to sabse purani-atime entries delete (oldest-first). */
    fun evictIfNeeded(ctx: Context) {
        runCatching {
            val d = dir(ctx)
            val entries = mutableListOf<Triple<Long, String, Long>>() // (atime, key, size)
            var total = 0L
            d.listFiles()?.forEach { f ->
                if (f.name.endsWith(".bin") && f.length() > 0) {
                    val k = f.name.removeSuffix(".bin")
                    val m = readMeta(ctx, k)
                    entries.add(Triple(m?.atime ?: 0L, k, f.length()))
                    total += f.length()
                }
            }
            if (total <= CAP_BYTES) return
            entries.sortBy { it.first }
            for ((_, k, size) in entries) {
                if (total <= CAP_BYTES) break
                runCatching { File(d, "$k.bin").delete() }
                runCatching { File(d, "$k.meta").delete() }
                runCatching { File(d, "$k.session").delete() }
                total -= size
            }
        }
    }

    // ------------------------------------------------------------ main intercept

    /**
     * Media GET ke liye cache-aware response, ya null (= WebView khud network kare).
     * rangeHeader = request "Range" header (null ok), ua = request ka User-Agent.
     */
    fun intercept(ctx: Context, url: String, rangeHeader: String?, ua: String?): WebResourceResponse? {
        if (!isMediaUrl(url)) return null
        val k = key(url)
        val bin = binF(ctx, k)
        val meta = readMeta(ctx, k)
        val online = OrbitEngine.isOnlineNow(ctx)

        // ---------------- HIT ----------------
        if (bin.exists() && meta != null) {
            touch(ctx, k, meta)
            val diskLen = bin.length()
            val (s, e) = parseRange(rangeHeader)
            if (s > diskLen) return null // prefix ke bahar seek — pass-through (guard D)
            val mime = meta.mime.ifBlank { MIME[binExt(url)] ?: "application/octet-stream" }
            val total = meta.total
            val complete = total in 1..diskLen
            val wantEnd = if (e >= 0) e + 1 else if (total > 0) total else diskLen
            val needNet = wantEnd > diskLen && online

            if (needNet) {
                // session marker: concurrent double-append se corruption block
                val session = File(dir(ctx), "$k.session")
                val fresh = if (!session.exists()) session.createNewFile()
                else System.currentTimeMillis() - session.lastModified() > SESSION_TTL_MS
                if (fresh) runCatching { session.setLastModified(System.currentTimeMillis()) }
                if (fresh) {
                    val req = Request.Builder().url(url)
                        .header("Range", "bytes=$diskLen-")
                        .apply { if (ua != null) header("User-Agent", ua) }
                        .build()
                    val r = runCatching { http().newCall(req).execute() }.getOrNull()
                    var adopted = false
                    if (r != null && r.code == 206) {
                        val netTotal = parseTotal(r.header("Content-Range")) ?: total
                        val body = r.body
                        if (body != null && netTotal > diskLen) {
                            val fis = FileInputStream(bin)
                            var skipped = 0L
                            if (s > 0) { // seek: prefix ka start..s PEHLE (test Scenario B)
                                while (skipped < s) {
                                    val got = fis.skip(s - skipped)
                                    if (got <= 0) break
                                    skipped += got
                                }
                            }
                            val sink = runCatching {
                                FileOutputStream(bin, true) // append — write-through
                            }.getOrNull()
                            val stream = AppendStream(fis, body.byteStream(), sink) {
                                writeMeta(ctx, k, netTotal, meta.mime, System.currentTimeMillis())
                                evictIfNeeded(ctx)
                                runCatching { File(dir(ctx), "$k.session").delete() }
                            }
                            // serve: seek (s..diskLen) + net (diskLen..netTotal)
                            // BODY ADOPTED — response/conn lifecycle ab AppendStream(close) ka
                            adopted = true
                            return resp206(mime, s, netTotal - 1, netTotal, stream)
                        }
                    }
                    // adopt nahi hua to response band (use{} NAHI — body adopt hota to close hota)
                    if (r != null && !adopted) runCatching { r.close() }
                    if (!adopted) runCatching { File(dir(ctx), "$k.session").delete() }
                }
            }

            // disk-only serve (offline / no-session / continuation failed)
            return if (s > 0) {
                val end = (if (e >= 0) e else diskLen - 1).coerceAtMost(diskLen - 1)
                if (end < s) return null
                val fis = FileInputStream(bin)
                var skipped = 0L
                while (skipped < s) {
                    val got = fis.skip(s - skipped)
                    if (got <= 0) break
                    skipped += got
                }
                resp206(mime, s, end, if (complete) total else diskLen, fis)
            } else {
                resp200(mime, FileInputStream(bin))
            }
        }

        // ---------------- MISS ----------------
        if (!online) return null // offline: WebView ka LOAD_CACHE_ELSE_NETWORK HTTP cache dekhe
        val (s0, _) = parseRange(rangeHeader)
        if (s0 > 0) return null // ranged partial miss — full capture nahi ban sakta

        // capture-through: OkHttp full GET → WebView stream + disk tee
        val req = Request.Builder().url(url)
            .apply { if (ua != null) header("User-Agent", ua) }
            .build()
        val r = runCatching { http().newCall(req).execute() }.getOrNull() ?: return null
        val code = r.code
        val mime = (r.header("Content-Type") ?: "").substringBefore(';').trim()
            .ifBlank { MIME[binExt(url)] ?: "application/octet-stream" }
        if (code != 200) {
            runCatching { r.close() }
            // double-fetch avoid — same failure WebView ko dedo
            return WebResourceResponse(mime, null, code, "Failed",
                emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        val body = r.body ?: return null.also { runCatching { r.close() } }
        val clen = body.contentLength()
        // capture eligible (≤80MB ya unknown size) — tabhi tmp file bana (warna disk litter)
        val fos = if (clen in 1..MAX_CAPTURE || clen < 0)
            runCatching { FileOutputStream(tmpF(ctx, k)) }.getOrNull() else null
        val tmp = if (fos != null) tmpF(ctx, k) else null
        return WebResourceResponse(mime, null, 200, "OK",
            mapOf("Accept-Ranges" to "bytes"),
            TeeStream(body.byteStream(), fos, tmp, binF(ctx, k), mime, ctx, k))
    }

    private fun binExt(url: String): String =
        url.substringBefore('?').substringBefore('#').lowercase().substringAfterLast('.', "")
}
