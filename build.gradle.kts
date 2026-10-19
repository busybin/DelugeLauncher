// Shared build settings; regenerate rather than hand-edit.
// AGP 9 has built-in Kotlin: never apply org.jetbrains.kotlin.android; raise the bundled KGP here instead.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.library") version "9.4.0" apply false
    id("com.android.test") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false // must match KGP above
    // 1.3.4 breaks under the new DSL
    id("androidx.baselineprofile") version "1.5.0" apply false
}
