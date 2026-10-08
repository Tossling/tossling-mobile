plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(jdkVersion = 21)

    jvm()
    if (System.getProperty("os.name").startsWith("Mac")) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "TosslingKit"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
        commonTest.configure {
            kotlin.srcDir(layout.buildDirectory.dir("generated/vectors"))
        }
    }
}

val vectorsSource = tasks.register("vectorsSource") {
    val input = layout.projectDirectory.file("src/jvmTest/resources/vectors.json")
    val output = layout.buildDirectory.file("generated/vectors/com/kopylovis/tossling/protocol/VectorsJson.kt")
    inputs.file(input)
    outputs.file(output)
    doLast {
        val text = input.asFile.readText()
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "package com.kopylovis.tossling.protocol\n\ninternal const val VECTORS_JSON: String = \"\"\"" + text.replace("$", "\${'$'}") + "\"\"\"\n",
        )
    }
}

tasks.matching { it.name.startsWith("compileTestKotlin") || (it.name.startsWith("compile") && it.name.contains("Test")) }.configureEach {
    dependsOn(vectorsSource)
}
