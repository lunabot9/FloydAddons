plugins {
    // 1.21.10 is still obfuscated, so use the remapping Loom variant.
    id("net.fabricmc.fabric-loom-remap") version "1.14.10"
}

group = property("maven_group")!!
version = property("mod_version")!!

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        name = "FabricMC"
    }
    maven("https://m2.dv8tion.net/releases") {
        name = "Dv8tionReleases"
    }
    maven("https://maven.scijava.org/content/groups/public") {
        name = "SciJavaPublic"
    }
    maven("https://jcenter.bintray.com/") {
        name = "JCenterReadonly"
    }
    maven("https://jitpack.io") {
        name = "JitPack"
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    val discordRpc = "club.minnced:java-discord-rpc:v2.0.1"
    val discordNatives = "club.minnced:discord-rpc-release:v3.3.0"
    modImplementation(discordRpc)
    include(discordRpc)
    include(discordNatives) // brings win/linux/mac native DLLs/SOs
    include("net.java.dev.jna:jna:5.14.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

tasks.test {
    enabled = false
}
