package gg.floyd.features.impl.cosmetic

import gg.floyd.FloydAddonsMod
import net.minecraft.resources.Identifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile

/**
 * # CosmeticImages
 *
 * Single shared on-disk image directory for the cosmetic modules that pick a PNG/GIF asset.
 * Both [FloydCape] and [FloydConeHat] read from this one folder (`config/floydaddons/images`)
 * instead of their old per-feature `capes` / `cone-hats` folders.
 *
 * [ensureSeeded] is crash-safe and idempotent: it always recreates the directory if missing,
 * seeds the bundled default image(s) on first run, and migrates any files the user already
 * placed in the legacy `capes` / `cone-hats` directories into the shared folder WITHOUT
 * deleting the originals. It performs file I/O only — it never touches GL/NVG/RenderSystem,
 * so it is safe to call from the texture/list code paths (but NOT from module init).
 */
object CosmeticImages {

    /** Shared directory holding every cosmetic image (capes + cone hats). */
    val dir: Path = FloydAddonsMod.configFile.toPath().resolve("images")

    // Legacy per-feature directories whose contents are migrated into [dir] on first run.
    private val legacyDirs: List<Path> = listOf(
        FloydAddonsMod.configFile.toPath().resolve("capes"),
        FloydAddonsMod.configFile.toPath().resolve("cone-hats")
    )

    private var migrated = false

    /**
     * Bundled default images seeded into the shared directory on first run, regardless of which
     * cosmetic module triggers seeding first, so the folder always holds both the default cape and
     * the default cone-hat image.
     */
    private val bundledDefaults: List<Pair<Identifier, String>> = listOf(
        Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "textures/cape/default_cape.png") to "default_cape.png",
        Identifier.fromNamespaceAndPath(FloydAddonsMod.MOD_ID, "textures/entity/cone.png") to "Floyd.png"
    )

    /**
     * Ensures the shared directory exists, seeds the bundled default image(s), and (once per
     * session) copies any user files from the legacy directories into the shared one. Safe to call
     * repeatedly; failures are swallowed so a bad filesystem state can never crash a render path.
     */
    fun ensureSeeded() {
        try {
            Files.createDirectories(dir)
            migrateLegacy()
            for ((resource, fileName) in bundledDefaults) seedDefault(resource, fileName)
        } catch (_: Exception) {
            // Crash-safe: never propagate filesystem errors into a render/list path.
        }
    }

    private fun seedDefault(resource: Identifier, fileName: String) {
        val target = dir.resolve(fileName)
        if (Files.exists(target)) return
        FloydAddonsMod.mc.resourceManager.getResource(resource).ifPresent { res ->
            res.open().use { input -> Files.copy(input, target) }
        }
    }

    private fun migrateLegacy() {
        if (migrated) return
        migrated = true
        for (legacy in legacyDirs) {
            if (!Files.isDirectory(legacy)) continue
            try {
                Files.list(legacy).use { stream ->
                    stream.filter { it.isRegularFile() }.forEach { source ->
                        val target = dir.resolve(source.fileName.toString())
                        // Preserve user files: copy without overwriting, never delete the original.
                        if (!Files.exists(target)) {
                            runCatching { Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES) }
                                .recoverCatching { Files.copy(source, target) }
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip an unreadable legacy directory; other directories/seeds still proceed.
            }
        }
    }
}
