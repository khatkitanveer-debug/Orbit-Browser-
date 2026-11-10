package com.orbit.browser

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Orbit OTA updater — repo ke [ota/update.json] ko check karta hai
 * (raw.githubusercontent.com se). Naya version mila to APK download karke
 * installer prompt dikhata hai. Poori tarah GitHub par based — koi extra server nahi.
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
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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

    /** APK download (DownloadManager) — complete hone par installer prompt */
    fun downloadAndInstall(ctx: Context, info: UpdateInfo) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "orbit-browser-${info.versionName}.apk"
        val req = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("Orbit Browser ${info.versionName}")
            setDescription("Orbit update download ho raha hai...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }
        val id = dm.enqueue(req)
        Toast.makeText(ctx, "Orbit ${info.versionName} download ho raha hai...", Toast.LENGTH_SHORT).show()

        ctx.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val doneId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (doneId == id) {
                        val uri = dm.getUriForDownloadedFile(id)
                        if (uri != null) {
                            val install = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { c?.startActivity(install) }
                                .onFailure {
                                    Toast.makeText(c, "Install prompt nahi khula — Downloads se APK install karo", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                    runCatching { ctx.unregisterReceiver(this) }
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        )
    }
}
