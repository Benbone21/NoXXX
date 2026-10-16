plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing reads the key from outside the repository: ~/.gradle/gradle.properties or environment
// variables (NOXXX_STORE_FILE, NOXXX_STORE_PASSWORD, NOXXX_KEY_ALIAS, NOXXX_KEY_PASSWORD). Without them the
// release APK is left unsigned, which is what F-Droid expects: it signs with its own key.
fun secret(name: String): String? = providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull
val releaseStoreFile = secret("NOXXX_STORE_FILE")

android {
    namespace = "com.noxxx.guard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noxxx.guard"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        ndk {
            // Phones plus 64-bit emulators; drops rarely used 32-bit x86 to keep the APK smaller.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = secret("NOXXX_STORE_PASSWORD")
                keyAlias = secret("NOXXX_KEY_ALIAS") ?: "noxxx"
                keyPassword = secret("NOXXX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
    androidResources {
        noCompress += "tflite"
    }
    // Leave out AGP's encrypted dependency report: only Google can read it, and F-Droid's scanner rejects it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.litert)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
