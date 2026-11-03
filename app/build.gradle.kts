import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionCode = 4
val appVersionName = "2.0.0"

android {
    namespace = "io.github.s1ddhants1.unhinge"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.s1ddhants1.unhinge"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        val rootKeystore = rootProject.file("unhinge.keystore")
        val appKeystore = project.file("unhinge.keystore")
        val envPath = System.getenv("UNHINGE_KEYSTORE_PATH")
            ?: System.getenv("KEYSTORE_PATH")
            ?: System.getenv("KEYSTORE_FILE")
        val propPath = (project.findProperty("unhingeKeystoreFile")
            ?: project.findProperty("unhinge.keystore.file")) as? String

        val keystoreFile = when {
            !envPath.isNullOrBlank() -> file(envPath)
            !propPath.isNullOrBlank() -> file(propPath)
            rootKeystore.exists() -> rootKeystore
            appKeystore.exists() -> appKeystore
            else -> {
                val base64Key = System.getenv("KEYSTORE") ?: System.getenv("UNHINGE_KEYSTORE_BASE64")
                if (!base64Key.isNullOrBlank()) {
                    val decodedKeystore = rootProject.layout.buildDirectory.file("keystore/unhinge.keystore").get().asFile
                    decodedKeystore.parentFile.mkdirs()
                    decodedKeystore.writeBytes(Base64.getDecoder().decode(base64Key.trim()))
                    decodedKeystore
                } else {
                    null
                }
            }
        }

        val passFile = rootProject.file(".unhinge_keystore_password.txt")
        val envPass = System.getenv("UNHINGE_KEYSTORE_PASSWORD")
            ?: System.getenv("KEYSTORE_PASSWORD")
        val propPass = (project.findProperty("unhingeKeystorePassword")
            ?: project.findProperty("unhinge.keystore.password")) as? String

        val keystorePassword = when {
            !envPass.isNullOrBlank() -> envPass
            !propPass.isNullOrBlank() -> propPass
            passFile.exists() -> passFile.readText().trim()
            else -> null
        }

        val envAlias = System.getenv("UNHINGE_KEY_ALIAS")
            ?: System.getenv("KEY_ALIAS")
        val propAlias = (project.findProperty("unhingeKeyAlias")
            ?: project.findProperty("unhinge.key.alias")) as? String
        val keyAliasName = when {
            !envAlias.isNullOrBlank() -> envAlias
            !propAlias.isNullOrBlank() -> propAlias
            else -> "unhinge"
        }

        val envKeyPass = System.getenv("UNHINGE_KEY_PASSWORD")
            ?: System.getenv("KEY_PASSWORD")
        val propKeyPass = (project.findProperty("unhingeKeyPassword")
            ?: project.findProperty("unhinge.key.password")) as? String
        val keyPasswordVal = when {
            !envKeyPass.isNullOrBlank() -> envKeyPass
            !propKeyPass.isNullOrBlank() -> propKeyPass
            else -> keystorePassword
        }

        if (keystoreFile != null && keystoreFile.exists() && !keystorePassword.isNullOrBlank()) {
            create("unhinge") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordVal
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
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
            signingConfigs.findByName("unhinge")?.let {
                signingConfig = it
            }
        }
        debug {
            applicationIdSuffix = ""
            signingConfigs.findByName("unhinge")?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/**/LICENSE*",
                "/META-INF/**/NOTICE*",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/DebugProbesKt.bin",
                "**/*.kotlin_builtins"
            )
        }
    }
}

dependencies {

    compileOnly(libs.libxposed.api)

    implementation(libs.libxposed.service)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.libxposed.api)
    testImplementation(libs.bundles.unit.test)
}
