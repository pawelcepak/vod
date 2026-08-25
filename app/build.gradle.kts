plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.szynolandia.mjakmiloscusb"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.szynolandia.mjakmiloscusb"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("VOD_KEYSTORE_PATH")
            val storePasswordEnv = System.getenv("VOD_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("VOD_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("VOD_KEY_PASSWORD")

            if (
                !storePath.isNullOrBlank() &&
                !storePasswordEnv.isNullOrBlank() &&
                !keyAliasEnv.isNullOrBlank() &&
                !keyPasswordEnv.isNullOrBlank()
            ) {
                storeFile = file(storePath)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil:2.7.0")

    val youtubedlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
}
