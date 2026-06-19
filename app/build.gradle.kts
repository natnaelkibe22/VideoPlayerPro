import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun signingProperty(name: String): String? =
    localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull

android {
    namespace = "com.natkibe.videoplayerpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.natkibe.videoplayerpro"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "0.8.1-video-only-micro-modular"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingProperty("VP_RELEASE_STORE_FILE") ?: "release.keystore")
            storePassword = signingProperty("VP_RELEASE_STORE_PASSWORD")
            keyAlias = signingProperty("VP_RELEASE_KEY_ALIAS") ?: "release"
            keyPassword = signingProperty("VP_RELEASE_KEY_PASSWORD")
        }
    }

    flavorDimensions += "target"
    productFlavors {
        create("autosky") {
            dimension = "target"
            applicationId = "com.natkibe.videoplayerpro.autosky"
            targetSdk = 31
            versionNameSuffix = "-autosky-headunit"
            manifestPlaceholders["appLabel"] = "PlayerPro AutoSky"
        }
        create("s26ultra") {
            dimension = "target"
            applicationId = "com.natkibe.videoplayerpro.s26ultra"
            versionNameSuffix = "-s26ultra"
            manifestPlaceholders["appLabel"] = "PlayerPro S26U"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media:media:1.7.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.coil-kt:coil:2.7.0")
}
