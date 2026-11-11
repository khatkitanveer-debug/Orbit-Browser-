package com.orbit.browser.lens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Module 3 — Google Lens reverse-image engine.
 *
 * 1. downscale(): camera JPEG → ≤1600px q85 JPEG (fast upload, no EXIF/GPS
 *    leaves the device because we re-encode from raw pixels).
 * 2. upload(): multipart POST to the public Lens upload endpoint.
 * 3. pickResultsUrl(): extract the results URL (Location header, then body
 *    regexes with JSON-escape decoding).
 *
 * This endpoint is UNOFFICIAL and may drift — every caller must handle a
 * null return by falling back to https://lens.google.com/ (manual upload
 * UI). Nothing else in the app knows about this endpoint; it changes here
 * only.
 */
object LensEngine {

    private const val UPLOAD_URL = "https://lens.google.com/v3/upload"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 85
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    /** Lens responses carry the results URL early — no need to buffer more. */
    private const val MAX_BODY_BYTES = 2 shl 20

    /** src JPEG → dst JPEG, longest side ≤ MAX_DIMENSION. Blocking. */
    fun downscale(src: File, dst: File): Boolean {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            var longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / 2 >= MAX_DIMENSION) {
                sample *= 2
                longest /= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return false

            val scaled = if (maxOf(decoded.width, decoded.height) > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height)
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                decoded
            }

            FileOutputStream(dst).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            dst.length() > 0
        }.getOrDefault(false)
    }

    /**
     * Blocking multipart upload. Returns the Lens results URL, or null when
     * the endpoint drifted / network failed / the response changed shape.
     */
    fun upload(image: File): String? {
        return runCatching {
            val boundary = "----OrbitLensBoundary${System.currentTimeMillis()}"
            val connection = URL(UPLOAD_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.instanceFollowRedirects = false // capture cross-host Location ourselves

                DataOutputStream(connection.outputStream).use { out ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes(
                        "Content-Disposition: form-data; name=\"encoded_image\"; " +
                            "filename=\"lens.jpg\"\r\n"
                    )
                    out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    image.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                    out.writeBytes("\r\n--$boundary--\r\n")
                }

                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                val stream = if (code in 200..399) connection.inputStream else connection.errorStream
                val body = stream?.let { readCapped(it) }

                pickResultsUrl(location, body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Location header wins, then known shapes inside the HTML body. */
    fun pickResultsUrl(location: String?, body: String?): String? {
        if (!location.isNullOrBlank() && location.startsWith("http")) return location
        if (body.isNullOrEmpty()) return null

        val patterns = listOf(
            Regex("https://www\\.google\\.[a-z.]+/search\\?[^\"'<>\\s\\\\]+"),
            Regex("https://lens\\.google\\.com/[^\"'<>\\s\\\\]+"),
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(body)) {
                // Responses embed URLs JSON-escaped inside script payloads.
                val candidate = match.value
                    .replace("\\u003d", "=")
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                if (candidate.length > "https://".length + 8) return candidate
            }
        }
        return null
    }

    private fun readCapped(stream: java.io.InputStream): String {
        val buffer = ByteArrayOutputStream(MAX_BODY_BYTES)
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < MAX_BODY_BYTES) {
            val n = stream.read(chunk, 0, minOf(chunk.size, MAX_BODY_BYTES - total))
            if (n < 0) break
            buffer.write(chunk, 0, n)
            total += n
        }
        return buffer.toString("UTF-8")
    }
}
