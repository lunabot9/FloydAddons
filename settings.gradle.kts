pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        gradlePluginPortal()
    }
}

plugins {
    // Allow automatic toolchain download if JDK 21 is missing
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "FloydAddons NOT DOGSHIT"
include("app")
