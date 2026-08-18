package gg.floyd.features.impl.render

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
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
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.apache.logging.log4j.LogManager

/** Bundled vanilla fallbacks plus an upstream-style local Hypixel pack repository source. */
object FloydSkyBlockPackAssets {
    private const val MOD_ID = "floydaddons"
    private const val ITEM_DATA_RESOURCE = "/floyd_skyblock_items.json"
    private const val FALLBACK_PACK_RESOURCE = "/floyd_skyblock_pack_fallback.zip"
    private const val DEFAULT_PACK_URL =
        "https://resourcepacks.hypixel.net/SkyBlock/5c59e0a9-9865-4d4e-91d2-915515672cbd/84.zip"

    private val logger = LogManager.getLogger("FloydAddons")
    private val livePackCacheDir = FabricLoader.getInstance().configDir.resolve(MOD_ID)
    private val packDir = FabricLoader.getInstance().configDir.resolve(MOD_ID).resolve("skyblock-pack")
    private val packFileA = packDir.resolve("pack-a.zip")
    private val packFileB = packDir.resolve("pack-b.zip")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("Floyd-SkyBlockPackLoader"))
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val itemData by lazy {
        val models = HashMap<String, net.minecraft.resources.Identifier>()
        val profiles = HashMap<String, ResolvableProfile>()
        val stream = javaClass.getResourceAsStream(ITEM_DATA_RESOURCE)
            ?: error("Missing $ITEM_DATA_RESOURCE")
        stream.reader().use { reader ->
            for ((skyBlockId, value) in JsonParser.parseReader(reader).asJsonObject.entrySet()) {
                val item = value.asJsonObject
                item.get("model")?.asString?.let { models[skyBlockId] = net.minecraft.resources.Identifier.parse(it) }
                item.get("texture")?.asString?.takeIf(String::isNotEmpty)?.let {
                    profiles[skyBlockId] = createProfile(skyBlockId, it)
                }
            }
        }
        applySprayonatorAliases(models, profiles)
        logger.info("Loaded ${models.size} SkyBlock vanilla item fallbacks")
        models to profiles
    }
    @Volatile private var liveBaseModelsCache: Pair<Path, Map<Identifier, Identifier>>? = null
    @Volatile private var liveHeadSkinsCache: Pair<Path, Map<Identifier, Identifier>>? = null

    @Volatile private var reloadJob: Job? = null
    @Volatile private var packUrl: String? = null
    @Volatile private var lastSeenPackUrl: String? = null
    @Volatile private var activePackFile = getPackFile() ?: packFileA
    @Volatile private var activePack = lazy {
        runBlocking(scope.coroutineContext) {
            preparePack(activePackFile, allowCachedFallback = true)
                ?: error("Failed to prepare initial SkyBlock pack")
        }
    }

    val itemModels: Map<String, net.minecraft.resources.Identifier> get() = itemData.first
    val skullProfiles: Map<String, ResolvableProfile> get() = itemData.second
    val liveItemBaseModels: Map<Identifier, Identifier>
        get() = loadLiveBaseModels()
    val liveHeadSkins: Map<Identifier, Identifier>
        get() = loadLiveHeadSkins()

    @JvmStatic
    fun refreshFromLivePack(url: String, expectedSha1: String) {
        lastSeenPackUrl = url
        if (packUrl == url) return
        packUrl = url
        reload()
    }

    @JvmStatic
    fun reloadLastSeenLivePack(): Boolean {
        val url = lastSeenPackUrl ?: return false
        packUrl = url
        reload()
        return true
    }

    @JvmStatic
    fun forceReload(onComplete: (Boolean) -> Unit = {}) {
        reload(onComplete)
    }

    private fun reload(onComplete: (Boolean) -> Unit = {}) {
        if (reloadJob?.isActive == true) return
        reloadJob = scope.launch {
            val targetFile = if (activePackFile == packFileA) packFileB else packFileA
            val pack = preparePack(targetFile) ?: error("No valid Hypixel pack is available")
            activePack = lazyOf(pack)
            activePackFile = targetFile
            Minecraft.getInstance().submit(Minecraft.getInstance()::reloadResourcePacks)
        }
        reloadJob?.invokeOnCompletion { error ->
            if (error != null) {
                logger.error("Failed to reload the local SkyBlock pack", error)
            }
            onComplete(error == null)
            reloadJob = null
        }
    }

    private suspend fun preparePack(targetFile: Path, allowCachedFallback: Boolean = false): Pack? {
        withContext(Dispatchers.IO) { Files.createDirectories(packDir) }
        val path = downloadPack(targetFile)
            ?: if (allowCachedFallback) getPackFile() ?: targetFile else return null
        if (!Files.exists(path)) {
            logger.info("SkyBlock pack download unavailable, extracting bundled fallback pack")
            javaClass.getResourceAsStream(FALLBACK_PACK_RESOURCE)?.use { input ->
                withContext(Dispatchers.IO) {
                    Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } ?: error("Bundled SkyBlock fallback pack not found at $FALLBACK_PACK_RESOURCE")
        }

        return buildPack(path)
    }

    private suspend fun downloadPack(targetFile: Path): Path? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(packUrl ?: DEFAULT_PACK_URL))
            .header("Accept-Encoding", "gzip")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val tmp = withContext(Dispatchers.IO) { Files.createTempFile(packDir, "skyblock-pack", ".tmp") }
        val response = runCatching {
            withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tmp))
            }
        }.onFailure {
            logger.error("Failed to download Hypixel pack from ${request.uri()}", it)
            Files.deleteIfExists(tmp)
        }.getOrNull() ?: return null

        if (response.statusCode() !in 200..299) {
            logger.error(
                "GET request to ${request.uri()} returned status ${response.statusCode()}, discarding"
            )
            withContext(Dispatchers.IO) { Files.deleteIfExists(tmp) }
            return null
        }

        return try {
            withContext(Dispatchers.IO) { Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING) }
            logger.info("Hypixel SkyBlock pack downloaded successfully")
            targetFile
        } catch (error: FileSystemException) {
            if (!Files.isRegularFile(targetFile)) {
                withContext(Dispatchers.IO) { Files.deleteIfExists(tmp) }
                throw error
            }
            logger.warn("Cannot replace $targetFile; using fresh download for this session", error)
            tmp.toFile().deleteOnExit()
            tmp
        } catch (error: IOException) {
            logger.error("Failed to move downloaded Hypixel pack into place", error)
            withContext(Dispatchers.IO) { Files.deleteIfExists(tmp) }
            throw error
        }
    }

    private fun buildPack(packPath: Path): Pack {
        val locationInfo = PackLocationInfo(
            "hypixel_skyblock",
            Component.literal("FloydAddons: SkyBlock Pack"),
            PackSource.BUILT_IN,
            Optional.empty(),
        )
        val selectionConfig = PackSelectionConfig(
            true,
            Pack.Position.BOTTOM,
            true,
        )
        val resourcesSupplier = FilePackResources.FileResourcesSupplier(packPath.toFile())
        return Pack.readMetaAndCreate(
            locationInfo,
            resourcesSupplier,
            PackType.CLIENT_RESOURCES,
            selectionConfig,
        ) ?: error("Failed to read pack metadata for $packPath")
    }

    private fun getPackFile(): Path? =
        listOf(packFileA, packFileB)
            .filter(Files::exists)
            .maxByOrNull(Files::getLastModifiedTime)

    private fun loadLiveBaseModels(): Map<Identifier, Identifier> {
        val packPath = getPackFile() ?: return emptyMap()
        liveBaseModelsCache?.takeIf { it.first == packPath }?.let { return it.second }

        return runCatching { FloydSkyBlockLivePackCache.inspect(packPath).baseModels }
            .onFailure { logger.warn("Failed to inspect the cached live SkyBlock item pack at {}", packPath, it) }
            .getOrDefault(emptyMap())
            .also { models ->
                if (models.isNotEmpty()) {
                    logger.info("Loaded ${models.size} live SkyBlock base-model fallbacks from $packPath")
                }
                liveBaseModelsCache = packPath to models
            }
    }

    /**
     * Auto-derived player-head skin references from the cached live pack, keyed by head item model
     * id (e.g. `hypixel_skyblock:abiphone/abiphone_basic`). This mirrors [liveItemBaseModels] and is
     * what lets new SkyBlock heads resolve without a hand-maintained registry entry.
     */
    private fun loadLiveHeadSkins(): Map<Identifier, Identifier> {
        val packPath = getPackFile() ?: return emptyMap()
        liveHeadSkinsCache?.takeIf { it.first == packPath }?.let { return it.second }

        return runCatching { FloydSkyBlockLivePackCache.inspect(packPath).headSkins }
            .onFailure { logger.warn("Failed to inspect cached live SkyBlock head skins at {}", packPath, it) }
            .getOrDefault(emptyMap())
            .also { skins ->
                if (skins.isNotEmpty()) {
                    logger.info("Auto-resolved {} SkyBlock head skins from $packPath", skins.size)
                }
                liveHeadSkinsCache = packPath to skins
            }
    }

    private fun createProfile(skyBlockId: String, texture: String): ResolvableProfile {
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        val profile = GameProfile(
            UUID.nameUUIDFromBytes("floydaddons:$skyBlockId".toByteArray()),
            "FloydSkyBlockFallback",
            properties,
        )
        return ResolvableProfile.createResolved(profile)
    }

    private fun applySprayonatorAliases(
        models: MutableMap<String, net.minecraft.resources.Identifier>,
        profiles: MutableMap<String, ResolvableProfile>,
    ) {
        val sprayonatorModel = models["SPRAYONATOR"] ?: return
        val sprayonatorProfile = profiles["SPRAYONATOR"]
        val vanillaGlassBottle = net.minecraft.resources.Identifier.parse("minecraft:glass_bottle")

        for (upgradedId in listOf("JUICY_SPRAYONATOR", "SALTY_SPRAYONATOR")) {
            models.putIfAbsent(upgradedId, sprayonatorModel)
            sprayonatorProfile?.let { profiles.putIfAbsent(upgradedId, it) }
        }
        for (nozzleId in listOf("BASIC_NOZZLE", "JUICY_NOZZLE", "SALTY_NOZZLE")) {
            models.putIfAbsent(nozzleId, vanillaGlassBottle)
        }
    }

    class Repository : RepositorySource {
        override fun loadPacks(onLoad: Consumer<Pack>) {
            onLoad.accept(activePack.value)
        }
    }
}
