plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.libxposed.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    buildFeatures {
        androidResources = false
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.9.1")
}
