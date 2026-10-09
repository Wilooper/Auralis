plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "app.auralis.playback"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core-model"))
    api("androidx.media3:media3-common:1.6.1")
    api("androidx.media3:media3-session:1.6.1")
    implementation("org.videolan.android:libvlc-all:3.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
