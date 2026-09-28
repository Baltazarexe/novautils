pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "novautils"

// "common" is not a Gradle subproject on purpose: it is a plain source folder
// (common/src/main/java, common/src/main/resources) that both fabric/ and neoforge/
// add as an extra source root and compile directly against their own Minecraft
// dependency. That sidesteps needing a shared binary artifact (shadow/jar-in-jar)
// between two Gradle toolchains (Fabric Loom vs NeoForge ModDev) that don't otherwise
// agree on how to produce one.
include("fabric")
include("neoforge")
// A second NeoForge target for MC 26.1.2 (older than the 26.3 build above), because 26.1.2's
// internals differ enough (GLFW vs SDL keys, different render-pipeline packages, ...) that one
// build can't cover both - see neoforge261/README-DIFFERENCES.md for what actually changed.
include("neoforge261")
