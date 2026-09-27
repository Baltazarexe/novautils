plugins {
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
}

version = "0.1.0"
group = "dev.novautils"

base {
    archivesName = "novautils-fabric"
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        // common/ is a plain source folder (see settings.gradle.kts), compiled straight into
        // this module against Fabric's own Minecraft dependency below.
        java.srcDir("../common/src/main/java")
        resources.srcDir("../common/src/main/resources")
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }

    withSourcesJar()
}

// Fabric client gametest (see src/main/java/dev/novautils/test/NovaClientGameTest.java)
fabricApi {
    configureTests {
        createSourceSet = false
        modId = "novautils"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
        clearRunDirectory = true
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to "~${libs.versions.minecraft.get()}",
            "loader_version" to libs.versions.fabric.loader.get(),
            "jdk_version" to libs.versions.jdk.get()
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(libs.versions.jdk.get().toInt())
    }
}
