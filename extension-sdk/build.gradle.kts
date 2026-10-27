plugins {
    id("com.android.library")
    kotlin("android")
}
android {
    namespace = "app.reva.sdk"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
