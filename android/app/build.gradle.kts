import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------- credentials
// SIGNING + API credentials — 3-layer resolution (priority order):
//   1. ENVIRONMENT VARIABLES      → GitHub Actions secrets / shell exports
//      (ORBIT_KEYSTORE_PATH, ORBIT_STORE_PASSWORD, ORBIT_KEY_ALIAS,
//       ORBIT_KEY_PASSWORD, ORBIT_GLM_KEY)
//   2. local.properties           → local dev machine (gitignored, repo me kabhi nahi)
//   3. Committed fallback         → orbit.keystore repo me (stable OTA signing)
//
// RULE: key MATERIAL hamesha SAME rehna chahiye — chahe source koi bhi ho.
// Agar keystore/alias/password kisi build me badal gaya to Android naya version
// purane ke UPAR install hone se block kar deta hai ("App not installed",
// signature conflict) — isliye fallback chain deliberately identical rakha hai.

val localProps: Properties by lazy {
    Properties().apply {
        val lf = rootProject.file("local.properties")
        if (lf.exists()) lf.inputStream().use { load(it) }
    }
}

fun signingValue(envKey: String, propKey: String, fallback: String): String =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: fallback

val orbitKeystorePath = signingValue("ORBIT_KEYSTORE_PATH", "ORBIT_KEYSTORE_PATH", "orbit.keystore")
val orbitStorePassword = signingValue("ORBIT_STORE_PASSWORD", "ORBIT_STORE_PASSWORD", "orbitbrowser")
val orbitKeyAlias = signingValue("ORBIT_KEY_ALIAS", "ORBIT_KEY_ALIAS", "orbit")
val orbitKeyPassword = signingValue("ORBIT_KEY_PASSWORD", "ORBIT_KEY_PASSWORD", "orbitbrowser")

// GLM API key (optional — AI backend URL ke bina bhi browser chalta hai)
val orbitKey: String = signingValue("ORBIT_GLM_KEY", "ORBIT_GLM_KEY", "")

android {
    namespace = "com.orbit.browser"
    compileSdk = 35

    // STABLE signing — har build (local + CI) SAME key se sign hota hai, isliye
    // naya version purane ke UPAR install hota hai (data safe) aur Play Protect
    // ko bhi consistent signature milta hai (unknown-app friction kam hota hai).
    signingConfigs {
        create("orbit") {
            val kf = rootProject.file(orbitKeystorePath)
            if (!kf.exists()) {
                throw GradleException(
                    "Orbit keystore not found at: $kf\n" +
                        "Set ORBIT_KEYSTORE_PATH (env) or ORBIT_KEYSTORE_PATH (local.properties), " +
                        "or keep the committed orbit.keystore in android/.",
                )
            }
            storeFile = kf
            storePassword = orbitStorePassword
            keyAlias = orbitKeyAlias
            keyPassword = orbitKeyPassword
            // Signing schemes: v1 (JAR, Android<7 fallback) + v2 (7+ fast verify)
            // + v3 (9+ key rotation support) — sab ON, koi scheme-missing reflow nahi.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.orbit.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 33
        versionName = "1.6.0"
        buildConfigField("String", "GLM_KEY", "\"$orbitKey\"")
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("orbit")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("orbit")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1") // FileProvider (OTA installer)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0") // Chrome pull-to-refresh
    implementation("androidx.webkit:webkit:1.11.0") // document-start JS (anti-fingerprinting)
    implementation("com.google.zxing:core:3.5.3") // LENS scanner — QR/barcode LOCAL decode (pure Java, offline)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media:media:1.7.0") // MODULE 3 — MediaSession + lock-screen playback controls
    implementation("com.google.android.gms:play-services-auth:21.2.0") // MODULE 4 — Google Sign-In (sync)
}
