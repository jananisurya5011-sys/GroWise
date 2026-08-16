plugins {
    // REMOVED the duplicate id("com.android.application") line here
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") // No version needed here!
}

android {
    namespace = "com.simats.growise"
    compileSdk = 36
    // REQUIRED: Prevents TFLite file compression so the interpreter can read it
    androidResources {
        noCompress += "tflite"
    }

    defaultConfig {
        applicationId = "com.simats.growise"
        minSdk = 24
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// FIX: This resolution strategy forces Gradle to block the broken Kotlin 2.2.0 update
// across all Firebase/Google dependencies.
configurations.all {
    resolutionStrategy {
        force("com.google.android.gms:play-services-measurement:22.0.2")
        force("com.google.android.gms:play-services-measurement-api:22.0.2")
        force("com.google.android.gms:play-services-measurement-impl:22.0.2")
        force("com.google.android.gms:play-services-measurement-sdk:22.0.2")
        force("com.google.android.gms:play-services-measurement-sdk-api:22.0.2")
        force("com.google.android.gms:play-services-measurement-base:22.0.2")
    }
}

dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Removed the duplicate 2.4.0 Coil dependency and kept the latest
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Firebase BoM to automatically resolve Firebase versions
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-storage")
    // Firebase services
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth") {
        version {
            strictly("23.1.0")
        }
    }
    implementation("com.google.firebase:firebase-firestore:25.0.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // FIXED: Removed the extra '.capturable' from the group ID
    implementation("dev.shreyaspatil:capturable:2.1.0")

    // Added OpenStreetMap dependency for free mapping API
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}