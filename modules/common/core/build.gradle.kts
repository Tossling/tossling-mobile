plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kopylovis.tossling.core"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
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
    api(platform(libs.compose.bom))

    api(libs.compose.runtime)
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    api(libs.haze)

    api(libs.decompose)
    api(libs.decompose.extensions.compose)
    api(libs.decompose.extensions.compose.experimental)
    api(libs.decompose.essenty.lifecycle)
    api(libs.decompose.essenty.stateKeeper)
    api(libs.decompose.essenty.instanceKeeper)
    api(libs.decompose.essenty.backHandler)

    api(libs.koin.core)
    api(libs.koin.android)
    api(libs.koin.compose)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.collections.immutable)

    api(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
