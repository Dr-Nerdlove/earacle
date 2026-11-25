plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// CI signs with a fixed key from repository secrets (see .github/workflows/release.yml), so a newer
// APK installs over the old one. Without it (local builds and pull request checks) the debug build
// uses the normal debug key and the release build stays unsigned.
val sharedKeystore = System.getenv("EARACLE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let(::file)
val sharedKeystorePassword = System.getenv("EARACLE_KEYSTORE_PASSWORD")

android {
    namespace = "io.github.drnerdlove.earacle"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.drnerdlove.earacle"
        minSdk = 29
        targetSdk = 37
        versionCode = 7
        versionName = "0.6.0"
    }

    signingConfigs {
        if (sharedKeystore != null) {
            create("shared") {
                storeFile = sharedKeystore
                storePassword = sharedKeystorePassword
                keyAlias = "earacle"
                keyPassword = sharedKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            if (sharedKeystore != null) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
        release {
            isMinifyEnabled = false
            if (sharedKeystore != null) {
                signingConfig = signingConfigs.getByName("shared")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Reads LRCLIB's lyricsfile (YAML with line ends and word times).
    implementation(libs.snakeyaml.engine)
    testImplementation(libs.junit)
    // Android ships org.json, but in JVM unit tests android.jar only has stubs of it.
    testImplementation(libs.org.json)
}
