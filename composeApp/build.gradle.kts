import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Default iOS resource sync settings when not launched from Xcode
if (!project.hasProperty("compose.ios.resources.platform")) {
    project.extensions.extraProperties["compose.ios.resources.platform"] = "iphonesimulator"
}
if (!project.hasProperty("compose.ios.resources.archs")) {
    project.extensions.extraProperties["compose.ios.resources.archs"] = "arm64"
}

// Ensure Compose iOS resource sync has a target output directory even when not invoked from Xcode
afterEvaluate {
    tasks.named("syncComposeResourcesForIos").configure {
        val dir = layout.buildDirectory.dir("compose/ios/$name")
        // Task type is internal, set its DirectoryProperty via reflection
        val outputDirProp = javaClass.methods
            .firstOrNull { it.name == "getOutputDir" && it.parameterCount == 0 }
            ?.invoke(this) as? org.gradle.api.file.DirectoryProperty
        outputDirProp?.set(dir)
    }
}

// Task to build Rust RSS parser
val buildRustRssParser by tasks.registering(Exec::class) {
    val scriptFile = project.file("../rust-rss-parser/build.sh")

    // Set working directory
    workingDir(project.file("../rust-rss-parser"))

    // Run the build script with bash (bash handles permissions)
    commandLine("bash", scriptFile.absolutePath)

    // Define inputs so Gradle can track changes
    inputs.file(scriptFile)
    inputs.dir("../rust-rss-parser/src")
    inputs.file("../rust-rss-parser/Cargo.toml")
    inputs.file("../rust-rss-parser/Cargo.lock")

    // Define outputs so Gradle can cache and track changes
    outputs.dir("../rust-rss-parser/target/outputs")
    outputs.dir("src/androidMain/jniLibs")
    outputs.dir("src/jvmMain/resources/darwin-aarch64")
    outputs.dir("src/jvmMain/resources/darwin-x86_64")
}

// Task to build Rust Audio Player
val buildRustAudioPlayer by tasks.registering(Exec::class) {
    val scriptFile = project.file("../rust-audio-player/build.sh")

    // Set working directory
    workingDir(project.file("../rust-audio-player"))

    // Run the build script with bash (bash handles permissions)
    commandLine("bash", scriptFile.absolutePath)

    // Define inputs so Gradle can track changes
    inputs.file(scriptFile)
    inputs.dir("../rust-audio-player/src")
    inputs.file("../rust-audio-player/Cargo.toml")

    // Define outputs so Gradle can cache and track changes
    outputs.dir("../rust-audio-player/target/outputs")
    outputs.dir("src/androidMain/jniLibs")
    outputs.dir("src/jvmMain/resources/darwin-aarch64")
    outputs.dir("src/jvmMain/resources/darwin-x86_64")
    outputs.dir("../rust-audio-player/target/aarch64-apple-ios/release")
    outputs.dir("../rust-audio-player/target/aarch64-apple-ios-sim/release")
}

// Make Kotlin compilation and cinterop depend on Rust builds
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(buildRustRssParser)
    dependsOn(buildRustAudioPlayer)
}

// Make cinterop tasks depend on Rust audio player build
tasks.matching { it.name.contains("cinterop") && it.name.contains("RustAudioPlayer") }.configureEach {
    dependsOn(buildRustAudioPlayer)
}

// For Android, also ensure preBuild depends on Rust builds
tasks.findByName("preBuild")?.dependsOn(buildRustRssParser, buildRustAudioPlayer)

// For JVM, ensure jvmProcessResources depends on Rust builds
tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(buildRustRssParser, buildRustAudioPlayer)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = false
        }

        val targetName = when (iosTarget.name) {
            "iosArm64" -> "aarch64-apple-ios"
            "iosSimulatorArm64" -> "aarch64-apple-ios-sim"
            else -> "aarch64-apple-ios"
        }
        val libPath = project.file("../rust-audio-player/target/$targetName/release").absolutePath

        // Link Rust audio player library
        iosTarget.compilations.getByName("main") {
            val rustAudioPlayer by cinterops.creating {
                defFile(project.file("src/nativeInterop/cinterop/rustAudioPlayer.def"))
                packageName("com.opoojkk.podium.rust")
            }
        }

        // Ensure rust audio player and sqlite are linked into the K/N framework
        iosTarget.binaries.all {
            linkerOpts(
                "-L$libPath",
                "-lrust_audio_player",
                // Force load to avoid stripping unused symbols in static framework
                "-force_load", "$libPath/librust_audio_player.a",
                "-framework", "CoreAudio",
                "-framework", "AudioToolbox",
                "-framework", "Security",
                "-lsqlite3"
            )
        }
    }
    
    // Set iOS deployment target
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.all {
            freeCompilerArgs += listOf(
                "-Xoverride-konan-properties=osVersionMin.ios_simulator_arm64=15.0;osVersionMin.ios_arm64=15.0"
            )
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android)
            implementation(libs.ktor.serialization.kotlinx.xml)
            implementation(libs.coil.network.okhttp)
            implementation(libs.androidx.media)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.ui)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.async)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.jvm)
            implementation(libs.ktor.serialization.kotlinx.xml)
            implementation(libs.jlayer)
        }
    }
}

android {
    namespace = "com.opoojkk.podium"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    // Load signing configuration from multiple possible locations
    // Priority: 1. signing/keystore.properties (submodule)
    //          2. keystore.properties (project root)
    //          3. Environment variables (CI/CD)
    val keystorePropertiesFile = when {
        rootProject.file("signing/keystore.properties").exists() -> {
            println("📦 Using signing configuration from submodule: signing/keystore.properties")
            rootProject.file("signing/keystore.properties")
        }
        rootProject.file("keystore.properties").exists() -> {
            println("📦 Using signing configuration from project root: keystore.properties")
            rootProject.file("keystore.properties")
        }
        else -> {
            println("⚠️  No keystore.properties found. Using debug signing.")
            null
        }
    }

    val keystoreProperties = Properties()
    val hasKeystoreConfig = keystorePropertiesFile?.exists() == true

    if (hasKeystoreConfig && keystorePropertiesFile != null) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    defaultConfig {
        applicationId = "com.opoojkk.podium"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // Enable NDK filter for specific ABIs
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    // Signing configurations
    signingConfigs {
        if (hasKeystoreConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")

                // Enable V1 and V2 signing
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isShrinkResources = false
        }

        getByName("release") {
            // Enable code minification and resource shrinking
            isMinifyEnabled = true
            isShrinkResources = true

            // ProGuard rules
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Use release signing config if available
            if (hasKeystoreConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Split APKs by ABI for smaller download sizes
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true  // Also generate a universal APK
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.opoojkk.podium.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.opoojkk.podium"
            packageVersion = "1.0.0"
        }
    }
}

sqldelight {
    databases {
        create("PodcastDatabase") {
            packageName.set("com.opoojkk.podium.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight"))
            version = 5
        }
    }
}
