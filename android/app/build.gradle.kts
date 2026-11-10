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

    defaultConfig {
        applicationId = "com.orbit.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
        buildConfigField("String", "GLM_KEY", "\"$orbitKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
