import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
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

// Make Kotlin compilation depend on Rust builds
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(buildRustRssParser)
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
            isStatic = true
        }

        // Link Rust audio player library
        iosTarget.compilations.getByName("main") {
            val rustAudioPlayer by cinterops.creating {
                defFile(project.file("src/nativeInterop/cinterop/rustAudioPlayer.def"))
                packageName("com.opoojkk.podium.rust")
            }

            // Add linker options to link the Rust static library
            kotlinOptions {
                val targetName = when (iosTarget.name) {
                    "iosArm64" -> "aarch64-apple-ios"
                    "iosSimulatorArm64" -> "aarch64-apple-ios-sim"
                    else -> "aarch64-apple-ios"
                }
                val libPath = project.file("../rust-audio-player/target/$targetName/release").absolutePath
                freeCompilerArgs += listOf(
                    "-linker-option", "-L$libPath",
                    "-linker-option", "-lrust_audio_player",
                    "-linker-option", "-framework", "-linker-option", "CoreAudio",
                    "-linker-option", "-framework", "-linker-option", "AudioToolbox",
                    "-linker-option", "-framework", "-linker-option", "Security"
                )
            }
        }
    }
    
    // Set iOS deployment target
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.configureEach {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xoverride-konan-properties=osVersionMin.ios_simulator_arm64=15.0;osVersionMin.ios_arm64=15.0")
            }
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

    defaultConfig {
        applicationId = "com.opoojkk.podium"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
