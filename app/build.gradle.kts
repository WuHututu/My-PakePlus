plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

val splayerKeystorePath = providers.gradleProperty("SPLAYER_KEYSTORE").orNull
    ?: System.getenv("SPLAYER_KEYSTORE")
val splayerKeystorePassword = providers.gradleProperty("SPLAYER_KEYSTORE_PASSWORD").orNull
    ?: System.getenv("SPLAYER_KEYSTORE_PASSWORD")
val splayerKeyPassword = providers.gradleProperty("SPLAYER_KEY_PASSWORD").orNull
    ?: System.getenv("SPLAYER_KEY_PASSWORD")
    ?: splayerKeystorePassword
val splayerKeyAlias = providers.gradleProperty("SPLAYER_KEY_ALIAS").orNull
    ?: System.getenv("SPLAYER_KEY_ALIAS")
    ?: "splayer"
val hasReleaseSigning = !splayerKeystorePath.isNullOrBlank()

android {
    namespace = "com.app.pakeplus"
    compileSdk = 34

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(splayerKeystorePath!!)
                storePassword = splayerKeystorePassword
                keyPassword = splayerKeyPassword
                keyAlias = splayerKeyAlias
            }
        }
    }

    defaultConfig {
        applicationId = "com.splayer.webapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
