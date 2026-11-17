plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.realwear.thermalsample"
    // The FLIR Atlas SDK 2.24.0 aars require compileSdk 37 and minSdk 33.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.realwear.thermalsdksample"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sample only: lets a release build install on a device without a release key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        // Compressed native libs: the APK is about 100 MB smaller.
        // The libs are extracted on install, so only their 16 KB ELF alignment matters.
        jniLibs { useLegacyPackaging = true }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val flirAars = listOf("thermalsdk-release.aar", "androidsdk-release.aar").map { file("libs/$it") }
    check(flirAars.all { it.exists() }) {
        "FLIR SDK missing in app/libs. Run scripts/patch-flir-sdk.sh <atlas-java-sdk-android-X.tar.gz> first."
    }
    implementation(files(flirAars))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
}
