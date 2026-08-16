plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // FIX: Added the version number and 'apply false'
    id("com.google.gms.google-services") version "4.5.0" apply false
}