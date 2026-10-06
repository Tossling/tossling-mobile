import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val keystore = rootProject.file("tossy.jks")

val buildNumber: Int = file("version-code.txt")
    .takeIf { it.exists() }
    ?.readText()
    ?.trim()
    ?.toIntOrNull()
    ?: 1

android {
    namespace = "com.kopylovis.tossling"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.kopylovis.tossling"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = buildNumber
        versionName = libs.versions.appversion.get()
    }

    signingConfigs {
        if (keystore.exists()) {
            create("app") {
                storeFile = keystore
                val password = localProperties.getProperty("keystore_password")
                keyAlias = localProperties.getProperty("keystore_alias")
                keyPassword = password
                storePassword = password
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("app")?.let { signingConfig = it }
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(jdkVersion = 21)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(projects.modules.common.core)
    implementation(projects.modules.common.navigation)
    implementation(projects.modules.common.sync)
    implementation(projects.modules.feature.devices)
    implementation(projects.modules.feature.home)
    implementation(projects.modules.feature.notifications)
    implementation(projects.modules.feature.pairing)
    implementation(projects.modules.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.filekit.dialogs.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
