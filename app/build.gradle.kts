plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ir.weirdnet.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.weirdnet.client"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Debug signing uses the default Android debug keystore automatically.
        // For release, create your own keystore and fill this in -- see
        // docs/BUILD.md "Creating a release build" for exact steps.
        create("release") {
            val storeFilePath = System.getenv("WEIRDNET_KEYSTORE_PATH")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("WEIRDNET_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WEIRDNET_KEY_ALIAS")
                keyPassword = System.getenv("WEIRDNET_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to debug signing automatically if no keystore env vars are set,
            // so `assembleRelease` still produces an installable (unsigned-for-store) APK.
            signingConfig = if (System.getenv("WEIRDNET_KEYSTORE_PATH") != null) {
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
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
        // WireGuard's native .so libraries; keep uncompressed per upstream guidance.
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // NOTE ON NATIVE ABIs:
    // WireGuard-android ships prebuilt libraries for arm64-v8a, armeabi-v7a, x86, x86_64.
    // If/when the Xray-core AAR is added (see docs/XRAY_INTEGRATION.md), keep this list
    // in sync with the ABIs that AAR provides.
    splits {
        abi {
            isEnable = false // disabled by default -> ships a universal APK; enable for smaller per-ABI APKs
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }
}

dependencies {
    // --- Core / Kotlin ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- Compose (BOM-managed versions) ---
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Persistence (Room) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Encrypted local storage for secrets (private keys, passwords) ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- DataStore for settings ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- WireGuard: official Android tunnel library (Apache-2.0) ---
    // https://git.zx2c4.com/wireguard-android/ -- real, functional tunnel implementation.
    implementation("com.wireguard.android:tunnel:1.0.20230706")

    // --- Xray-core engine (real implementation) ---
    // Both AARs below are downloaded manually from their upstream GitHub
    // Releases, not fetched by Gradle -- see docs/XRAY_INTEGRATION.md for
    // exact versions and URLs. This fileTree picks up ANY .aar dropped into
    // app/libs/, so adding the two required files needs no further Gradle
    // edits. The directory can be empty (as it is in this checkout); Gradle
    // does not fail on an empty fileTree, it just contributes nothing --
    // XrayEngine.availability reports NOT_INSTALLED until they're present.
    // Required files:
    //   app/libs/libv2ray.aar              (2dust/AndroidLibXrayLite, LGPL-3.0)
    //   app/libs/hev-socks5-tunnel.aar      (heiher/hev-socks5-tunnel, MIT)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    // --- QR Scanning ---
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Room's exportSchema=true (set in AppDatabase.kt) needs a location to export to;
// these versioned JSON schemas are what Room's migration-testing tools compare
// against when you bump AppDatabase's `version` in the future.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
