import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GLM API key local.properties se aata hai (gitignored) — repo me kabhi nahi jata.
// Format: ORBIT_GLM_KEY=...  (optional — AI backend URL ke bina bhi browser chalta hai)
val orbitKey: String = run {
    val lf = rootProject.file("local.properties")
    if (lf.exists()) {
        val p = Properties().apply { lf.inputStream().use { load(it) } }
        p.getProperty("ORBIT_GLM_KEY", "")
    } else ""
}

android {
    namespace = "com.orbit.browser"
    compileSdk = 35

    // STABLE signing — repo me committed orbit.keystore. Har build (local + CI)
    // SAME key se sign hota hai, isliye naya version purane ke UPAR install hota
    // hai (data safe). Bina iske CI ka ephemeral debug key har release me badal
    // jata tha aur OTA upgrade fail ho jata tha ("App not installed").
    signingConfigs {
        create("orbit") {
            storeFile = rootProject.file("orbit.keystore")
            storePassword = "orbitbrowser"
            keyAlias = "orbit"
            keyPassword = "orbitbrowser"
        }
    }

    defaultConfig {
        applicationId = "com.orbit.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.3.7"
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
