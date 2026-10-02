plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.ove.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.ove.studio"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
        ndk {
            // v0.1.0 build target (docs/ENGINE_INTEGRATION_AUDIT.md §13.3)
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            // The release keystore is provisioned by CI via repo secrets
            // (OVE_ANDROID_KEYSTORE_BASE64/PASSWORD/ALIAS/KEY_PASSWORD) and is
            // deliberately NEVER committed (charter rule). When the env is
            // absent (local builds, forks) nothing is configured here and the
            // release build type falls back to the debug key below — an
            // unsigned APK (uninstallable, v0.1.0 lesson) is never produced.
            val ksPath = System.getenv("OVE_KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("OVE_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("OVE_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("OVE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksPath = System.getenv("OVE_KEYSTORE_PATH")
            signingConfig = if (ksPath != null && file(ksPath).exists()) {
                signingConfigs.getByName("release")
            } else {
                // Installable fallback (debug-signed); CI verifies + records
                // the actual signer either way.
                signingConfigs.getByName("debug")
            }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
