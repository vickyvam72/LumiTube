plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lumitube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumitube.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "1.8.0"
    }

    // Dua aplikasi terpisah dari satu kode: YouTube Lumi & YT Music Lumi.
    flavorDimensions += "app"
    productFlavors {
        create("youtube") {
            dimension = "app"
            // Sama dengan LumiTube lama, jadi langsung meng-update aplikasi yang sudah terpasang.
            applicationId = "com.lumitube.app"
            buildConfigField("String", "APP_MODE", "\"video\"")
        }
        create("music") {
            dimension = "app"
            applicationId = "com.lumitube.music"
            buildConfigField("String", "APP_MODE", "\"music\"")
        }
    }

    // Kunci tetap supaya APK versi baru bisa langsung meng-update versi lama.
    signingConfigs {
        create("lumi") {
            storeFile = file("../keystore/lumitube.jks")
            storePassword = "lumitube"
            keyAlias = "lumitube"
            keyPassword = "lumitube"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("lumi")
        }
        debug {
            signingConfig = signingConfigs.getByName("lumi")
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
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-database:$media3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
