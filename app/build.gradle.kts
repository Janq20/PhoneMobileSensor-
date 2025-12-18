plugins {
    // Jeśli używasz Version Catalog (libs. ...), alias(...) jest ok.
    // W przeciwnym razie zamień alias(...) na id("com.android.application")
    alias(libs.plugins.android.application)
    // Google services plugin - upewnij się, że masz go w project buildscript (classpath)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.mobilesensor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mobilesensor"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Poprawiony artifact MPAndroidChart (bez "v" przed numerem wersji)
    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")

    // Zaktualizowany BOM Firebase (możesz użyć innej wersji jeśli chcesz)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-database")

    // Pozostałe zależności wykorzystujące version catalog (libs.*).
    // Jeśli nie masz version catalog, zamień te na klasyczne coordinates.
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}