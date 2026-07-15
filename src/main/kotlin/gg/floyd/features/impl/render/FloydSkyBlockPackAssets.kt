package gg.floyd.features.impl.render

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import gg.floyd.FloydAddonsMod
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import net.minecraft.world.item.component.ResolvableProfile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

/** Vanilla fallbacks for Hypixel's custom item model components. */
object FloydSkyBlockPackAssets {
    private const val FALLBACK_RESOURCE = "/floyd_skyblock_pack_fallback.zip"
    private const val ITEM_DATA_RESOURCE = "/floyd_skyblock_items.json"
    private const val FALLBACK_PACK_REVISION = "480bc658b73a9ca7"
    private val extractedPack = FabricLoader.getInstance().configDir
        .resolve("floydaddons")
        .resolve("skyblock-pack-models-$FALLBACK_PACK_REVISION.zip")

    val itemModels: Map<String, Identifier> by lazy { loadItemData().first }
    val skullProfiles: Map<String, ResolvableProfile> by lazy { loadItemData().second }
    private val activePack: Pack by lazy { buildPack() }

    private val itemData by lazy {
        val models = HashMap<String, Identifier>()
        val profiles = HashMap<String, ResolvableProfile>()
        val stream = javaClass.getResourceAsStream(ITEM_DATA_RESOURCE)
            ?: error("Missing $ITEM_DATA_RESOURCE")
        stream.reader().use { reader ->
            for ((skyBlockId, value) in JsonParser.parseReader(reader).asJsonObject.entrySet()) {
                val item = value.asJsonObject
                item.get("model")?.asString?.let { models[skyBlockId] = Identifier.parse(it) }
                item.get("texture")?.asString?.takeIf(String::isNotEmpty)?.let {
                    profiles[skyBlockId] = createProfile(skyBlockId, it)
                }
            }
        }
        FloydAddonsMod.logger.info("Loaded ${models.size} SkyBlock vanilla item fallbacks")
        models to profiles
    }

    private fun loadItemData() = itemData

    private fun createProfile(skyBlockId: String, texture: String): ResolvableProfile {
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        val profile = GameProfile(
            UUID.nameUUIDFromBytes("floydaddons:$skyBlockId".toByteArray()),
            "FloydSkyBlockFallback",
            properties,
        )
        return ResolvableProfile.createResolved(profile)
    }

    private fun buildPack(): Pack {
        Files.createDirectories(extractedPack.parent)
        javaClass.getResourceAsStream(FALLBACK_RESOURCE)?.use { input ->
            FloydSkyBlockPackMaterializer.materialize(input, extractedPack)
        } ?: error("Missing $FALLBACK_RESOURCE")

        val location = PackLocationInfo(
            "floyd_skyblock_model_fallbacks",
            Component.literal("FloydAddons: SkyBlock model fallbacks"),
            PackSource.BUILT_IN,
            Optional.empty(),
        )
        val supplier = FilePackResources.FileResourcesSupplier(extractedPack.toFile())
        val selection = PackSelectionConfig(true, Pack.Position.BOTTOM, true)
        return Pack.readMetaAndCreate(location, supplier, PackType.CLIENT_RESOURCES, selection)
            ?: error("Failed to load SkyBlock fallback pack")
    }

    class Repository : RepositorySource {
        override fun loadPacks(onLoad: Consumer<Pack>) {
            onLoad.accept(activePack)
        }
    }
}

internal object FloydSkyBlockPackMaterializer {
    fun materialize(input: java.io.InputStream, target: Path) {
        // FilePackResources keeps the ZIP mounted. Replacing that file on a repository refresh
        // fails on Windows and aborts PackSelectionScreen before it can list the user's packs.
        if (Files.exists(target)) return
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
