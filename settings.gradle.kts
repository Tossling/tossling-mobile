@file:Suppress("UnstableApiUsage")

import java.nio.file.Files

rootProject.name = "Tossling"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")

fun isGradleModule(dir: java.nio.file.Path): Boolean =
    listOf("build.gradle.kts", "build.gradle").any { filename -> dir.resolve(filename).toFile().exists() }

fun Settings.includeModule(modulePath: String, moduleDir: java.nio.file.Path) {
    include(modulePath)
    project(modulePath).projectDir = moduleDir.toFile()
}

fun Settings.includeAllModules(prefix: String) {
    val base = rootDir.toPath().resolve("modules").resolve(prefix)
    if (!Files.isDirectory(base)) return
    Files.list(base).filter { Files.isDirectory(it) && isGradleModule(it) }.forEach { child ->
        val name = child.fileName.toString()
        includeModule(":modules:$prefix:$name", child)
    }
}

includeAllModules(prefix = "common")
includeAllModules(prefix = "feature")
