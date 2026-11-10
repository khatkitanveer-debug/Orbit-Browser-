package com.orbit.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Orbit OTA updater — repo ke [ota/update.json] ko check karta hai
 * (raw.githubusercontent.com se).
 *
 * Naya flow (user ko sirf INSTALL dabana hai):
 *   1. "Update karo" dabate hi APK BACKGROUND me download hota hai (app ke cache me)
 *   2. Download complete hote hi system installer KAHD KHUD khul jata hai
 *   3. User sirf INSTALL dabaye — same applicationId + same signature hone se
 *      purana version REPLACE ho jata hai (aapka data bana rehta hai),
 *      Downloads folder me koi APK kachra nahi.
 */
object UpdateChecker {

    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/khatkitanveer-debug/Orbit-Browser-/main/ota/update.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String,
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())

    fun fetchLatest(): UpdateInfo? {
        return runCatching {
            val req = Request.Builder().url(UPDATE_JSON_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val o = org.json.JSONObject(body)
                UpdateInfo(
                    versionCode = o.getInt("versionCode"),
                    versionName = o.getString("versionName"),
                    apkUrl = o.getString("apkUrl"),
                    notes = o.optString("notes", ""),
                )
            }
        }.getOrNull()
    }

    fun isUpdateAvailable(latest: UpdateInfo): Boolean =
        latest.versionCode > BuildConfig.VERSION_CODE

    /** Background download → installer prompt khud khulta hai */
    fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        toast(ctx, "Orbit ${info.versionName} background me download ho raha hai…")
        Thread {
            val apk: File? = runCatching {
                val req = Request.Builder().url(info.apkUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                    // purana pending update file clear — hamesha fresh download
                    dir.listFiles()?.forEach { it.delete() }
                    val out = File(dir, "orbit-${info.versionName}.apk")
                    val body = resp.body ?: error("empty body")
                    body.byteStream().use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    out
                }
            }.getOrNull()

            if (apk == null || apk.length() < 100_000) {
                toast(ctx, "Download fail ho gaya — internet check karke dobara try karo")
                return@Thread
            }
            toast(ctx, "Download complete — Install dabao")
            promptInstall(ctx, apk)
        }.start()
    }

    private fun promptInstall(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure {
                toast(ctx, "Installer nahi khula — Settings me 'Install unknown apps' allow karo")
            }
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
