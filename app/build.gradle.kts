import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Sheet endpoint and secret live in local.properties so they never reach git.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.karthi.expense"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.karthi.expense"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4"

        buildConfigField("String", "SHEET_URL", "\"${localProps.getProperty("sheet.url", "")}\"")
        buildConfigField("String", "SHEET_TOKEN", "\"${localProps.getProperty("sheet.token", "")}\"")
    }

    // Fixed debug key, committed on purpose: every CI build gets the same signature,
    // so a new APK installs over the old one without losing the app's data.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // The installed APK is a release build: debug builds of Compose run far slower
        // (no R8, no ahead-of-time compiled UI code), which shows up as janky scrolling.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.haze)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
}
