plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing. The keystore is never committed. Provide it through environment variables:
//   KALEIDO_KEYSTORE_PATH   path to the .jks/.keystore file
//   KALEIDO_STORE_PASSWORD  keystore password
//   KALEIDO_KEY_ALIAS       key alias inside the keystore
//   KALEIDO_KEY_PASSWORD    key password
// Local builds without a keystore fall back to the debug key. CI requires the real key
// (KALEIDO_REQUIRE_RELEASE_KEY=true) so it never ships an APK signed with the debug key.
val releaseKeystorePath: String? = System.getenv("KALEIDO_KEYSTORE_PATH")
val releaseStorePassword: String? = System.getenv("KALEIDO_STORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("KALEIDO_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("KALEIDO_KEY_PASSWORD")
// All four values or none: a partial setup falls back to debug, never to a broken release config.
val hasReleaseKey: Boolean = releaseKeystorePath != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null &&
    file(releaseKeystorePath).let { it.isFile && it.length() > 0L }
val requireReleaseKey: Boolean = System.getenv("KALEIDO_REQUIRE_RELEASE_KEY") == "true"

if (requireReleaseKey && !hasReleaseKey) {
    throw GradleException(
        "Release keystore missing. Set the ANDROID_KEYSTORE_BASE64 secret and the " +
            "KALEIDO_STORE_PASSWORD, KALEIDO_KEY_ALIAS and KALEIDO_KEY_PASSWORD secrets."
    )
}

android {
    namespace = "com.tabgl.kaleido"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tabgl.kaleido"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Produce one APK per supported ABI (splits and abiFilters must not be combined).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
}
