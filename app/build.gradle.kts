import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.ideathon.kondaeshield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ideathon.kondaeshield"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        val transcriptionModel = localProperties.getProperty(
            "OPENAI_TRANSCRIPTION_MODEL",
            "gpt-4o-mini-transcribe",
        )
        val summaryModel = localProperties.getProperty(
            "OPENAI_SUMMARY_MODEL",
            "gpt-5.4-mini",
        )
        val groqTranscriptionModel = localProperties.getProperty(
            "GROQ_TRANSCRIPTION_MODEL",
            "whisper-large-v3-turbo",
        )

        buildConfigField("String", "OPENAI_TRANSCRIPTION_MODEL", transcriptionModel.asBuildConfigString())
        buildConfigField("String", "OPENAI_SUMMARY_MODEL", summaryModel.asBuildConfigString())
        buildConfigField("String", "GROQ_TRANSCRIPTION_MODEL", groqTranscriptionModel.asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
