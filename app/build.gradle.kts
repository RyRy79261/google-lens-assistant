import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Driven by release-please via the generic updater; do not edit by hand.
val versionMajor = 1 // x-release-please-major
val versionMinor = 0 // x-release-please-minor
val versionPatch = 0 // x-release-please-patch

/**
 * Release signing comes from the environment so the private key never enters the repo.
 * Without the variables — a local build, or a fork's CI — debug falls back to the local
 * debug keystore and release comes out unsigned.
 */
val signingStore: String? = System.getenv("LENSASSIST_KEYSTORE")
val signingStorePassword: String? = System.getenv("LENSASSIST_KEYSTORE_PASSWORD")
val signingKeyAlias: String? = System.getenv("LENSASSIST_KEY_ALIAS")
val signingKeyPassword: String? = System.getenv("LENSASSIST_KEY_PASSWORD")
val hasSigningKey = sequenceOf(
    signingStore,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).none { it.isNullOrBlank() }

android {
    namespace = "com.ryry79261.lensassist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ryry79261.lensassist"
        minSdk = 29
        targetSdk = 36

        // Monotonic and derived, so a semver bump is the only thing anyone edits.
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "1.0.0" // x-release-please-version
    }

    signingConfigs {
        if (hasSigningKey) {
            create("sideload") {
                storeFile = file(signingStore!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Same key as release when one is available. Otherwise every CI run signs
            // with a freshly generated debug keystore and successive APKs refuse to
            // upgrade each other.
            if (hasSigningKey) signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            // Left off deliberately: the app is a handful of classes, and R8 buys
            // nothing here worth the risk to manifest-instantiated services.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningKey) signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
