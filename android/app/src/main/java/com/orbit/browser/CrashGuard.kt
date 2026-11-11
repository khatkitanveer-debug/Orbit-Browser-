package com.orbit.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash guard — KOI BHI uncaught exception file me save hota hai
 * (filesDir/crash-log.txt) aur phir system handler ko jata hai.
 *
 * "App crash ho raha hai" report karne ke liye ab exact stacktrace milega:
 * agle launch par [lastCrashSummary] se dialog dikhta hai — user screenshot
 * bhej de, wajah line-by-line pata chal jati hai. Max 1 file, chhoti rakhte
 * hain (64KB cap) — storage kabhi nahi bharta.
 */
object CrashGuard {

    private const val MAX_LOG_BYTES = 64_000

    @Volatile
    private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(ctx.applicationContext, thread, throwable) }
            // system ko bhi do — "app stopped" dialog + process death normal rahe
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun logFile(ctx: Context): File = File(ctx.filesDir, "crash-log.txt")

    private fun writeCrash(ctx: Context, thread: Thread, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "\n==== CRASH @ $ts (thread: ${thread.name}) ====\n${sw}\n"
        val f = logFile(ctx)
        // purana log shuru me rakho, naya upar — file 64KB se badi na ho
        val newText = if (f.exists()) entry + f.readText().take(MAX_LOG_BYTES) else entry
        f.writeText(newText.take(MAX_LOG_BYTES + entry.length))
    }

    /** Pichhla crash (48h ke andar) — (timestampLine, firstExceptionLine, topFrames) ya null */
    fun lastCrash(ctx: Context): Triple<String, String, String>? {
        val f = logFile(ctx)
        if (!f.exists()) return null
        return runCatching {
            val text = f.readText()
            val start = text.indexOf("==== CRASH @")
            if (start < 0) return null
            val section = text.substring(start).lineSequence().take(14).joinToString("\n")
            val lines = section.split("\n")
            val ts = lines.getOrNull(0)?.removePrefix("==== CRASH @ ")?.removeSuffix(" ====") ?: "unknown"
            val firstExc = lines.firstOrNull { it.startsWith("\t") || it.contains("Exception") || it.contains("Error") } ?: "unknown"
            Triple(ts, firstExc.trim(), section)
        }.getOrNull()
    }

    /** Crash log file clear — user ne dekh liya to */
    fun clear(ctx: Context) {
        runCatching { logFile(ctx).delete() }
    }

    /** Startup par: pichhla crash tha to chhota dialog dikhao (screenshot-friendly) */
    fun showReportIfAny(activity: android.app.Activity) {
        val crash = lastCrash(activity) ?: return
        val (ts, first, full) = crash
        // 48h se purana crash — sab fix ho chuka hoga, chup raho
        runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val ageMs = System.currentTimeMillis() - sdf.parse(ts)!!.time
            if (ageMs > 48 * 3600_000L) return
        }.onFailure { return }
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            runCatching {
                android.app.AlertDialog.Builder(activity, R.style.OrbitDialog)
                    .setTitle("Orbit pichhli baar crash hua tha")
                    .setMessage(
                        "Wakt: $ts\nWajah: $first\n\n" +
                            "Ye report developer ko bhejo (screenshot) — jaldi fix hoga.\n\n" +
                            "Poora log:\n${full.take(1200)}",
                    )
                    .setPositiveButton("Theek hai") { _, _ -> clear(activity) }
                    .setNegativeButton("Log rakho", null)
                    .show()
            }
        }, 800)
    }

    fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
}
