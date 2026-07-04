plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pocketcraft.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketcraft.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only target real arm64 devices — no x86 emulator bloat
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Release signing — driven entirely by environment variables so the
        // keystore never lives in the repository.
        //
        // Set these env vars (locally or in GitHub Secrets via CI):
        //   SIGNING_STORE_FILE      — path to the .keystore file
        //   SIGNING_STORE_PASSWORD  — keystore password
        //   SIGNING_KEY_ALIAS       — key alias inside the keystore
        //   SIGNING_KEY_PASSWORD    — key password
        //
        // When any variable is missing the release build falls back to unsigned
        // (safe for local development; CI will always have the secrets).
        val storeFile = System.getenv("SIGNING_STORE_FILE")
        val storePass = System.getenv("SIGNING_STORE_PASSWORD")
        val keyAlias  = System.getenv("SIGNING_KEY_ALIAS")
        val keyPass   = System.getenv("SIGNING_KEY_PASSWORD")

        if (storeFile != null && storePass != null && keyAlias != null && keyPass != null) {
            create("release") {
                this.storeFile     = file(storeFile)
                this.storePassword = storePass
                this.keyAlias      = keyAlias
                this.keyPassword   = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing config when available; unsigned otherwise
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) signingConfig = releaseSigning
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM — pins all compose versions together
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Coil (image loading — used for avatars in Players tab later)
    implementation(libs.coil.compose)
}
