import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.tasks.Jar

plugins {
    id("fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://maven.terraformersmc.com/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    modRuntimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    implementation("club.minnced:java-discord-rpc:v2.0.1")
    include("club.minnced:java-discord-rpc:v2.0.1")
    include("club.minnced:discord-rpc-release:v3.3.0")

    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    property("minecraft_lwjgl_version").let { lwjglVersion ->
        modImplementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "linux", "macos", "macos-arm64").forEach { os ->
            modImplementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }

    modCompileOnly("maven.modrinth:iris:${property("iris")}")
    if (System.getenv("FLOYDADDONS_SODIUM_RUNTIME") == "true") {
        modRuntimeOnly("maven.modrinth:sodium:${property("sodium")}")
    }

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    mixin {
        useLegacyMixinAp = true
        defaultRefmapName = "floydaddons.refmap.json"
    }

    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArgs.addAll(
            arrayOf(
                "-Dmixin.debug.export=true",
                "-Ddevauth.enabled=${System.getenv("FLOYDADDONS_DEVAUTH") ?: "false"}",
                "-Ddevauth.account=${System.getenv("FLOYDADDONS_DEVAUTH_ACCOUNT") ?: "main"}",
                "-XX:+AllowEnhancedClassRedefinition",
                "-XX:+IgnoreUnrecognizedVMOptions", // AllowEnhancedClassRedefinition is only available on JBR
            )
        )
    }

    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

afterEvaluate {
    loom.runs.named("client") {
        vmArg("-javaagent:${configurations.compileClasspath.get().find { it.name.contains("sponge-mixin") }}")
    }
}

tasks {
    processResources {
        filesMatching("fabric.mod.json") {
            expand(getProperties())
        }
    }

    named<Jar>("jar") {
        exclude("com/odtheking/**", "starred/skies/**", "odin.mixins.json", "odinClient.mixins.json")
        from(listOf("LICENSE", "THIRD_PARTY_NOTICES.md", "PROVENANCE.md")) {
            into("META-INF")
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs.add("-Xlambdas=class") //Commodore
        }
    }

    compileJava {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.named<Jar>("sourcesJar") {
    exclude("com/odtheking/**", "starred/skies/**", "odin.mixins.json", "odinClient.mixins.json")
    from(listOf("LICENSE", "THIRD_PARTY_NOTICES.md", "PROVENANCE.md")) {
        into("META-INF")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "floydaddons"
            artifactId = "FloydAddons"
            version = version
            from(components["java"])
        }
    }
}
