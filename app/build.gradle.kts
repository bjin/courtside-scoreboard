import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val releaseKeystore = providers.environmentVariable("COURTSIDE_RELEASE_KEYSTORE").orNull

val gitVersion = Properties().apply {
    val output = providers.exec {
        workingDir(rootDir)
        commandLine("bash", rootProject.file("tools/git-version.sh").absolutePath)
    }.standardOutput.asText.get()
    load(output.reader())
}

android {
    namespace = "io.github.bjin.courtside"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.bjin.courtside"
        minSdk = 26
        // The target phone runs Android 16 (API 36); keep its behaviour on newer emulators too.
        targetSdk = 36
        versionCode = gitVersion.getProperty("versionCode").toInt()
        versionName = gitVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("COURTSIDE_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("COURTSIDE_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("COURTSIDE_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local release builds are unsigned unless release credentials are provided.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }
    testOptions {
        // Core logic references android.view.KeyEvent constants only; keep JVM tests Android-free.
        unitTests.isReturnDefaultValues = true
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
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
