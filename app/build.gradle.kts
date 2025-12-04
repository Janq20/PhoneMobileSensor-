plugins {
    alias(libs.plugins.android.application)
    // Plugin Google Services dla integracji z Firebase
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
    // MPAndroidChart z JitPack (naprawione współrzędne)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Firebase BOM – zarządza wersjami wszystkich artefaktów Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    // Realtime Database (używasz FirebaseDatabase, DatabaseReference, itp.)
    implementation("com.google.firebase:firebase-database")

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