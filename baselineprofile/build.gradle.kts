plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "dev.delugelauncher.baselineprofile"
    compileSdk = (rootProject.findProperty("baseline.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
