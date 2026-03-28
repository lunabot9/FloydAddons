plugins {
    // 1.21.x is still obfuscated, so use the remapping Loom variant.
    id("net.fabricmc.fabric-loom-remap") version "1.14.10"
}

fun Project.strProp(name: String): String? = findProperty(name) as String?
fun String.toKeySuffix() = replace(".", "_")

val targetMc = strProp("target_mc") ?: "1.21.10"
val minecraftVersion = strProp("minecraft_version_${targetMc.toKeySuffix()}") ?: strProp("minecraft_version")
    ?: error("Missing minecraft_version for $targetMc")
val yarnMappings = strProp("yarn_mappings_${targetMc.toKeySuffix()}") ?: strProp("yarn_mappings")
    ?: error("Missing yarn_mappings for $targetMc")
val loaderVersion = strProp("loader_version_${targetMc.toKeySuffix()}") ?: strProp("loader_version")
    ?: error("Missing loader_version for $targetMc")
val fabricApiVersion = strProp("fabric_api_version_${targetMc.toKeySuffix()}") ?: strProp("fabric_api_version")
    ?: error("Missing fabric_api_version for $targetMc")
val minecraftDependency = strProp("minecraft_dependency_${targetMc.toKeySuffix()}") ?: strProp("minecraft_dependency")
    ?: ">=$minecraftVersion"
val loaderDependency = strProp("fabricloader_dependency_${targetMc.toKeySuffix()}") ?: strProp("fabricloader_dependency")
    ?: ">=$loaderVersion"

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
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

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
    inputs.property("minecraft_dependency", minecraftDependency)
    inputs.property("loader_dependency", loaderDependency)
    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft_dependency" to minecraftDependency,
                "loader_dependency" to loaderDependency,
            )
        )
    }
}

tasks.test {
    enabled = false
}
