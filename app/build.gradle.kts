import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is optional and driven by a local, gitignored keystore.properties (see README).
// Its absence must not break a fresh clone's `assembleDebug` — only assembleRelease needs it, and
// even that build still succeeds unsigned if the file is missing.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    // R class / generated sources live under this package. (Distinct from applicationId below —
    // this was renamed away from the decompiled APK's artifact package name.)
    namespace = "com.originisle.android"

    // compileSdk 36 matches the platform installed on this machine (android-36). The original
    // targeted 37 (Android 17 preview); if you install the 37 platform + a matching AGP, bump.
    compileSdk = 36

    defaultConfig {
        // IMPORTANT: this applicationId is load-bearing. vivo OriginOS only honours
        // setSuperXInfosSceneList for whitelisted packages; the original author spoofed
        // AMap (com.autonavi.minimap) to get onto that whitelist. Change it and the island
        // access will likely stop working on your X200 Pro.
        applicationId = "com.autonavi.minimap"
        minSdk = 34
        targetSdk = 36
        versionCode = 13
        versionName = "1.4.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Falls back to unsigned when no keystore.properties is present (e.g. a fresh clone),
            // so `assembleRelease` never hard-fails on a missing local secret.
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    // Provides the Theme.Material3.* XML theme parents used in themes.xml (not directly imported
    // in Kotlin, but required for the manifest/activity theme resource to resolve).
    implementation(libs.google.material)
    debugImplementation(libs.androidx.ui.tooling)
}
