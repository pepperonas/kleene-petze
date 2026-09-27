import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Signing: locally from keystore.properties (gitignored), on CI from env secrets.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasSigning = keystorePropsFile.exists() || System.getenv("KEYSTORE_PASSWORD") != null

android {
    namespace = "io.celox.notifvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.celox.notifvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.10.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                if (keystorePropsFile.exists()) {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                } else {
                    storeFile = rootProject.file(System.getenv("KEYSTORE_FILE") ?: "release.jks")
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
    }

    // buildConfig: the Settings screen shows the running version, which is the quickest way to
    // tell whether an update actually landed on the device.
    buildFeatures { compose = true; buildConfig = true }
    lint {
        // AGP 8.7.3's bundled lint crashes against the AndroidX artifacts pulled by material3
        // 1.5.0-alpha18 (same as Brutus) — the app uses no LiveData, so nothing is lost.
        disable += "NullSafeMutableLiveData"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Room writes its expected schema JSON here (checked in) — the reference for hand-written
// migrations: index/table DDL in a Migration must match these definitions exactly.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    // BOM 2026.06.01 maps material3 1.4.0 — the Expressive APIs (MaterialExpressiveTheme,
    // MotionScheme) are still internal there, so material3 is pinned past the BOM like Brutus
    // and Flipper: 1.5.0-alpha18 is the newest alpha still on Compose 1.11 (alpha19+ needs
    // compileSdk 37 + AGP 9.1).
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // NotificationCompat (MessagingStyle extraction)
    implementation("androidx.core:core:1.15.0")

    // Room + SQLCipher (encrypted database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Encrypted key storage (AES-256-GCM)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // App lock
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Opt-in daily update check (v1.10.0). 2.10.x is the newest line still on compileSdk 35.
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests (pure JVM)
    testImplementation("junit:junit:4.13.2")
    // android.jar only ships stubs of org.json; the real one lets the update parsers run on the JVM.
    testImplementation("org.json:json:20240303")
}
