plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.tambovkaef.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.tambovkaef.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.6.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        implementation("com.google.mlkit:text-recognition:16.0.1")
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}
