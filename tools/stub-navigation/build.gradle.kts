plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.kanyandula.stubnavigation"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.kanyandula.stubnavigation"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric reads merged resources and the manifest.
            isIncludeAndroidResources = true
        }
    }
}

// Defaults plus config/detekt/detekt.yml (picked up from the root by convention), as in :app.
detekt {
    buildUponDefaultConfig = true
}

tasks.withType<Test>().configureEach {
    // StubManifestContractTest reads the source manifest; without this a manifest-only change leaves it UP-TO-DATE.
    inputs.file("src/main/AndroidManifest.xml")
    // Robolectric at SDK 36 (Android 16) touches jdk.internal.access on JDK 21.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
