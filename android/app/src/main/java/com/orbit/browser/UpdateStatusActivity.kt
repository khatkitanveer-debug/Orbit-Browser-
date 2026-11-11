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
            conflict -> "Update install nahi hua (security key alag hai)"
            blocked -> "Install roka gaya (Play Protect)"
            else -> "Install fail ho gaya"
        }

        val baseMsg = if (conflict) {
            "Aapke phone me purana Orbit kisi ALAG build key se bana tha — Android security " +
                "rule ki wajah se uske upar naya version nahi lagta. YE SIRF IS EK BAAR hai — " +
                "v1.0.3 se aage ke saare updates khud-ba-khud install honge.\n\n" +
                "Ab ye karo:\n" +
                "1. Neeche 'APK Downloads me save karo' dabao\n" +
                "2. Purana Orbit uninstall karo (data — bookmarks/history — waise bhi local thi)\n" +
                "3. Files/Downloads app me saved APK kholke install karo"
        } else {
            "Wajah: ${if (msg.isNotBlank()) msg else "status code $status"}\n\n" +
                "Thodi der me dobara try karo — naya update aate hi Orbit khud download karke " +
                "Install button dikhayega."
        }

        val dialog = AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle(title)
            .setMessage(baseMsg)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setNeutralButton("", null) // conflict me "APK Downloads me save karo" ban jayega
            .create()

        dialog.setOnShowListener {
            if (conflict && apkPath != null) {
                val btn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                btn.text = "APK Downloads me save karo"
                btn.setOnClickListener {
                    val apk = File(apkPath)
                    if (!apk.exists() || apk.length() < 100_000) {
                        Toast.makeText(applicationContext, "Downloaded APK gayab hai — update phir aayega", Toast.LENGTH_LONG).show()
                        finish()
                        return@setOnClickListener
                    }
                    btn.isEnabled = false
                    Thread {
                        val path = UpdateChecker.saveApkToDownloads(applicationContext, apk)
                        Handler(Looper.getMainLooper()).post {
                            if (path != null) {
                                dialog.setMessage(
                                    baseMsg.substringBefore("\n\n") + "\n\nAPK yahan save ho gaya: " + path +
                                        "\n\nAb: purana Orbit uninstall karo, phir Files/Downloads me is APK ko kholo."
                                )
                            } else {
                                dialog.setMessage(
                                    "Downloads me save nahi ho paya.\n\nChrome me ye page kholke APK " +
                                        "seedha download karo: github.com/khatkitanveer-debug/Orbit-Browser-/releases"
                                )
                            }
                            btn.isEnabled = true
                            btn.text = "Dobara save karo"
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
