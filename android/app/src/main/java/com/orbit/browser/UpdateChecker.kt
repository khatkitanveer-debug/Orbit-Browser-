package com.orbit.browser

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
 * FULLY SILENT AUTO-UPDATE (Chrome updater jaisa):
 *   1. App launch par khud check hota hai — update mila to koi puchha-woochha nahi,
 *      APK BACKGROUND me khud download hona shuru (app ke apne cache me —
 *      na Chrome kholta hai, na Downloads folder, na notification)
 *   2. Download complete hote hi screen par sirf INSTALL button wala dialog aata hai
 *   3. Install dabao — same applicationId + signature se purana version REPLACE,
 *      aapka data bana rehta hai
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

    /**
     * Silent auto-update: update mila to background me khud download,
     * complete hone par [onReady] — app sirf INSTALL dialog dikhayegi.
     * manual=true (menu se check) par chhote status toast bhi dikhte hain.
     */
    fun autoUpdate(ctx: Context, manual: Boolean, onReady: (UpdateInfo) -> Unit) {
        Thread {
            val latest = fetchLatest()
            if (latest == null) {
                if (manual) toast(ctx, "Update check nahi ho paya (network?)")
                return@Thread
            }
            if (!isUpdateAvailable(latest)) {
                if (manual) toast(ctx, "Orbit up-to-date hai (v${BuildConfig.VERSION_NAME})")
                return@Thread
            }
            // pehle se downloaded hai (pichhli baar)? — download skip, seedha Install
            val cached = cachedApk(ctx, latest.versionName)
            if (cached != null) {
                main.post { onReady(latest) }
                return@Thread
            }
            if (manual) toast(ctx, "Orbit ${latest.versionName} background me download ho raha hai…")
            val apk = download(ctx, latest)
            if (apk == null) {
                if (manual) toast(ctx, "Download fail ho gaya — thodi der me dobara try karo")
                return@Thread
            }
            main.post { onReady(latest) }
        }.start()
    }

    /** Pehle se downloaded valid APK hai? (cache/updates/orbit-<v>.apk) */
    fun cachedApk(ctx: Context, versionName: String): File? {
        val f = File(File(ctx.cacheDir, "updates"), "orbit-$versionName.apk")
        return if (f.exists() && f.length() > 100_000) f else null
    }

    /** SILENT download — Orbit ke cache me. Koi Chrome/DownloadManager/notification nahi. */
    private fun download(ctx: Context, info: UpdateInfo): File? {
        return runCatching {
            val req = Request.Builder().url(info.apkUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                // purane pending update files clear — hamesha fresh
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "orbit-${info.versionName}.apk")
                val body = resp.body ?: error("empty body")
                body.byteStream().use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out.takeIf { it.length() > 100_000 }
            }
        }.getOrNull()
    }

    /** System installer prompt khud khulta hai — user sirf INSTALL dabaye */
    fun promptInstall(ctx: Context, apk: File) {
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

    const val ACTION_INSTALL_STATUS = "com.orbit.browser.INSTALL_STATUS"

    /**
     * PackageInstaller-based install — fail hone par REASON milta hai
     * (ACTION_INSTALL_PACKAGE sirf "App not installed" bolta tha, wajah nahi).
     *
     * Status [InstallStatusReceiver] me aata hai:
     *   PENDING_USER_ACTION → system confirmation screen khulta hai
     *   SUCCESS             → "Orbit update ho gaya"
     *   FAILURE_CONFLICT    → signature mismatch dialog + "APK Downloads me save" (one-time migration)
     *   other failures      → reason ke saath dialog
     */
    fun installWithStatus(ctx: Context, apk: File) {
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("orbit-update.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val statusIntent = Intent(ACTION_INSTALL_STATUS)
                    .setPackage(ctx.packageName)
                    .putExtra("apkPath", apk.absolutePath)
                val sender = PendingIntent.getBroadcast(
                    ctx, sessionId, statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(sender.intentSender)
            }
        } catch (e: Exception) {
            // PackageInstaller na chale to purana wala prompt (wajah pata nahi chalegi)
            promptInstall(ctx, apk)
        }
    }

    /** APK public Downloads me copy — uninstall ke baad bhi zinda rehta hai (cache wipe ho jata hai) */
    fun saveApkToDownloads(ctx: Context, apk: File): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, apk.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("insert fail")
                resolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/${apk.name}"
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                apk.copyTo(File(dir, apk.name), overwrite = true).absolutePath
            }
        }.getOrNull()
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
