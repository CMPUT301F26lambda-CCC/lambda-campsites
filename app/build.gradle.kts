// AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped add the
// navigation, lifecycle, icon and coroutines-test dependencies, the Dokka
// setup, the Firebase setup and the emulator test runner in October 2026.
// Sashreek Addanki reviewed and committed them.
//
// The Coil dependencies were added by Aarush Bhat with the assistance of
// Claude Sonnet 5 (Anthropic), through Claude Code, in November 2026, for
// loading campsite photos. Aarush Bhat reviewed and committed them.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    // Reads app/google-services.json, which connects the app to our Firebase project.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.lambda.campsite"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.lambda.campsite"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // Points every androidTest at the Firebase emulators. See docs/testing.md.
        testInstrumentationRunner = "com.lambda.campsite.testutil.LambdaTestRunner"
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.core)
    // The BoM picks versions of the Firebase libraries that work together.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    // Adds await(), so suspend functions can wait for Firebase calls.
    implementation(libs.kotlinx.coroutines.play.services)
    // Loads campsite photos from their Storage download URL.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Builds the KDoc website with `./gradlew :app:dokkaGenerate`, into app/build/dokka/html.
dokka {
    moduleName.set("lambda-campsite")
    dokkaSourceSets.configureEach {
        // Each class and function on the site links to its source on GitHub.
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl("https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/tree/main/app/src/main/java")
            remoteLineSuffix.set("#L")
        }
    }
}
