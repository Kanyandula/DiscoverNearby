plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.kanyandula.calprobe"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }
    defaultConfig {
        applicationId = "com.kanyandula.calprobe"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Car App Library, latest stable on Google Maven (2026-10-05). Kept out of the root catalog on purpose.
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-automotive:1.7.0")
}
