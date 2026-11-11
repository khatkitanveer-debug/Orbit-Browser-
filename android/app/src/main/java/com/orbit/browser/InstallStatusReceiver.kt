package com.orbit.browser

import android.content.pm.PackageInstaller
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * PackageInstaller session ka status yahan aata hai (see [UpdateChecker.installWithStatus]).
 * MainActivity band ho sakta hai install ke dauran — isliye manifest-registered receiver.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        // system callback — kuch bhi odd aaya to APP CRASH nahi hona chahiye
        CrashGuard.install(ctx.applicationContext)
        runCatching { handle(ctx, intent) }.onFailure {
            android.util.Log.e("OrbitInstall", "install status handle fail", it)
        }
    }

    @Suppress("DEPRECATION")
    private fun handle(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // system install confirmation screen kholo
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                runCatching {
                    ctx.startActivity(confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    Toast.makeText(ctx, "Install screen nahi khuli — 'Install unknown apps' allow karo", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(ctx, "Orbit update ho gaya", Toast.LENGTH_LONG).show()

            else -> {
                val i = Intent(ctx, UpdateStatusActivity::class.java)
                    .putExtra(UpdateStatusActivity.EXTRA_STATUS, status)
                    .putExtra(UpdateStatusActivity.EXTRA_MSG, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                    .putExtra(UpdateStatusActivity.EXTRA_APK, intent.getStringExtra("apkPath"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(i) }
            }
        }
    }
}
