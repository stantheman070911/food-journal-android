plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The personal signing key lives outside the repo, in ~/.gradle/gradle.properties as
// foodJournal.signing.storeFile, .storePassword, .keyAlias, and .keyPassword (see DECISIONS.md).
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
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.core)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
