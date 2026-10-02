// Single source for the compile SDK; the android.car.jar path below derives from it.
val compileApi = 37

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kanyandula.discovernearby"
    compileSdk {
        version = release(compileApi)
    }

    defaultConfig {
        applicationId = "com.kanyandula.discovernearby"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// ManifestContractTest reads the source manifest; without this a manifest-only change leaves the test UP-TO-DATE.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml")
}

dependencies {
    // Car API (CarUxRestrictionsManager), provided by AAOS at runtime (docs/03 §6.1).
    compileOnly(
        files(androidComponents.sdkComponents.sdkDirectory.map { it.file("platforms/android-$compileApi.0/optional/android.car.jar") }),
    )

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
