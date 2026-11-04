plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The personal signing key lives outside the repo, in ~/.gradle/gradle.properties as
// foodJournal.signing.storeFile, .storePassword, .keyAlias, and .keyPassword (see PRODUCT.md).
// Without all four, release builds come out unsigned, so no build signed with another key can
// ever replace the installed app and its data.
val signingProperties: Map<String, String> =
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .mapNotNull { name ->
            providers.gradleProperty("foodJournal.signing.$name").orNull?.let { name to it }
        }
        .toMap()

android {
    namespace = "com.stanley.foodjournal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.stanley.foodjournal"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.02.00"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingProperties.size == 4) {
            create("personal") {
                storeFile = file(signingProperties.getValue("storeFile"))
                storePassword = signingProperties.getValue("storePassword")
                keyAlias = signingProperties.getValue("keyAlias")
                keyPassword = signingProperties.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug and release share the key, so either can be installed over the other.
        debug {
            signingConfigs.findByName("personal")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Device tests install the app on every connected device and uninstall it afterwards, which
// would delete real meals on the phone. They run only when ANDROID_SERIAL names one emulator:
//   ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest
// The whole value must match, since a comma-separated list such as "emulator-5560,<phone>"
// would also reach the phone.
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    doFirst {
        val serial = System.getenv("ANDROID_SERIAL").orEmpty()
        check(Regex("emulator-\\d+").matches(serial)) {
            "Device tests run only on an emulator. Set ANDROID_SERIAL, e.g. ANDROID_SERIAL=emulator-5560."
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Opens the database at an older schema, for the migration test.
    androidTestImplementation(libs.androidx.room.testing)
    // Compose's test library brings Espresso 3.5, which can't start on current Android.
    androidTestImplementation(libs.androidx.test.espresso.core)
}
