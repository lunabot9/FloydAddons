rootProject.name = "FloydAddons"
pluginManagement {

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }

    val loom_version: String by settings
    val kotlin_version: String by settings

    plugins {
        id("net.fabricmc.fabric-loom") version loom_version
        kotlin("jvm") version kotlin_version
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    create(rootProject) {
        versions("26.1", "26.1.2", "26.2")
        vcsVersion = "26.1.2"
    }
}
