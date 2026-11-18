plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

android {
    namespace = "com.cmf.budscontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cmf.budscontroller"
        minSdk = 26
        targetSdk = 35
        versionCode = 2523
        versionName = "2.4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signingProperties = Properties().apply {
        val propertiesFile = rootProject.file("keystore.properties")
        if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
    }
    val releaseKeyFile = signingProperties.getProperty("storeFile")
        ?.let { rootProject.file(it) }
        ?: System.getenv("CMF_RELEASE_KEYSTORE")?.let { file(it) }

    buildTypes {
        release {
            if (releaseKeyFile?.exists() == true) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = releaseKeyFile
                    storePassword = signingProperties.getProperty("storePassword")
                        ?: System.getenv("CMF_RELEASE_STORE_PASSWORD")
                    keyAlias = signingProperties.getProperty("keyAlias")
                        ?: System.getenv("CMF_RELEASE_KEY_ALIAS")
                    keyPassword = signingProperties.getProperty("keyPassword")
                        ?: System.getenv("CMF_RELEASE_KEY_PASSWORD")
                    enableV1Signing = true
                    enableV2Signing = true
                    enableV3Signing = true
                    enableV4Signing = true
                }
            }
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
