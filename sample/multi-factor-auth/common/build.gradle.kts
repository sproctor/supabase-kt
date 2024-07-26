@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.compose.plugin.get().pluginId)
    alias(libs.plugins.compose.compiler)
    id(libs.plugins.kotlin.multiplatform.get().pluginId)
    alias(libs.plugins.kotlinx.plugin.serialization)
}

group = "io.github.jan.supabase"
version = "1.0-SNAPSHOT"

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    jvmToolchain(8)
    androidTarget()
    jvm("desktop")
    js(IR) {
        browser()
    }
    applyDefaultHierarchyTemplate {
        common {
            group("nonJs") {
                withJvm()
                withAndroidTarget()
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.materialIconsExtended)
                addModules(SupabaseModule.GOTRUE)
                api(libs.koin.core)
            }
        }
        val nonJsMain by getting {
            dependencies {
                api(libs.ktor.client.cio)
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.androidx.compat)
                api(libs.androidx.core)
                api(libs.koin.android)
                api(libs.androidx.lifecycle.viewmodel.ktx)
                api(libs.androidx.lifecycle.viewmodel.compose)
                api(libs.coil.svg)
                api(libs.coil.compose)
                api(libs.coil)
            }
        }
        val desktopMain by getting {
            dependsOn(nonJsMain)
            dependencies {
                api(compose.preview)
            }
        }
        val jsMain by getting {
            dependencies {
                api(libs.ktor.client.js)
            }
        }
    }
}

android {
    compileSdk = 34
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    namespace = "io.github.jan.supabase.common"
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
