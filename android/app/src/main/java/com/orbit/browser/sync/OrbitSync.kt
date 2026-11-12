package com.orbit.browser.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.orbit.browser.CrashGuard
import com.orbit.browser.LocalStore
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Module 4 port — Orbit Sync (Google) for the ORIGINAL native app.
 *
 * Native Google Sign-In (play-services-auth) → idToken → Firebase Auth
 * (identitytoolkit REST) → Firestore REST pull/push. NO google-services.json,
 * NO extra SDK — sirf 3 config values jo Settings dialog me user deta hai
 * (Firebase console → Project settings → General → Web app + Web client ID).
 *
 * Config khali = sync honestly "off" bolega (jhooth nahi) — M4 design wahi tha.
 *
 * Merge engine (M4 syncEngine.ts ka port, "merge-by-updatedAt with tombstones"):
 *   • har entry ka updatedAt (local = ts) — newer wins outright;
 *   • per-user cursor (syncCursor) har round ko bound karta hai;
 *   • bookmark deletion = tombstone doc (deleted:true) — sab devices pull
 *     karne tak zinda; history me tombstones nahi (v1, Fire-button wipe local only);
 *   • doc id = sha256(url) hex — URL-safe, deterministic dono taraf.
 *
 * Sab network I/O background thread par; callbacks main thread par.
 */
object OrbitSync {

    const val REQ_SIGN_IN = 4203

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())

    private const val MAX_DOCS = 300

    /* ------------------------------- status --------------------------------- */

    private fun cfg(ctx: Context): Triple<String, String, String> {
        val clientId = LocalStore.getSetting(ctx, "syncWebClientId", "").trim()
        val apiKey = LocalStore.getSetting(ctx, "syncApiKey", "").trim()
        val projectId = LocalStore.getSetting(ctx, "syncProjectId", "").trim()
        return Triple(clientId, apiKey, projectId)
    }

    fun isConfigured(ctx: Context): Boolean {
        val (clientId, apiKey, projectId) = cfg(ctx)
        return clientId.isNotEmpty() && apiKey.isNotEmpty() && projectId.isNotEmpty()
    }

    fun isSignedIn(ctx: Context): Boolean =
        LocalStore.getSetting(ctx, "syncUid", "").isNotEmpty() &&
            LocalStore.getSetting(ctx, "syncRefreshToken", "").isNotEmpty()

    fun statusLine(ctx: Context): String {
        val base = when {
            !isConfigured(ctx) ->
                "Status: config not set — fill the 3 values below and Save (Firebase console → Project settings → Web app + Google Web client ID)."
            !isSignedIn(ctx) ->
                "Status: config saved — now tap 'Sign in with Google'."
            else ->
                "Status: signed in (${LocalStore.getSetting(ctx, "syncEmail", "Google account")})."
        }
        return "$base\nLast sync: ${relative(LocalStore.getSetting(ctx, "syncCursor", "0").toLongOrNull() ?: 0L)}"
    }

    private fun relative(ts: Long): String {
        if (ts <= 0) return "never"
        val mins = (System.currentTimeMillis() - ts) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }

    /* ------------------------------ sign-in --------------------------------- */

    /** Native Google sign-in — system account picker khulta hai (M4 jaisa). */
    fun signIn(activity: Activity) {
        if (!isConfigured(activity)) {
            CrashGuard.toast(activity, "Save the config first — Web client ID is required")
            return
        }
        val ok = runCatching {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(cfg(activity).first)
                .requestEmail()
                .build()
            activity.startActivityForResult(
                GoogleSignIn.getClient(activity, gso).signInIntent,
                REQ_SIGN_IN,
            )
            true
        }.getOrDefault(false)
        if (!ok) CrashGuard.toast(activity, "Google sign-in didn't open — check Play Services")
    }

    /** MainActivity.onActivityResult se aata hai — idToken → Firebase session. */
    fun handleSignInResult(activity: Activity, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            CrashGuard.toast(activity, "Sign-in cancelled")
            return
        }
        Thread {
            val (_, apiKey, _) = cfg(activity)
            val ok = runCatching {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken ?: error("Google didn't return an idToken")
                val body = JSONObject()
                    .put("postBody", "id_token=$idToken&providerId=google.com")
                    .put("requestUri", "http://localhost")
                    .put("returnIdpCredential", true)
                    .put("returnSecureToken", true)
                val o = JSONObject(
                    post(
                        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey",
                        body.toString(),
                    ),
                )
                LocalStore.setSetting(activity, "syncUid", o.getString("localId"))
                LocalStore.setSetting(activity, "syncRefreshToken", o.getString("refreshToken"))
                LocalStore.setSetting(activity, "syncEmail", o.optString("email", account.email ?: "Google account"))
                true
            }.getOrDefault(false)
            // NOTE: sync fail = normal user error (galat config/network) — CrashGuard
            // me log NAHI karte, warna agla launch survival/emergency screen dikhata.
            main.post {
                CrashGuard.toast(
                    activity,
                    if (ok) "Signed in — now tap 'Sync now'"
                    else "Sign-in failed — check Web client ID / API key",
                )
            }
        }.start()
    }

    /* ------------------------------- sync ----------------------------------- */

    /** Bookmarks + history pull/push — merge-by-updatedAt (newer wins). */
    fun syncNow(ctx: Context, done: (Boolean, String) -> Unit) {
        Thread {
            val result = runCatching { runSync(ctx) }.getOrElse {
                Pair(false, "Sync failed — check network or Firebase config")
            }
            main.post { done(result.first, result.second) }
        }.start()
    }

    private class RemoteDoc(val url: String, val title: String, val updatedAt: Long, val deleted: Boolean)

    private fun runSync(ctx: Context): Pair<Boolean, String> {
        if (!isConfigured(ctx)) return Pair(false, "Sync is off — save the Firebase config first")
        if (!isSignedIn(ctx)) return Pair(false, "Sync is off — sign in with Google first")
        val (_, apiKey, projectId) = cfg(ctx)
        val uid = LocalStore.getSetting(ctx, "syncUid", "")
        val token = runCatching { refreshIdToken(ctx, apiKey) }.getOrNull()
            ?: return Pair(false, "Session expired — sign in again")
        val base = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/orbit/$uid"

        var added = 0
        var removed = 0
        var pushed = 0
        var newestRemote = LocalStore.getSetting(ctx, "syncCursor", "0").toLongOrNull() ?: 0L

        // ---------------- bookmarks: pull + merge ----------------
        val remoteB = listDocs("$base/bookmarks", token)
        val localB = LocalStore.listBookmarks(ctx)
        for (d in remoteB) {
            if (d.updatedAt > newestRemote) newestRemote = d.updatedAt
            val mine = localB.firstOrNull { it.url == d.url }
            if (d.deleted) {
                if (mine != null && d.updatedAt > mine.ts) {
                    LocalStore.dropBookmark(ctx, d.url)
                    removed++
                }
            } else if (mine == null || d.updatedAt > mine.ts) {
                LocalStore.putBookmark(ctx, d.url, d.title, d.updatedAt)
                added++
            }
        }
        // ---------------- bookmarks: push (local newer / remote absent) --------
        for (b in localB) {
            val r = remoteB.firstOrNull { it.url == b.url }
            if (r == null || b.ts > r.updatedAt) {
                pushDoc(
                    "$base/bookmarks/${docId(b.url)}", token,
                    JSONObject()
                        .put("url", JSONObject().put("stringValue", b.url))
                        .put("title", JSONObject().put("stringValue", b.title))
                        .put("updatedAt", JSONObject().put("integerValue", b.ts)),
                )
                pushed++
            }
        }

        // ---------------- history: pull + merge (no tombstones v1) -------------
        val remoteH = listDocs("$base/history", token)
        val localHByUrl = LocalStore.listHistory(ctx)
            .groupBy { it.url }
            .mapValues { (_, v) -> v.maxByOrNull { it.ts }!! }
        for (d in remoteH) {
            if (d.updatedAt > newestRemote) newestRemote = d.updatedAt
            val mine = localHByUrl[d.url]
            if (d.deleted) continue
            if (mine == null || d.updatedAt > mine.ts) {
                LocalStore.putHistory(ctx, d.url, d.title, d.updatedAt)
                added++
            }
        }
        // ---------------- history: push ----------------------------------------
        var hPushed = 0
        for ((_, item) in localHByUrl) {
            val r = remoteH.firstOrNull { it.url == item.url }
            if (r == null || item.ts > r.updatedAt) {
                pushDoc(
                    "$base/history/${docId(item.url)}", token,
                    JSONObject()
                        .put("url", JSONObject().put("stringValue", item.url))
                        .put("title", JSONObject().put("stringValue", item.title))
                        .put("updatedAt", JSONObject().put("integerValue", item.ts)),
                )
                hPushed++
            }
        }
        pushed += hPushed

        // cursor advance — skewed clock kabhi doosre device ke writes skip na kare
        LocalStore.setSetting(ctx, "syncCursor", maxOf(System.currentTimeMillis(), newestRemote).toString())
        return Pair(true, "Sync complete — bookmarks: +$added/-$removed, push $pushed")
    }

    /* ------------------------------- REST ----------------------------------- */

    /** 1h baad expire hota Firebase idToken — refresh_token se naya lo. */
    private fun refreshIdToken(ctx: Context, apiKey: String): String {
        val refresh = LocalStore.getSetting(ctx, "syncRefreshToken", "")
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        val req = Request.Builder()
            .url("https://securetoken.googleapis.com/v1/token?key=$apiKey")
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: error("empty refresh response")
            if (!resp.isSuccessful) error("refresh fail ${resp.code}: ${text.take(200)}")
            val o = JSONObject(text)
            // securetoken rotated refresh token bhi wapas deta hai — save (re-use safe)
            runCatching { LocalStore.setSetting(ctx, "syncRefreshToken", o.getString("refresh_token")) }
            return o.getString("id_token")
        }
    }

    private fun listDocs(url: String, token: String): List<RemoteDoc> {
        val req = Request.Builder()
            .url("$url?pageSize=$MAX_DOCS")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "null"
            if (resp.code == 404) return emptyList() // collection abhi bana hi nahi
            if (!resp.isSuccessful) error("pull fail ${resp.code}: ${text.take(200)}")
            val docs = JSONObject(text).optJSONArray("documents") ?: return emptyList()
            val out = mutableListOf<RemoteDoc>()
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val f = d.optJSONObject("fields") ?: continue
                val u = f.optJSONObject("url")?.optString("stringValue") ?: continue
                out.add(
                    RemoteDoc(
                        url = u,
                        title = f.optJSONObject("title")?.optString("stringValue") ?: "",
                        // Firestore integerValue STRING ke roop me aata hai — optLong parse karta hai
                        updatedAt = f.optJSONObject("updatedAt")?.optLong("integerValue") ?: 0L,
                        deleted = f.optJSONObject("deleted")?.optBoolean("booleanValue") ?: false,
                    ),
                )
            }
            return out
        }
    }

    private fun pushDoc(docUrl: String, token: String, fields: JSONObject) {
        val req = Request.Builder()
            .url(docUrl)
            .header("Authorization", "Bearer $token")
            .patch("{\"fields\":$fields}".toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("push fail ${resp.code}")
        }
    }

    private fun post(url: String, body: String): String {
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: error("empty response")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
            return text
        }
    }

    /** Firestore doc id — URL-safe, deterministic dono devices par same. */
    private fun docId(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
