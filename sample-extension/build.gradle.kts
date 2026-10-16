plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "app.reva.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.reva.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":extension-sdk")) }
