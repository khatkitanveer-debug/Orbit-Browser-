package com.orbit.browser.plugins

import android.app.Activity
import android.content.Intent
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * OrbitSync — Module 4 native bridge.
 *
 * Google Sign-In via Google Identity (play-services-auth). This path needs
 * ONLY the OAuth **Web client ID** — no google-services.json, no SHA-1
 * upload, so the APK build stays green before Firebase is configured.
 * The returned idToken is exchanged for a Firebase Auth session by the JS
 * layer (syncClient.ts → signInWithCredential), which then owns Firestore.
 *
 * Config: capacitor.config.ts → plugins.OrbitSync.webClientId
 * (Firebase console → Authentication → Google → Web SDK client ID).
 */
@CapacitorPlugin(name = "OrbitSync")
class OrbitSyncPlugin : Plugin() {

    private var client: GoogleSignInClient? = null

    private fun webClientId(): String? =
        bridge.config.getPluginConfiguration("OrbitSync")?.getString("webClientId")

    @PluginMethod
    fun signIn(call: PluginCall) {
        val clientId = webClientId()
        if (clientId.isNullOrBlank()) {
            call.reject("Set plugins.OrbitSync.webClientId in capacitor.config.ts first")
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        val signInClient = GoogleSignIn.getClient(bridge.activity, options)
        client = signInClient
        startActivityForResult(call, signInClient.signInIntent, "onSignInResult")
    }

    @ActivityCallback
    private fun onSignInResult(call: PluginCall?, result: androidx.activity.result.ActivityResult) {
        if (call == null) return
        val accountTask = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = accountTask.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                call.reject("Google returned no idToken (check the Web client ID)")
                return
            }
            call.resolve(
                JSObject()
                    .put("idToken", idToken)
                    .put("email", account.email)
                    .put("displayName", account.displayName)
                    .put("photoUrl", account.photoUrl?.toString()),
            )
        } catch (e: ApiException) {
            if (e.statusCode == 12501) {
                call.resolve(JSObject().put("cancelled", true))
            } else {
                call.reject("Google sign-in failed (code ${e.statusCode})")
            }
        }
    }

    @PluginMethod
    fun signOut(call: PluginCall) {
        val active = client
            ?: GoogleSignIn.getClient(bridge.activity, GoogleSignInOptions.DEFAULT_SIGN_IN)
        active.signOut().addOnCompleteListener { call.resolve() }
    }

    override fun handleOnDestroy() {
        client = null
    }
}
