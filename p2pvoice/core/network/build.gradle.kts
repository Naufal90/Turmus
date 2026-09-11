plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.offlinep2p.core.network"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:protocol"))
    implementation(libs.kotlinx.coroutines.android)
    // Nearby Connections — actual usage begins in Phase 2. Kept here so
    // the module compiles against the transport API from day one.
    implementation(libs.play.services.nearby)
    testImplementation(libs.junit)
}
