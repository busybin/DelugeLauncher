import java.util.Properties

plugins {
    id("com.android.application")
    // AGP 9 has built-in Kotlin; the Compose compiler plugin still needs applying separately.
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing reads a gitignored keystore.properties; the release keystore lives outside
// this repo. Without it the build is UNSIGNED and must never be published.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigningConfig = keystoreProperties.getProperty("storeFile") != null

// SDK versions are not hardcoded here: the shared baseline generates the baseline.* keys in
// gradle.properties.
fun baselineInt(key: String): Int = (rootProject.findProperty(key) as String).toInt()

android {
    namespace = "dev.delugelauncher"
    compileSdk = baselineInt("baseline.compileSdk")
    buildToolsVersion = rootProject.findProperty("baseline.buildTools") as String

    lint {
        // Translations are community-contributed and always lag a version; a missing one falls
        // back to English, so it is no reason to fail the build.
        warning += "MissingTranslation"
    }

    defaultConfig {
        applicationId = "dev.delugelauncher"
        minSdk = baselineInt("baseline.minSdk")
        targetSdk = baselineInt("baseline.targetSdk")
        // Plain integers, no "V" prefix, only ever raised, and always kept equal: the system
        // compares versionCode, and a lower number is rejected as a downgrade.
        versionCode = 25
        versionName = "25"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 carries most of the size and startup win: Compose ships thousands of icons and
            // this app draws a few dozen.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // AGP 9 only accepts the optimize variant; proguard-android.txt is rejected.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                // No keystore.properties means an unsigned build: do not publish it.
                logger.warn("WARNING: keystore.properties not found — this release build will be UNSIGNED.")
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Strip the Google dependency metadata block, or the build is not reproducible (F-Droid rejects it).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP 9 removed android.kotlinOptions{}. jvmTarget is left out: it follows
// android.compileOptions.targetCompatibility.
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.datastore.preferences)

    // Freezing apps needs either root or a Shizuku-authorized shell.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // One binder call per app online through Shizuku beats forking `pm` per command.
    implementation(libs.hiddenapi.bypass)

    testImplementation(libs.junit)
    // Real JSON on the unit-test classpath; the android.jar stubs only throw.
    testImplementation(libs.json)

    // Robolectric: drag-to-reorder only proves itself through a real pointer-event stream.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // UiAutomator drives the "set as default home" flow itself (system dialogs, the Launcher
    // role), not just in-process code.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
