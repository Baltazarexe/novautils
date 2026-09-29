plugins {
    alias(libs.plugins.fabric.loom)
}

version = "0.1.0"
group = "dev.novautils"

base {
    archivesName = "novautils-fabric-26.1.2"
}

repositories {
    mavenCentral()
}

// Same approach as neoforge261: copy common/ into build/ minus the two files that differ on
// 26.1.2, and take those two from neoforge261/src (they only touch vanilla, not the loader).
val stageCommonSrc = tasks.register<Sync>("stageCommonSrc") {
    from("../common/src/main/java") {
        exclude("dev/novautils/utils/PlatformCompat.java")
        exclude("dev/novautils/utils/EspRender.java")
    }
    from("../neoforge261/src/main/java") {
        include("dev/novautils/utils/PlatformCompat.java")
        include("dev/novautils/utils/EspRender.java")
    }
    into(layout.buildDirectory.dir("generated/commonSrc"))
}

sourceSets {
    main {
        java.srcDir(stageCommonSrc.map { it.destinationDir })
        resources.srcDir("../common/src/main/resources")
    }
}

dependencies {
    minecraft(libs.minecraft261)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api261)
}

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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to "~${libs.versions.minecraft261.get()}",
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
