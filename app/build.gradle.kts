plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val libxposedApiVersion = "101.0.0"

android {
    namespace = "dev.yasir.dialerline"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.yasir.dialerline"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DIAGNOSTICS_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "DIAGNOSTICS_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:$libxposedApiVersion")
}
