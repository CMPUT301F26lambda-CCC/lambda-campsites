// Purpose: builds the Android app: its plugins, SDK levels, the Google Maps
// key and the libraries it uses.
// Last updated: 2026-11-03 (#98).
// Outstanding issues: None known.
// AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped add the
// navigation, lifecycle, icon and coroutines-test dependencies, the Dokka
// setup, the Firebase setup and the emulator test runner in October 2026.
// Sashreek Addanki reviewed and committed them.
//
// ZXing was added in November 2026 with Gemini 3.8 Flash (Google DeepMind)
// for QR code generation and decoding (US 01.12.01, US 02.02.01).
// Raghav Sethi reviewed and committed it.
// The Coil dependencies were added by Aarush Bhat with the assistance of
// Claude Sonnet 5 (Anthropic), through Claude Code, in November 2026, for
// loading campsite photos. Aarush Bhat reviewed and committed them.
// The firebase-messaging dependency was added by Aarush Bhat with the
// assistance of Claude Sonnet 5 (Anthropic), through Claude Code, in
// November 2026. Aarush Bhat reviewed and committed it.
// Flow 12 feature owner: Lalith Valluru. OpenAI GPT-6 (OpenAI), through Codex,
// assisted with the callable Functions dependency. Human review pending.
// Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
// Sashreek Addanki read MAPS_API_KEY from local.properties into the manifest,
// on November 3, 2026. Sashreek Addanki reviewed it.
// Flow 16: GPT-6 (OpenAI), through Codex, assisted me with the CameraX
// dependencies used by the entrant QR scanner. Human review is pending.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    // Reads app/google-services.json, which connects the app to our Firebase project.
    alias(libs.plugins.google.services)
}

// The Google Maps key, from MAPS_API_KEY in local.properties, which git ignores
// (#98). Without it the app still builds, like on CI, but maps stay grey.
val mapsApiKey: String = Properties().apply {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) localProperties.inputStream().use { load(it) }
}.getProperty("MAPS_API_KEY", "")

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

        // Fills ${MAPS_API_KEY} in AndroidManifest.xml.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
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
    // The owner screens use icons outside the core set, like QrCode and CalendarMonth.
    implementation(libs.androidx.compose.material.icons.extended)
    // The BoM picks versions of the Firebase libraries that work together.
    implementation(platform(libs.firebase.bom))
    //implementation(libs.androidx.ui.test.junit4)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    // Issue #151: GPT-6 (OpenAI), through Codex, assisted me with the catalog migration.
    implementation(libs.firebase.functions)
    // Adds await(), so suspend functions can wait for Firebase calls.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Loads campsite photos from their Storage download URL.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.zxing.core)
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
