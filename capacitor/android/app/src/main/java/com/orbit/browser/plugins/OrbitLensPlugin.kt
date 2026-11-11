package com.orbit.browser.plugins

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.orbit.browser.lens.LensEngine
import java.io.File

/**
 * OrbitLens — Module 3 native bridge.
 *
 * scan(): system camera (no CAMERA permission — ACTION_IMAGE_CAPTURE +
 * FileProvider cache) → re-encoded JPEG → Google Lens upload → results URL.
 * Results open in Orbit's own content slot (React calls back with the URL),
 * so the whole flow stays in-app. Temp photos are deleted immediately —
 * neither the original nor the upload copy ever lingers in cache.
 */
@CapacitorPlugin(name = "OrbitLens")
class OrbitLensPlugin : Plugin() {

    private var pendingPhoto: File? = null

    @PluginMethod
    fun scan(call: PluginCall) {
        val context = bridge.context
        val dir = File(context.cacheDir, "lens")
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, "scan_${System.currentTimeMillis()}.jpg")

        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        } catch (e: Exception) {
            call.reject("FileProvider misconfigured")
            return
        }

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (cameraIntent.resolveActivity(context.packageManager) == null) {
            call.reject("No camera app available")
            return
        }

        pendingPhoto = out
        startActivityForResult(call, cameraIntent, "onScanResult")
    }

    @ActivityCallback
    private fun onScanResult(call: PluginCall?, result: androidx.activity.result.ActivityResult) {
        val original = pendingPhoto
        pendingPhoto = null
        if (call == null) {
            // Call was released before the camera returned — nothing to answer.
            original?.delete()
            return
        }

        val cancelled =
            result.resultCode != Activity.RESULT_OK || original == null || !original.exists() || original.length() == 0L
        if (cancelled) {
            original?.delete()
            call.resolve(JSObject().put("cancelled", true))
            return
        }

        val uploadCopy = File(original.parentFile, "up_${original.name}")
        Thread {
            var resolved = false
            var resultsUrl: String? = null
            if (LensEngine.downscale(original, uploadCopy)) {
                resultsUrl = LensEngine.upload(uploadCopy)
                resolved = resultsUrl != null
            }
            // Privacy: nothing about the photo stays on disk.
            original.delete()
            uploadCopy.delete()

            val payload = JSObject()
            if (resolved) {
                payload.put("url", resultsUrl)
            } else {
                // Endpoint drifted or upload failed — manual upload UI.
                payload.put("url", "https://lens.google.com/")
                payload.put("fallback", true)
            }
            call.resolve(payload)
        }.start()
    }
}
