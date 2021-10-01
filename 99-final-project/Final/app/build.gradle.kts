import java.util.*

plugins {
    id("com.android.application")
    kotlin("android")
}

val javaVersion = JavaVersion.VERSION_11

// Access to private secrets and keys that shouldn't be checked into VCS
val localProperties = rootProject.file("local.properties").reader().use { reader ->
    Properties().also { it.load(reader) }
}

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "com.raywenderlich.android.club"
        minSdk = 23
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add a config field for the Agora App ID
        buildConfigField(
            "String",
            "AGORA_APP_ID",
            "\"${localProperties.getProperty("AGORA_APP_ID")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.google.android.material:material:1.4.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")

    implementation("io.agora.rtc:voice-sdk:3.5.0.3")
    implementation("io.agora.rtm:rtm-sdk:1.4.2")

    testImplementation("junit:junit:4.13.2")
}
