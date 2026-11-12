package com.orbit.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File

/**
 * Install fail hone par wajah samjhane wala dialog.
 * Khaas case: FAILURE_CONFLICT (signature mismatch) — purane versions ek doosri
 * key se bane the, isliye Android unke upar naya version block karta hai.
 * One-time migration: APK ko public Downloads me save karo (uninstall se cache
 * mar jata hai, Downloads nahi) → purana Orbit uninstall → Downloads me APK kholo.
 */
class UpdateStatusActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        val status = intent.getIntExtra(EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(EXTRA_MSG).orEmpty()
        val apkPath = intent.getStringExtra(EXTRA_APK)

        val conflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT
        val blocked = status == PackageInstaller.STATUS_FAILURE_BLOCKED

        val title = when {
            conflict -> "Update didn't install (different signing key)"
            blocked -> "Install blocked (Play Protect)"
            else -> "Install failed"
        }

        val baseMsg = if (conflict) {
            "The old Orbit on this phone was built with a DIFFERENT signing key — Android security " +
                "rules won't install the new version on top of it. This happens ONLY ONCE — " +
                "updates after this will install automatically.\n\n" +
                "Do this:\n" +
                "1. Tap 'Save APK to Downloads' below\n" +
                "2. Uninstall the old Orbit (data — bookmarks/history — was local anyway)\n" +
                "3. Open the saved APK from the Files/Downloads app"
        } else {
            "Reason: ${if (msg.isNotBlank()) msg else "status code $status"}\n\n" +
                "Try again in a moment — when the next update arrives, Orbit will download it " +
                "and show an Install button automatically."
        }

        val dialog = AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle(title)
            .setMessage(baseMsg)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setNeutralButton("", null) // conflict me "Save APK to Downloads" ban jayega
            .create()

        dialog.setOnShowListener {
            if (conflict && apkPath != null) {
                val btn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                btn.text = "Save APK to Downloads"
                btn.setOnClickListener {
                    val apk = File(apkPath)
                    if (!apk.exists() || apk.length() < 100_000) {
                        Toast.makeText(applicationContext, "Downloaded APK is gone — the update will arrive again", Toast.LENGTH_LONG).show()
                        finish()
                        return@setOnClickListener
                    }
                    btn.isEnabled = false
                    Thread {
                        val path = UpdateChecker.saveApkToDownloads(applicationContext, apk)
                        Handler(Looper.getMainLooper()).post {
                            if (path != null) {
                                dialog.setMessage(
                                    baseMsg.substringBefore("\n\n") + "\n\nAPK saved here: " + path +
                                        "\n\nNow: uninstall the old Orbit, then open this APK from Files/Downloads."
                                )
                            } else {
                                dialog.setMessage(
                                    "Couldn't save to Downloads.\n\nOpen this page in your browser and " +
                                        "download the APK directly: github.com/khatkitanveer-debug/Orbit-Browser-/releases"
                                )
                            }
                            btn.isEnabled = true
                            btn.text = "Try saving again"
                        }
                    }.start()
                }
            }
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_STATUS = "status"
        const val EXTRA_MSG = "msg"
        const val EXTRA_APK = "apkPath"
    }
}
