import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "photoswipe"
        browser {
            commonWebpackConfig {
                outputFileName = "photoswipe.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.documentfile)
        }
    }
}

android {
    namespace = "com.photoswipe.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.photoswipe.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("keystore.path") as String? ?: "./keys/release.jks")
            storePassword = project.findProperty("keystore.password") as String? ?: ""
            keyAlias = project.findProperty("key.alias") as String? ?: "photoswipe"
            keyPassword = project.findProperty("key.password") as String? ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Copy Skiko's web runtime (skiko.wasm / skiko.mjs / skiko.js) into the
// production browser distribution. Webpack's production bundle doesn't
// include them by default, so the page would 404 on skiko.wasm at runtime.
afterEvaluate {
    tasks.named("wasmJsBrowserDistribution").configure {
        dependsOn("wasmJsProcessResources")
        doLast {
            val skikoDir = layout.buildDirectory.dir("compose/skiko-for-web-runtime").get().asFile
            val distDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile
            if (skikoDir.exists() && distDir.exists()) {
                listOf("skiko.wasm", "skiko.mjs", "skiko.js").forEach { name ->
                    val src = skikoDir.resolve(name)
                    if (src.exists()) src.copyTo(distDir.resolve(name), overwrite = true)
                }
            }
        }
    }
}
