package com.orbit.browser

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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
import java.io.RandomAccessFile
import java.security.MessageDigest
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
 *
 * v1.2.1 HARDENING ("Install likha aata hai magar install hota nahi" bug):
 *   - Downloaded APK VALIDATE hota hai (PK magic + Android parse + versionCode) —
 *     adhoora/corrupt cache kabhi Install dialog tak nahi pahunchega
 *   - Install se PEHLE signing-cert compare — mismatch ho to seedha MIGRATION
 *     dialog (save → uninstall → reinstall), doomed install attempt kabhi nahi
 *   - ACTION_INSTALL_PACKAGE primary; na chale to PackageInstaller session fallback
 *   - Har dialog me "Chrome me APK kholo" escape hatch — updater kabhi deadlock
 *     nahi karega
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
     * complete hone par [onReady] — app INSTALL dialog dikhayegi.
     * Callback me (info, apkFile, compatible) — compatible=false (signing mismatch)
     * par app seedha MIGRATION dialog dikhayegi, Install attempt bekaar hai.
     * manual=true (menu se check) par chhote status toast bhi dikhte hain.
     */
    fun autoUpdate(ctx: Context, manual: Boolean, onReady: (UpdateInfo, File, Boolean) -> Unit) {
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
            // pehle se downloaded VALID apk hai? (pichhli baar) — download skip, seedha Install
            val cached = cachedApk(ctx, latest.versionName, latest.versionCode)
            if (cached == null && manual) {
                toast(ctx, "Orbit ${latest.versionName} background me download ho raha hai…")
            }
            val apk = cached ?: download(ctx, latest)
            if (apk == null) {
                if (manual) toast(ctx, "Download fail ho gaya — thodi der me dobara try karo")
                return@Thread
            }
            val compatible = signaturesCompatible(ctx, apk)
            main.post { onReady(latest, apk, compatible != false) }
        }.start()
    }

    /** Downloaded/cached APK sach me valid hai? (PK magic + Android parse + versionCode) */
    fun validateApk(ctx: Context, f: File?, expectedVersionCode: Int): File? {
        if (f == null || !f.exists() || f.length() < 100_000) return null
        val ok = runCatching {
            RandomAccessFile(f, "r").use { raf ->
                val magic = ByteArray(2)
                raf.readFully(magic)
                magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
            } && run {
                val info = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
                info != null && info.packageName == ctx.packageName &&
                    (expectedVersionCode <= 0 || info.longVersionCode == expectedVersionCode.toLong())
            }
        }.getOrDefault(false)
        return if (ok) f else null
    }

    /** Pehle se downloaded valid APK hai? (cache/updates/orbit-<v>.apk) — corrupt ho to delete */
    fun cachedApk(ctx: Context, versionName: String, versionCode: Int): File? {
        val f = File(File(ctx.cacheDir, "updates"), "orbit-$versionName.apk")
        if (!f.exists()) return null
        val ok = validateApk(ctx, f, versionCode)
        if (ok == null) f.delete()
        return ok
    }

    /** SILENT download — Orbit ke cache me. Koi Chrome/DownloadManager/notification nahi. */
    private fun download(ctx: Context, info: UpdateInfo): File? {
        return runCatching {
            val req = Request.Builder().url(info.apkUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val expectedLen = resp.body?.contentLength() ?: -1L
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                // purane pending update files clear — hamesha fresh
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "orbit-${info.versionName}.apk")
                val body = resp.body ?: error("empty body")
                body.byteStream().use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                // server ke bataye size se milaan — adhoora download pakdo
                if (expectedLen > 0 && out.length() != expectedLen) {
                    error("adhoora download (${out.length()}/${expectedLen} bytes)")
                }
                validateApk(ctx, out, info.versionCode)
                    ?: error("downloaded file valid APK nahi hai")
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------- signing pre-flight

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Installed Orbit ka signing cert SHA-256 */
    fun installedCertSha256(ctx: Context): String? = runCatching {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val p = pm.getPackageInfo(ctx.packageName, flags)
        val sig = if (Build.VERSION.SDK_INT >= 28)
            p.signingInfo?.apkContentsSigners?.firstOrNull()
        else
            @Suppress("DEPRECATION") p.signatures?.firstOrNull()
        sig?.let { sha256Hex(it.toByteArray()) }
    }.getOrNull()

    /** Downloaded APK ka signing cert SHA-256 */
    fun apkCertSha256(ctx: Context, apk: File): String? = runCatching {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
        val sig = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        else
            @Suppress("DEPRECATION") info.signatures?.firstOrNull()
        sig?.let { sha256Hex(it.toByteArray()) }
    }.getOrNull()

    /** Kya APK installed app ke UPAR install ho sakta hai (same signing key)? null = pata nahi */
    fun signaturesCompatible(ctx: Context, apk: File): Boolean? {
        val a = installedCertSha256(ctx) ?: return null
        val b = apkCertSha256(ctx, apk) ?: return null
        return a.equals(b, ignoreCase = true)
    }

    // ---------------------------------------------------------------- install

    const val ACTION_INSTALL_STATUS = "com.orbit.browser.INSTALL_STATUS"

    /**
     * Install — PROVEN PATH: ACTION_INSTALL_PACKAGE (system installer + FileProvider).
     * Na khule to PackageInstaller session fallback (InstallStatusReceiver
     * PENDING_USER_ACTION par confirmation screen kholta hai).
     */
    fun installWithStatus(ctx: Context, apk: File) {
        promptInstall(ctx, apk)
    }

    /** System installer prompt khud khulta hai — user sirf INSTALL dabaye */
    fun promptInstall(ctx: Context, apk: File) {
        val opened = runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            // Kuch OEM ROMs par flag-grant kaafi nahi hota — installer packages ko explicit grant
            for (pkg in listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.android.permissioncontroller",
            )) {
                runCatching { ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, ctx.packageName)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!opened) installViaSession(ctx, apk)
    }

    /** Fallback — ACTION_INSTALL_PACKAGE koi handler na mile to PackageInstaller session */
    private fun installViaSession(ctx: Context, apk: File) {
        val ok = runCatching {
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
                    .setClass(ctx, InstallStatusReceiver::class.java)
                    .putExtra("apkPath", apk.absolutePath)
                val sender = PendingIntent.getBroadcast(
                    ctx, sessionId, statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(sender.intentSender)
            }
            true
        }.getOrDefault(false)
        if (!ok) toast(ctx, "Install nahi ho paya — Settings me 'Install unknown apps' allow karo, phir dobara try karo")
    }

    /** Migration: purana version uninstall (system confirm dialog khud aata hai) */
    fun uninstallSelf(ctx: Context) {
        runCatching {
            @Suppress("DEPRECATION")
            ctx.startActivity(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            toast(ctx, "Uninstall screen nahi khuli — Settings > Apps > Orbit uninstall karo")
        }
    }

    /** Escape hatch — APK URL system browser me kholo (Chrome download karke install offer karega) */
    fun openApkInBrowser(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast(ctx, "Browser nahi khula") }
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
