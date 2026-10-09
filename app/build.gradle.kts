plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// CI supplies these only to a trusted, tagged release build. Never store keys here.
val releaseKeystore = providers.environmentVariable("AURALIS_KEYSTORE_PATH").orNull
android {
    namespace = "app.auralis"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.auralis"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.0-dev"
    }
    if (releaseKeystore != null) {
        signingConfigs {
            create("ciRelease") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("AURALIS_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("AURALIS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("AURALIS_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("ciRelease")
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("auralis.fixtures", rootProject.file("fixtures").absolutePath)
            test.systemProperty("auralis.evidence", rootProject.file("evidence").absolutePath)
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-playback"))
    implementation("androidx.media3:media3-extractor:1.6.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
