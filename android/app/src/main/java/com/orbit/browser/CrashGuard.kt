package com.orbit.browser

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
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
        val appCtx = ctx.applicationContext
        // v1.4.6 — app UPDATE ke baad purana crash log GALAT build ka hota hai:
        // filesDir APK update par survive karta hai, aur purani entry naye build
        // ke fresh header ke saath dikhti thi (user ko laga "same crash abhi bhi").
        // Ab version change par purana log clear — sirf CURRENT build ke crashes.
        val vcFile = File(appCtx.filesDir, "crash-vc.txt")
        val lastVc = runCatching { vcFile.readText().trim().toIntOrNull() }.getOrNull()
        if (lastVc != null && lastVc != BuildConfig.VERSION_CODE) {
            runCatching { logFile(appCtx).delete() }
        }
        runCatching { vcFile.writeText(BuildConfig.VERSION_CODE.toString()) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(ctx.applicationContext, thread, throwable) }
            // system ko bhi do — "app stopped" dialog + process death normal rahe
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun logFile(ctx: Context): File = File(ctx.filesDir, "crash-log.txt")

    /** Caught exception ko bhi log karo (try/catch ke andar pakda, par dikhana hai) */
    fun logCrash(ctx: Context, t: Throwable) {
        runCatching { writeCrash(ctx.applicationContext, Thread.currentThread(), t) }
    }

    fun stackOf(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    /** crash timestamp "yyyy-MM-dd HH:mm:ss" 48h ke andar hai? */
    fun isFresh(ts: String): Boolean = runCatching {
        val age = System.currentTimeMillis() -
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(ts)!!.time
        age in 0..(48 * 3600_000L)
    }.getOrDefault(false)

    /** Emergency screen ka header — device/app/WebView provider info (screenshot se
     *  bhi poora context mil jaye) */
    fun deviceInfo(ctx: Context): String {
        val wvPkg = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        return "Orbit v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})\n" +
            "${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} " +
            "(SDK ${Build.VERSION.SDK_INT})\n" +
            "WebView: ${wvPkg?.packageName ?: "unknown"} v${wvPkg?.versionName ?: "?"}"
    }

    private fun writeCrash(ctx: Context, thread: Thread, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        // v1.4.6 — har entry me BUILD NUMBER stamp: emergency screen ka header to
        // CURRENT build ka hota hai, isliye purane build ka crash dikhta to
        // confuse hota. Stamp se ek nazar me pata chalta hai kaunsa build crash hua.
        val entry = "\n==== CRASH @ $ts (thread: ${thread.name}) ====\n" +
            "[app build ${BuildConfig.VERSION_CODE} — v${BuildConfig.VERSION_NAME}]\n${sw}\n"
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

    fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
}
