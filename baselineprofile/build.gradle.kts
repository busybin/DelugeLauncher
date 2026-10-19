plugins {
    id("com.android.test")
    // AGP 9 has built-in Kotlin; do not apply org.jetbrains.kotlin.android.
    id("androidx.baselineprofile")
}

android {
    namespace = "dev.delugelauncher.baselineprofile"
    // Same baseline gradle.properties as the app module.
    compileSdk = (rootProject.findProperty("baseline.compileSdk") as String).toInt()

    defaultConfig {
        // 28 is this module's own floor, required by the benchmark toolchain, not the baseline.
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Optional; kept off to avoid pulling in extra dependencies.
    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
