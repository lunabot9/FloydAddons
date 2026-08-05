import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.tasks.Jar
import java.nio.charset.StandardCharsets

plugins {
    id("net.fabricmc.fabric-loom")
    kotlin("jvm")
    `maven-publish`
}

group = property("maven_group") as String
val modVersion = property("mod_version") as String
val minecraftVersion = sc.current.version
val artifactVersion = "$modVersion-$minecraftVersion"
version = artifactVersion

data class BrandSpec(
    val key: String,
    val archiveBaseName: String,
    val displayName: String,
    val compactName: String,
    val splashText: String,
    val legacyGuiName: String,
    val legacyGuiDescription: String,
    val description: String,
    val authors: String,
    val iconPath: String,
    val legacyBackgroundPath: String,
    val menuBackgroundPath: String,
    val githubUrl: String,
    val discordUrl: String,
    val coffeeUrl: String,
)

val brands = mapOf(
    "floyd" to BrandSpec(
        key = "floyd",
        archiveBaseName = "FloydAddons",
        displayName = "Floyd Addons",
        compactName = "FloydAddons",
        splashText = "FloydAddons",
        legacyGuiName = "Floyd GUI",
        legacyGuiDescription = "Customizes and opens the fullscreen Floyd GUI.",
        description = "Floyd Addons - Fabric client module suite (ClickGUI, HUD, ESP, PvP).",
        authors = "Gobs, FloydAddons contributors",
        iconPath = "assets/floydaddons/icons/taskbar_icon_128x128.png",
        legacyBackgroundPath = "textures/gui/floydbg.png",
        menuBackgroundPath = "textures/gui/menu_sun_floyd.png",
        githubUrl = "https://github.com/lunabot9/FloydAddons",
        discordUrl = "https://discord.gg/FLOYD",
        coffeeUrl = "https://buymeacoffee.com/lunabot9",
    ),
    "ginger" to BrandSpec(
        key = "ginger",
        archiveBaseName = "FoidAddons",
        displayName = "Foid Addons",
        compactName = "FoidAddons",
        splashText = "FoidAddons",
        legacyGuiName = "Foid Gui",
        legacyGuiDescription = "Customizes and opens the fullscreen Foid Gui.",
        description = "Foid Addons - Fabric client module suite (ClickGUI, HUD, ESP, PvP).",
        authors = "Gobs, FoidAddons contributors",
        iconPath = "assets/floydaddons/icons/taskbar_icon_128x128.png",
        legacyBackgroundPath = "textures/gui/floydbg.png",
        menuBackgroundPath = "textures/gui/menu_sun_floyd.png",
        githubUrl = "https://github.com/lunabot9/FloydAddons",
        discordUrl = "https://discord.gg/FLOYD",
        coffeeUrl = "https://buymeacoffee.com/lunabot9",
    )
)
val activeBrand = brands[(findProperty("brand") as String?)?.lowercase() ?: "floyd"]
    ?: error("Unknown brand '${findProperty("brand")}'. Expected one of: ${brands.keys.sorted().joinToString(", ")}")

base {
    archivesName.set(activeBrand.archiveBaseName)
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
                "-Xms512M",
                "-Xmx2G",
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
    val generatedBrandingDir = layout.buildDirectory.dir("generated/sources/branding/kotlin")
    val generatedBrandingResourcesDir = layout.buildDirectory.dir("generated/resources/branding")

    val generateBrandingSources by registering {
        inputs.property("brandKey", activeBrand.key)
        inputs.property("brandDisplayName", activeBrand.displayName)
        inputs.property("brandCompactName", activeBrand.compactName)
        inputs.property("brandSplashText", activeBrand.splashText)
        inputs.property("brandLegacyGuiName", activeBrand.legacyGuiName)
        inputs.property("brandLegacyGuiDescription", activeBrand.legacyGuiDescription)
        inputs.property("brandDescription", activeBrand.description)
        outputs.dir(generatedBrandingDir)
        doLast {
            val outDir = generatedBrandingDir.get().file("gg/floyd").asFile
            outDir.mkdirs()
            val file = outDir.resolve("Branding.kt")
            file.writeText(
                """
                package gg.floyd

                object Branding {
                    const val FLAVOR = "${activeBrand.key}"
                    const val ARCHIVE_BASE_NAME = "${activeBrand.archiveBaseName}"
                    const val DISPLAY_NAME = "${activeBrand.displayName}"
                    const val COMPACT_NAME = "${activeBrand.compactName}"
                    const val SPLASH_TEXT = "${activeBrand.splashText}"
                    const val LEGACY_GUI_NAME = "${activeBrand.legacyGuiName}"
                    const val LEGACY_GUI_DESCRIPTION = "${activeBrand.legacyGuiDescription}"
                    const val DESCRIPTION = "${activeBrand.description}"
                    const val GITHUB_URL = "${activeBrand.githubUrl}"
                    const val DISCORD_URL = "${activeBrand.discordUrl}"
                    const val COFFEE_URL = "${activeBrand.coffeeUrl}"
                    const val LEGACY_BACKGROUND_PATH = "${activeBrand.legacyBackgroundPath}"
                    const val MENU_BACKGROUND_PATH = "${activeBrand.menuBackgroundPath}"
                    const val COMMUNITY_HEADER = "Join the ${activeBrand.displayName} Community"
                    const val VERSION = "${modVersion}"
                    val IS_GINGER: Boolean get() = FLAVOR == "ginger"
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8
            )
        }
    }

    val generateBrandingResources by registering {
        inputs.property("brandKey", activeBrand.key)
        outputs.dir(generatedBrandingResourcesDir)
        doLast {
            val root = generatedBrandingResourcesDir.get().asFile
            root.deleteRecursively()
            root.mkdirs()
            if (activeBrand.key == "ginger") {
                val sourceDir = rootProject.file("branding/ginger")
                val targetDir = root.resolve("assets/floydaddons")
                targetDir.mkdirs()
                copy {
                    from(sourceDir.resolve("floydbg.png"))
                    into(targetDir.resolve("textures/gui"))
                }
                copy {
                    from(sourceDir.resolve("menu_sun_floyd.png"))
                    into(targetDir.resolve("textures/gui"))
                }
                copy {
                    from(
                        sourceDir.resolve("taskbar_icon_16x16.png"),
                        sourceDir.resolve("taskbar_icon_32x32.png"),
                        sourceDir.resolve("taskbar_icon_48x48.png"),
                        sourceDir.resolve("taskbar_icon_128x128.png"),
                    )
                    into(targetDir.resolve("icons"))
                }
            }
        }
    }

    sourceSets.named("main") {
        kotlin.srcDir(generatedBrandingDir)
    }

    withType<AbstractArchiveTask>().configureEach {
        archiveBaseName.set(activeBrand.archiveBaseName)
        archiveVersion.set(artifactVersion)
    }

    processResources {
        dependsOn(generateBrandingResources)
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from(generatedBrandingResourcesDir)
        val resourceProps = mapOf(
            "mod_id" to project.property("mod_id").toString(),
            // Keep the in-game mod version release-oriented while the artifact name also
            // identifies which Minecraft version it was compiled against.
            "mod_version" to modVersion,
            "mod_name" to activeBrand.displayName,
            "mod_description" to activeBrand.description,
            "brand_authors" to activeBrand.authors,
            "brand_icon" to activeBrand.iconPath,
            "loader_version" to project.property("loader_version").toString(),
            "fabric_api_version" to project.property("fabric_api_version").toString(),
            "minecraft_version" to minecraftVersion,
            "fabric_kotlin_version" to project.property("fabric_kotlin_version").toString(),
        )
        inputs.properties(resourceProps)
        filesMatching(listOf("fabric.mod.json", "assets/floydaddons/lang/en_us.json")) {
            expand(resourceProps)
        }
    }

    compileKotlin {
        dependsOn(generateBrandingSources)
    }

    named<Jar>("jar") {
        destinationDirectory.set(file("$buildDir/libs"))
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
    dependsOn("generateBrandingSources", "generateBrandingResources")
    from(listOf("LICENSE", "THIRD_PARTY_NOTICES.md", "PROVENANCE.md")) {
        into("META-INF")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "floydaddons"
            artifactId = activeBrand.archiveBaseName
            version = version
            from(components["java"])
        }
    }
}
