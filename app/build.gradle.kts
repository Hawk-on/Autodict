plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.autodict"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autodict"
        minSdk = 26
        targetSdk = 35

        // Versjon kjem frå release-taggen (vX.Y.Z) via CI – sjå RELEASING.md.
        // Lokale/debug-byggjer brukar fallback under.
        versionName = System.getenv("RELEASE_VERSION_NAME") ?: "0.1.0"
        versionCode = System.getenv("RELEASE_VERSION_CODE")?.toInt() ?: 1

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Berre arm64 er aktuelt for whisper.cpp på reelle telefonar (M4).
        // x86 kan leggjast til for emulator ved behov.
    }

    signingConfigs {
        // Release-keystore blir berre levert av CI via env-variablar (sjå RELEASING.md).
        // Aldri commit keystore eller passord (CLAUDE.md-prinsipp 6).
        create("release") {
            System.getenv("RELEASE_KEYSTORE_FILE")?.let { keystorePath ->
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
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
            // Signerast berre når keystore er levert via env. Utan nøkkel blir release-APK-en
            // usignert — greitt for lokale testbyggjer, men ikkje for distribusjon.
            signingConfigs.getByName("release").storeFile?.let {
                signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
