plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.8.20"

}

android {
    namespace = "com.example.iotwificlient"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.iotwificlient"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11" // oppure usa la versione di compose nel tuo catalogo
    }
}


dependencies {
    // Wear OS
    implementation(libs.play.services.wearable)

    // Jetpack Compose base
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material:material:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")

    // HEALTH CONNECT: Questa è l'API raccomandata per l'integrazione con Health Connect.
    // Ho rimosso la vecchia "health-services-client" per evitare conflitti e usare l'API più moderna.
    // implementation("androidx.health.connect:connect-client:1.1.0")

    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Compose per Wear OS
    implementation("androidx.wear.compose:compose-material:1.2.1")

    // OkHttp (REST Client)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.appcompat)

    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

}