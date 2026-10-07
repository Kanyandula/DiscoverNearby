plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.serialization)
}

// The project's one compile SDK (gradle/libs.versions.toml); the android.car.jar path below derives from it.
val compileApi = libs.versions.android.compileSdk.get().toInt()

android {
    namespace = "com.kanyandula.discovernearby"
    compileSdk {
        version = release(compileApi)
    }

    defaultConfig {
        applicationId = "com.kanyandula.discovernearby"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
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
    testOptions {
        unitTests {
            // Robolectric reads merged resources and the manifest; createComposeRule needs both.
            isIncludeAndroidResources = true
            // Unmocked android.* calls (e.g. Log) return defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }
}

// Defaults plus config/detekt/detekt.yml (picked up from the root by convention).
detekt {
    buildUponDefaultConfig = true
}

// ManifestContractTest reads the source manifest; without this a manifest-only change leaves the test UP-TO-DATE.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml")
    // Robolectric at SDK 36 (Android 16) touches jdk.internal.access on JDK 21.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
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

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ui-test-manifest is debugImplementation; release unit tests would have no test activity to launch.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = false
    }
}
