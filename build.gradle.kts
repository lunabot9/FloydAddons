import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
val modVersion = property("mod_version") as String
val minecraftVersion = sc.current.version
version = "$modVersion-$minecraftVersion"

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    property("commodore_version").let {
        implementation("com.github.stivais:Commodore:$it")
        include("com.github.stivais:Commodore:$it")
    }

    implementation("club.minnced:java-discord-rpc:v2.0.1")
    include("club.minnced:java-discord-rpc:v2.0.1")
    include("club.minnced:discord-rpc-release:v3.3.0")

    implementation("org.jcodec:jcodec:0.2.5")
    include("org.jcodec:jcodec:0.2.5")
    implementation("org.jcodec:jcodec-javase:0.2.5")
    include("org.jcodec:jcodec-javase:0.2.5")

    compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")

    property("minecraft_lwjgl_version").let { lwjglVersion ->
        implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")

        listOf("windows", "windows-arm64", "windows-x86", "linux", "linux-arm64", "macos", "macos-arm64").forEach { os ->
            implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
            include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:natives-$os")
        }
    }

    property("minecraft_lwjgl_version").let { msdfgenVersion ->
        implementation("org.lwjgl:lwjgl-msdfgen:$msdfgenVersion") {
            exclude(group = "org.lwjgl", module = "lwjgl")
        }
        include("org.lwjgl:lwjgl-msdfgen:$msdfgenVersion")

        listOf(
            "natives-macos-arm64",
            "natives-macos",
            "natives-windows",
            "natives-windows-arm64",
            "natives-windows-x86",
            "natives-linux",
            "natives-linux-arm64",
        ).forEach { natives ->
            implementation("org.lwjgl:lwjgl-msdfgen:$msdfgenVersion:$natives") {
                exclude(group = "org.lwjgl", module = "lwjgl")
            }
            include("org.lwjgl:lwjgl-msdfgen:$msdfgenVersion:$natives")
        }
    }

    compileOnly("maven.modrinth:iris:${property("iris")}")
    compileOnly("maven.modrinth:sodium:${property("sodium")}")

    val sodiumRuntimeEnabled = providers.environmentVariable("FLOYDADDONS_SODIUM_RUNTIME")
        .map { it.equals("false", ignoreCase = true).not() }
        .orElse(true)
        .get()
    if (sodiumRuntimeEnabled) {
        runtimeOnly("maven.modrinth:sodium:${property("sodium")}")
    }

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
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
        val resourceProps = mapOf(
            "mod_id" to project.property("mod_id").toString(),
            // Keep the in-game mod version release-oriented while the artifact name also
            // identifies which Minecraft version it was compiled against.
            "mod_version" to modVersion,
            "mod_name" to project.property("mod_name").toString(),
            "mod_description" to project.property("mod_description").toString(),
            "loader_version" to project.property("loader_version").toString(),
            "fabric_api_version" to project.property("fabric_api_version").toString(),
            "minecraft_version" to minecraftVersion,
            "fabric_kotlin_version" to project.property("fabric_kotlin_version").toString(),
        )
        inputs.properties(resourceProps)
        filesMatching("fabric.mod.json") {
            expand(resourceProps)
        }
    }

    named<Jar>("jar") {
        from(listOf("LICENSE", "THIRD_PARTY_NOTICES.md", "PROVENANCE.md")) {
            into("META-INF")
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
            freeCompilerArgs.add("-Xlambdas=class") //Commodore
        }
    }

    compileJava {
        sourceCompatibility = "25"
        targetCompatibility = "25"
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    workingDir = rootProject.projectDir
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

tasks.named<Jar>("sourcesJar") {
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
