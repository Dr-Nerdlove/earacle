// AGP 9 has built-in Kotlin support, so there is no org.jetbrains.kotlin.android plugin here.
// AGP bundles an older Kotlin Gradle plugin; the newer one is put on the classpath below,
// as the AGP 9.0 release notes ("Upgrade to a higher KGP version") describe.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
